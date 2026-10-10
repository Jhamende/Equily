package be.equily.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import be.equily.app.domain.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "records")
data class Record(@PrimaryKey val id: String, val type: String, val body: String)

@Dao
interface RecordsDao {
    @Query("SELECT * FROM records ORDER BY id") fun observe(): Flow<List<Record>>
    @Query("SELECT * FROM records ORDER BY id") suspend fun all(): List<Record>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(record: Record)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(records: List<Record>)
    @Query("DELETE FROM records") suspend fun clear()
    @Query("DELETE FROM records WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [Record::class], version = 1, exportSchema = true)
abstract class EquilyDatabase : RoomDatabase() { abstract fun records(): RecordsDao }

data class Dossier(
    val self: String = "",
    val other: String = "",
    val children: String = "",
    val start: LocalDate = LocalDate.now(),
    val separation: LocalDate? = null,
    val selfBasisPoints: Int = 5000,
    val openingBalance: Long = 0,
)
data class Document(val id: String, val chargeId: String?, val mime: String, val hash: String, val size: Long)
data class Agreement(val id: String, val title: String, val category: String, val selfBasisPoints: Int, val effective: LocalDate, val notes: String)
data class Snapshot(val records: List<Record>) {
    val charges get() = records.filter { it.type == "charge" }.map { Codec.charge(it) }
    val payments get() = records.filter { it.type == "movement" }.map { Codec.movement(it) }
    val movements get() = payments.flatMap { it.expanded() }
    val recurring get() = records.filter { it.type == "recurring" }.map { Codec.recurring(it) }
    val documents get() = records.filter { it.type == "document" }.map { Codec.document(it) }
    val agreements get() = records.filter { it.type == "agreement" }.map { Codec.agreement(it) }
    val dossier get() = records.firstOrNull { it.type == "dossier" }?.let { Codec.dossier(it) }
    fun split(category: String, date: LocalDate): Int = agreements.filter { it.category in listOf(category, "*") && it.effective <= date }
        .sortedWith(compareBy<Agreement> { it.effective }.thenBy { if (it.category == category) 1 else 0 })
        .lastOrNull()?.selfBasisPoints ?: dossier?.selfBasisPoints ?: 5000
    fun validate() {
        require(records.size <= 100000)
        require(records.map { it.id }.distinct().size == records.size)
        require(records.count { it.type == "dossier" } <= 1)
        require(records.all { it.id.matches(Regex("[a-zA-Z0-9:_-]{1,100}")) && it.body.length <= 100000 && it.type in setOf("dossier", "charge", "movement", "recurring", "document", "agreement", "audit") })
        val chargeIds = charges.map { it.id }.toSet()
        require(payments.all { (it.chargeId == null || it.chargeId in chargeIds) && it.allocations.keys.all { id -> id in chargeIds } })
        val evidenceOwners = chargeIds + agreements.map { it.id } + payments.map { it.id }
        require(documents.all { it.chargeId == null || it.chargeId in evidenceOwners })
        require(documents.all { it.id.matches(Regex("[a-f0-9-]{36}")) && it.size in 1..25_000_000 && it.hash.matches(Regex("[a-f0-9]{64}")) && it.mime in setOf("image/jpeg", "image/png", "application/pdf") })
        require(agreements.all { it.selfBasisPoints in 0..10000 })
        require(recurring.all { it.cents in 1..MAX_CENTS && it.selfBasisPoints in 0..10000 && (it.end == null || it.end >= it.first) })
        val seeded = recurring.mapNotNull { it.seedChargeId }
        require(seeded.distinct().size == seeded.size)
        require(recurring.all { rule -> rule.seedChargeId == null || charges.any { it.id == rule.seedChargeId && it.date < rule.first && it.ruleId == null } })
        dossier?.let { require(it.selfBasisPoints in 0..10000 && it.openingBalance in -MAX_CENTS..MAX_CENTS) }
        Ledger.summarize(charges, movements)
    }
}

object Codec {
    private fun JSONObject.exactLong(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "Expected integer" }
        return (value as Number).toLong()
    }
    private fun JSONObject.exactInt(key: String): Int {
        val value = exactLong(key)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return value.toInt()
    }
    private fun Record.json() = JSONObject(body)
    private fun JSONObject.optional(key: String) = optString(key, "").takeIf { it.isNotBlank() }
    fun charge(record: Record): Charge = with(record.json()) {
        Charge(record.id, getString("description"), exactLong("cents"), LocalDate.parse(getString("date")), exactInt("split"), EntryStatus.valueOf(getString("status")), getString("category"), optString("child"), optString("notes"), optional("due")?.let(LocalDate::parse), optional("rule"), optBoolean("archived"))
    }
    fun record(charge: Charge) = Record(charge.id, "charge", JSONObject().apply {
        put("description", charge.description); put("cents", charge.cents); put("date", charge.date); put("split", charge.selfBasisPoints); put("status", charge.status); put("category", charge.category); put("child", charge.child); put("notes", charge.notes); put("due", charge.dueDate?.toString() ?: ""); put("rule", charge.ruleId ?: ""); put("archived", charge.archived)
    }.toString())
    fun movement(record: Record): Movement = with(record.json()) {
        val splits = optJSONObject("allocations")
        val allocations = splits?.keys()?.asSequence()?.associateWith { splits.exactLong(it) } ?: emptyMap()
        Movement(record.id, PaymentKind.valueOf(getString("kind")), exactLong("cents"), LocalDate.parse(getString("date")), Party.valueOf(getString("source")), Party.valueOf(getString("destination")), optional("charge"), optString("reference"), allocations)
    }
    fun record(movement: Movement) = Record(movement.id, "movement", JSONObject().apply {
        put("kind", movement.kind); put("cents", movement.cents); put("date", movement.date); put("source", movement.source); put("destination", movement.destination); put("charge", movement.chargeId ?: ""); put("reference", movement.reference); put("allocations", JSONObject(movement.allocations))
    }.toString())
    fun dossier(record: Record): Dossier = with(record.json()) {
        Dossier(optString("self"), optString("other"), optString("children"), LocalDate.parse(getString("start")), optional("separation")?.let(LocalDate::parse), exactInt("split"), if (has("opening")) exactLong("opening") else 0L)
    }
    fun record(dossier: Dossier) = Record("dossier", "dossier", JSONObject().apply {
        put("self", dossier.self); put("other", dossier.other); put("children", dossier.children); put("start", dossier.start); put("separation", dossier.separation?.toString() ?: ""); put("split", dossier.selfBasisPoints); put("opening", dossier.openingBalance)
    }.toString())
    fun recurring(record: Record): RecurringRule = with(record.json()) {
        val first = LocalDate.parse(getString("first"))
        val pauses = optJSONArray("pauses")?.let { array -> (0 until array.length()).map { index ->
            val pause = array.getJSONObject(index)
            RecurringPause(LocalDate.parse(pause.getString("from")), pause.optional("resumed")?.let(LocalDate::parse))
        } }.orEmpty()
        RecurringRule(record.id, getString("description"), exactLong("cents"), first, Frequency.valueOf(getString("frequency")), exactInt("split"), getString("category"), optional("end")?.let(LocalDate::parse), optBoolean("paused"), pauses, if (has("anchorDay")) exactInt("anchorDay") else first.dayOfMonth, optString("child"), optString("notes"), optional("seedCharge"))
    }
    fun record(rule: RecurringRule) = Record(rule.id, "recurring", JSONObject().apply {
        put("description", rule.description); put("cents", rule.cents); put("first", rule.first); put("frequency", rule.frequency); put("split", rule.selfBasisPoints); put("category", rule.category); put("end", rule.end?.toString() ?: ""); put("paused", rule.paused)
        put("anchorDay", rule.anchorDay); put("child", rule.child); put("notes", rule.notes); put("seedCharge", rule.seedChargeId ?: "")
        put("pauses", JSONArray(rule.pauses.map { pause -> JSONObject().put("from", pause.from).put("resumed", pause.resumed?.toString() ?: "") }))
    }.toString())
    fun document(record: Record): Document = with(record.json()) { Document(record.id, optional("charge"), getString("mime"), getString("hash"), exactLong("size")) }
    fun record(document: Document) = Record(document.id, "document", JSONObject().apply { put("charge", document.chargeId ?: ""); put("mime", document.mime); put("hash", document.hash); put("size", document.size) }.toString())
    fun agreement(record: Record): Agreement = with(record.json()) { Agreement(record.id, getString("title"), getString("category"), exactInt("split"), LocalDate.parse(getString("effective")), optString("notes")) }
    fun record(agreement: Agreement) = Record(agreement.id, "agreement", JSONObject().apply { put("title", agreement.title); put("category", agreement.category); put("split", agreement.selfBasisPoints); put("effective", agreement.effective); put("notes", agreement.notes) }.toString())
    fun encode(records: List<Record>): ByteArray = JSONObject().apply {
        put("format", 1)
        put("records", JSONArray().apply { records.forEach { put(JSONObject().put("id", it.id).put("type", it.type).put("body", it.body)) } })
    }.toString().toByteArray(Charsets.UTF_8)
    fun decode(bytes: ByteArray): Snapshot {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.exactInt("format") == 1) { "Unsupported archive version" }
        val records = root.getJSONArray("records")
        return Snapshot((0 until records.length()).map { index -> records.getJSONObject(index).let { Record(it.getString("id"), it.getString("type"), it.getString("body")) } }).also { it.validate() }
    }
}

interface EquilyRepository {
    val snapshots: Flow<Snapshot>
    suspend fun save(record: Record)
    suspend fun refreshRecurring()
}

class LocalRepository(context: Context, databaseName: String = "equily.db") : EquilyRepository {
    val database = Room.databaseBuilder(context, EquilyDatabase::class.java, databaseName).build()
    val mutex = Mutex()
    override val snapshots = database.records().observe().map(::Snapshot)
    suspend fun snapshot() = Snapshot(database.records().all())
    override suspend fun save(record: Record) = saveBatch(listOf(record))
    suspend fun saveBatch(records: List<Record>, beforeWrite: () -> Unit = {}) = mutex.withLock {
        beforeWrite()
        saveBatchLocked(records)
    }
    private suspend fun saveBatchLocked(records: List<Record>) {
        database.withTransaction {
            val before = database.records().all()
            Snapshot(before.filterNot { old -> records.any { it.id == old.id } } + records).validate()
            for (record in records) {
                val previous = before.firstOrNull { it.id == record.id }
                database.records().put(record)
                database.records().put(Record(UUID.randomUUID().toString(), "audit", JSONObject().apply {
                    put("record", record.id); put("time", java.time.Instant.now()); put("before", previous?.body ?: ""); put("after", record.body); put("provenance", "local-user-unverified")
                }.toString()))
            }
        }
    }
    suspend fun removeDocument(id: String, onUnused: (String) -> Unit = {}) = mutex.withLock {
        var hash: String? = null
        database.withTransaction {
            val existing = database.records().all().first { it.id == id && it.type == "document" }
            hash = Codec.document(existing).hash
            database.records().delete(id)
            database.records().put(Record(UUID.randomUUID().toString(), "audit", JSONObject().put("record", id).put("time", java.time.Instant.now()).put("before", existing.body).put("after", "removed").toString()))
        }
        hash?.let { removed -> if (snapshot().documents.none { it.hash == removed }) onUnused(removed) }
    }
    override suspend fun refreshRecurring() = mutex.withLock {
        database.withTransaction {
            val current = snapshot()
            val existing = current.charges.map { it.id }.toSet()
            val generated = current.recurring.flatMap { it.generate(LocalDate.now(), existing) }
            if (generated.isNotEmpty()) database.records().insert(generated.map(Codec::record))
        }
    }
    suspend fun setRecurringPaused(id: String, paused: Boolean, effective: LocalDate = LocalDate.now()) = mutex.withLock {
        val rule = snapshot().recurring.first { it.id == id }
        saveBatchLocked(listOf(Codec.record(rule.setPaused(paused, effective))))
    }
    suspend fun reviseRecurring(id: String, replacement: RecurringRule) = mutex.withLock {
        val current = snapshot()
        val previous = current.recurring.first { it.id == id }
        require(replacement.id != id && current.records.none { it.id == replacement.id })
        require(replacement.first > previous.first)
        require(current.charges.none { it.ruleId == id && it.date >= replacement.first })
        saveBatchLocked(listOf(Codec.record(previous.copy(end = replacement.first.minusDays(1))), Codec.record(replacement)))
    }
}
