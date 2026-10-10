package be.equily.app

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import be.equily.app.data.*
import be.equily.app.domain.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
private val LocalAppLocked = staticCompositionLocalOf { false }
private fun date(value: String) = LocalDate.parse(value.trim(), dateFormat)
private fun optionalDate(value: String) = value.takeIf { it.isNotBlank() }?.let(::date)
private fun percent(value: String) = BigDecimal(value.replace(',', '.')).movePointRight(2).intValueExact().also { require(it in 0..10000) }
private fun decimal(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
private val categories = listOf("general", "medical", "school", "childcare", "extracurricular", "clothing", "mortgage", "insurance", "property_tax", "maintenance", "repairs", "utilities", "occupancy")
private val categoryResources = listOf(R.string.general, R.string.medical, R.string.school, R.string.childcare, R.string.extracurricular, R.string.clothing, R.string.mortgage, R.string.insurance, R.string.property_tax, R.string.maintenance, R.string.repairs, R.string.utilities, R.string.occupancy)
private fun partyResource(party: Party) = when (party) { Party.SELF -> R.string.self; Party.OTHER -> R.string.other; Party.SHARED -> R.string.shared; Party.UNKNOWN -> R.string.unknown }
private fun statusResource(status: EntryStatus) = when (status) { EntryStatus.DRAFT -> R.string.draft; EntryStatus.DECLARED -> R.string.declared; EntryStatus.ACCEPTED -> R.string.accepted; EntryStatus.CONTESTED -> R.string.contested; EntryStatus.ARCHIVED -> R.string.archived }
private fun kindResource(kind: PaymentKind) = when (kind) { PaymentKind.SUPPLIER -> R.string.supplier; PaymentKind.TRANSFER -> R.string.transfer; PaymentKind.CONTRIBUTION -> R.string.contribution; PaymentKind.REFUND -> R.string.refund; PaymentKind.CREDIT -> R.string.credit }

@Composable
fun EquilyApp(model: EquilyModel, unlock: () -> Unit, canLock: () -> Boolean) {
    val preferences by model.preferences.collectAsStateWithLifecycle()
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localizedConfiguration = remember(configuration, preferences.language) { Configuration(configuration).apply { setLocale(Locale.forLanguageTag(preferences.language)) } }
    val localized = remember(base, localizedConfiguration) { android.view.ContextThemeWrapper(base, R.style.Theme_Equily).apply { applyOverrideConfiguration(localizedConfiguration) } }
    val dark = preferences.theme == "dark" || (preferences.theme == "system" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color(0xFF71E0C6), secondary = Color(0xFFB5C2EA), background = Color(0xFF10172A), surface = Color(0xFF19233B))
    else lightColorScheme(primary = Color(0xFF16213E), secondary = Color(0xFF176C5C), background = Color(0xFFF4F6FA), surface = Color.White, primaryContainer = Color(0xFFD1F5EB))
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localizedConfiguration) {
        MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
            val snapshot by model.snapshot.collectAsStateWithLifecycle()
            val ready by model.ready.collectAsStateWithLifecycle()
            val preferencesReady by model.preferencesReady.collectAsStateWithLifecycle()
            val locked by model.locked.collectAsStateWithLifecycle()
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(locked) { if (locked) { focus.clearFocus(force = true); keyboard?.hide() } }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (!ready || !preferencesReady) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("Equily", style = MaterialTheme.typography.displayMedium); CircularProgressIndicator() }
                } else Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalAppLocked provides locked) {
                        Box(Modifier.focusProperties { onEnter = { if (locked) cancelFocusChange() } }.focusGroup().fillMaxSize().graphicsLayer { alpha = if (locked) 0f else 1f }.then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) {
                            if (snapshot.dossier == null) Onboarding(model) else MainNavigation(model, snapshot, preferences, canLock)
                        }
                    }
                    if (locked) Surface(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    }, color = MaterialTheme.colorScheme.background) {
                        BackHandler {}
                        Page(R.string.locked) { Action(R.string.unlock, Icons.Default.Lock, unlock) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        content()
        Spacer(Modifier.height(24.dp))
    }
}
@Composable
private fun Field(label: Int, state: MutableState<String>, numeric: Boolean = false, password: Boolean = false) {
    OutlinedTextField(value = state.value, onValueChange = { state.value = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (numeric) KeyboardType.Decimal else KeyboardType.Text, autoCorrectEnabled = !password), visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
@Composable
private fun text(initial: String = "") = remember { mutableStateOf(initial) }
@Composable
private fun Action(label: Int, icon: ImageVector = Icons.Default.Add, onClick: () -> Unit) {
    FilledTonalButton(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(stringResource(label)) }
}
@Composable
private fun <Value> Choice(label: Int, values: List<Value>, current: Value, display: @Composable (Value) -> String, changed: (Value) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(label) + " : " + display(current)); Icon(Icons.Default.KeyboardArrowDown, null) }
        DropdownMenu(expanded && !LocalAppLocked.current, { expanded = false }) { values.forEach { value -> DropdownMenuItem(text = { Text(display(value)) }, onClick = { changed(value); expanded = false }) } }
    }
}
@Composable
private fun Save(model: EquilyModel, onClick: () -> Unit) {
    val busy by model.busy.collectAsStateWithLifecycle()
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) { Text(stringResource(if (busy) R.string.working else R.string.save)) }
}

@Composable
private fun Onboarding(model: EquilyModel) {
    val self = text(); val other = text(); val children = text(); val start = text(LocalDate.now().format(dateFormat)); val separation = text(); val split = text("50"); val opening = text("0")
    Page(R.string.welcome) {
        Text("Equily", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge)
        Field(R.string.self_name, self); Field(R.string.other_name, other); Field(R.string.children, children)
        Field(R.string.start_date, start); Field(R.string.separation_date, separation); Field(R.string.split, split, true); Field(R.string.opening, opening, true)
        Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodySmall)
        Save(model) { model.perform(notify = false) { val signed = opening.value.trim(); val amount = Money.parse(signed.removePrefix("-")); model.repository.save(Codec.record(Dossier(self.value.trim(), other.value.trim(), children.value.trim(), date(start.value), optionalDate(separation.value), percent(split.value), if (signed.startsWith("-")) -amount else amount))) } }
        Feedback(model)
    }
}

@Composable
private fun Feedback(model: EquilyModel) {
    val message by model.message.collectAsStateWithLifecycle()
    message?.let { Text(stringResource(it), color = if (it == R.string.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavigation(model: EquilyModel, snapshot: Snapshot, preferences: Preferences, canLock: () -> Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val tabs = listOf(Triple("dashboard", R.string.dashboard, Icons.Default.Home), Triple("history", R.string.history, Icons.Default.List), Triple("recurring", R.string.recurring, Icons.Default.DateRange), Triple("settings", R.string.settings, Icons.Default.Settings))
    val snackbar = remember { SnackbarHostState() }
    val message by model.message.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(model.getApplication<android.app.Application>().let { LocalizedString(it, preferences.language, message!!) }); model.message.value = null } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Equily", fontWeight = FontWeight.Bold) }, navigationIcon = { if (tabs.none { it.first == route }) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) },
        bottomBar = { NavigationBar { tabs.forEach { (path, label, icon) -> NavigationBarItem(selected = route == path, onClick = { nav.navigate(path) { popUpTo("dashboard"); launchSingleTop = true } }, icon = { Icon(icon, null) }, label = { Text(stringResource(label), maxLines = 1) }) } } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(nav, "dashboard") {
                composable("dashboard") { Dashboard(snapshot, { nav.navigate("expense") }, { nav.navigate("scan") }, { nav.navigate("payment") }, { nav.navigate("detail/$it") }) }
                composable("history") { History(snapshot, { nav.navigate("detail/$it") }, { nav.navigate("payments") }) }
                composable("expense") { ExpenseForm(model, snapshot, null, false) { nav.popBackStack() } }
                composable("edit/{id}") { backStack -> snapshot.charges.firstOrNull { it.id == backStack.arguments?.getString("id") }?.let { ExpenseForm(model, snapshot, it, false) { nav.popBackStack() } } }
                composable("detail/{id}") { backStack -> snapshot.charges.firstOrNull { it.id == backStack.arguments?.getString("id") }?.let { Details(model, snapshot, it, { nav.navigate("edit/${it.id}") }, { nav.navigate("payment/${it.id}") }, { nav.navigate("new-recurring/${it.id}") }) } }
                composable("scan") { Scan(model) { nav.navigate("review") } }
                composable("review") { ExpenseForm(model, snapshot, null, true) { nav.popBackStack("dashboard", false) } }
                composable("payment") { PaymentForm(model, snapshot, null) { nav.popBackStack() } }
                composable("payment/{id}") { PaymentForm(model, snapshot, it.arguments?.getString("id")) { nav.popBackStack() } }
                composable("payments") { Payments(snapshot, { nav.navigate("edit-payment/$it") }, { nav.navigate("documents/$it") }) }
                composable("edit-payment/{id}") { PaymentForm(model, snapshot, null, snapshot.payments.firstOrNull { movement -> movement.id == it.arguments?.getString("id") }) { nav.popBackStack() } }
                composable("recurring") { Recurrences(model, snapshot, { nav.navigate("new-recurring") }, { nav.navigate("edit-recurring/$it") }) }
                composable("new-recurring") { RecurringForm(model, snapshot) { nav.popBackStack() } }
                composable("new-recurring/{id}") { RecurringForm(model, snapshot, seeded = snapshot.charges.firstOrNull { charge -> charge.id == it.arguments?.getString("id") }) { nav.popBackStack() } }
                composable("edit-recurring/{id}") { RecurringForm(model, snapshot, snapshot.recurring.firstOrNull { rule -> rule.id == it.arguments?.getString("id") }) { nav.popBackStack() } }
                composable("agreements") { Agreements(snapshot, { nav.navigate("new-agreement") }, { nav.navigate("documents/$it") }) }
                composable("new-agreement") { AgreementForm(model) { nav.popBackStack() } }
                composable("documents") { Documents(model, snapshot, null) }
                composable("documents/{id}") { Documents(model, snapshot, it.arguments?.getString("id")) }
                composable("reports") { ReportPage(model, snapshot) }
                composable("backup") { BackupPage(model) }
                composable("settings") { Settings(model, preferences, canLock) { nav.navigate(it) } }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
private fun LocalizedString(context: android.content.Context, language: String, resource: Int): String = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).getString(resource)

@Composable
private fun MonthSelector(month: YearMonth, annual: Boolean = false, changed: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton({ changed(month.minusMonths(if (annual) 12 else 1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
        Text(month.format(DateTimeFormatter.ofPattern(if (annual) "yyyy" else "MMMM yyyy", LocalConfiguration.current.locales[0])), fontWeight = FontWeight.SemiBold)
        IconButton({ changed(month.plusMonths(if (annual) 12 else 1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next)) }
    }
}
@Composable
private fun Metric(label: Int, cents: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(label), Modifier.weight(1f)); Text(Money.format(cents), fontWeight = FontWeight.SemiBold) }
}
@Composable
private fun Dashboard(snapshot: Snapshot, expense: () -> Unit, scan: () -> Unit, payment: () -> Unit, detail: (String) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val tracking = requireNotNull(snapshot.dossier)
    val active = snapshot.charges.filter { it.date >= tracking.start }
    val activeIds = active.map { it.id }.toSet()
    val movements = snapshot.movements.filter { it.date >= tracking.start && (it.chargeId == null || it.chargeId in activeIds) }
    val cumulative = Ledger.summarize(active, movements)
    val selected = active.filter { YearMonth.from(it.date) == month }
    val ids = selected.map { it.id }.toSet()
    val summary = Ledger.summarize(selected, movements.filter { it.chargeId in ids || (it.chargeId == null && YearMonth.from(it.date) == month) })
    Page(R.string.dashboard) {
        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cumulative), style = MaterialTheme.typography.labelLarge)
                Text(Money.format(cumulative.otherOwesSelf + tracking.openingBalance), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.balance_help), style = MaterialTheme.typography.bodySmall)
                if (cumulative.qualified) Text(stringResource(R.string.qualified), fontWeight = FontWeight.Bold)
            }
        }
        MonthSelector(month) { month = it }
        Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(R.string.declared, summary.declared); Metric(R.string.accepted, summary.accepted); Metric(R.string.contested, summary.contested)
            HorizontalDivider(); Metric(R.string.your_share, summary.allocated.self); Metric(R.string.other_share, summary.allocated.other)
            Metric(R.string.paid, summary.supplierPaid); Metric(R.string.outstanding, summary.outstanding)
            val total = summary.supplierPaid + summary.outstanding
            if (total > 0) {
                val fraction = BigDecimal.valueOf(summary.supplierPaid).divide(BigDecimal.valueOf(total), 6, java.math.RoundingMode.HALF_UP).toFloat()
                val progress by animateFloatAsState(fraction, label = "supplier payment progress")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider()
            Text(stringResource(R.string.source), style = MaterialTheme.typography.titleMedium)
            Party.entries.forEach { party -> Metric(partyResource(party), movements.filter { it.kind == PaymentKind.SUPPLIER && it.source == party && it.chargeId in ids }.sumOf { it.cents }) }
            Metric(R.string.shared, cumulative.sharedCash)
        } }
        Action(R.string.add_expense, Icons.Default.Add, expense); Action(R.string.scan, Icons.Default.Search, scan); Action(R.string.add_payment, Icons.Default.ShoppingCart, payment)
        Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleLarge)
        if (active.isEmpty()) Text(stringResource(R.string.empty))
        active.filterNot { it.archived }.sortedByDescending { it.date }.take(5).forEach { ExpenseCard(it) { detail(it.id) } }
    }
}

@Composable
private fun ExpenseCard(charge: Charge, onClick: () -> Unit) {
    Card(onClick, Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) { Text(charge.description, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(Money.format(charge.cents), fontWeight = FontWeight.Bold) }
        Text(charge.date.format(dateFormat) + " · " + stringResource(statusResource(charge.status)), style = MaterialTheme.typography.bodySmall)
        if (charge.child.isNotBlank()) Text(charge.child, style = MaterialTheme.typography.labelMedium)
    } }
}

@Composable
private fun ExpenseForm(model: EquilyModel, snapshot: Snapshot, existing: Charge?, scanned: Boolean, done: () -> Unit) {
    val candidate = if (scanned) model.scanCandidate else null
    val description = text(existing?.description ?: candidate?.supplier.orEmpty())
    val amount = text(existing?.cents?.let(::decimal) ?: candidate?.cents?.let(::decimal).orEmpty())
    val expenseDate = text((existing?.date ?: candidate?.date ?: LocalDate.now()).format(dateFormat))
    val due = text(existing?.dueDate?.format(dateFormat).orEmpty())
    val split = text(decimal((existing?.selfBasisPoints ?: snapshot.split("general", LocalDate.now())).toLong()))
    val child = text(existing?.child.orEmpty()); val notes = text(existing?.notes ?: candidate?.number.orEmpty())
    var category by remember { mutableStateOf(existing?.category ?: "general") }
    var paid by remember { mutableStateOf(false) }
    var payer by remember { mutableStateOf(Party.UNKNOWN) }
    Page(if (scanned) R.string.ocr_review else if (existing == null) R.string.add_expense else R.string.edit) {
        if (scanned) {
            Text(stringResource(R.string.ocr_uncertain)); Text(stringResource(R.string.invoice_warning))
            if (model.scanFailed) Text(stringResource(R.string.ocr_failed))
            if (model.scanDuplicate) Text(stringResource(R.string.duplicate), color = MaterialTheme.colorScheme.error)
            candidate?.alternatives?.takeIf { it.size > 1 }?.let { Text(stringResource(R.string.ocr_alternatives) + ": " + it.joinToString { value -> Money.format(value) }) }
            model.scanDocument?.let { DocumentRow(model, it, false) }
        }
        Field(R.string.description, description); Field(R.string.amount, amount, true); Field(R.string.date, expenseDate); Field(R.string.due, due)
        Choice(R.string.category, categories, category, { stringResource(categoryResources[categories.indexOf(it)]) }) { category = it; if (existing == null) split.value = decimal(snapshot.split(it, runCatching { date(expenseDate.value) }.getOrDefault(LocalDate.now())).toLong()) }
        Field(R.string.child, child); Field(R.string.split, split, true); Field(R.string.notes, notes)
        if (existing == null) {
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(paid, { paid = it }); Spacer(Modifier.width(12.dp)); Text(stringResource(if (paid) R.string.paid_full else R.string.not_paid)) }
            if (paid) Choice(R.string.source, Party.entries, payer, { stringResource(partyResource(it)) }) { payer = it }
        }
        Save(model) { model.perform(done, notify = false) {
            require(description.value.isNotBlank())
            val charge = Charge(existing?.id ?: UUID.randomUUID().toString(), description.value.trim(), Money.parse(amount.value), date(expenseDate.value), percent(split.value), existing?.status ?: EntryStatus.DECLARED, category, child.value.trim(), notes.value.trim(), optionalDate(due.value), existing?.ruleId, existing?.archived ?: false)
            val records = mutableListOf(Codec.record(charge))
            if (paid && existing == null) records += Codec.record(Movement(kind = PaymentKind.SUPPLIER, cents = charge.cents, date = charge.date, source = payer, chargeId = charge.id))
            if (scanned) model.scanDocument?.let { records += Codec.record(it.copy(chargeId = charge.id)) }
            model.repository.saveBatch(records)
            model.scanDocument = null; model.scanCandidate = null
        } }
    }
}

@Composable
private fun Scan(model: EquilyModel, reviewed: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.scan(it, reviewed) } }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) cameraUri?.let { model.scan(it, reviewed) } }
    Page(R.string.scan) {
        Text(stringResource(R.string.invoice_warning))
        Action(R.string.import_file, Icons.Default.Share) { picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }
        Action(R.string.camera, Icons.Default.Add) {
            val file = File(context.cacheDir, "camera/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs(); createNewFile() }
            cameraUri = FileProvider.getUriForFile(context, "be.equily.app.files", file)
            runCatching { camera.launch(cameraUri!!) }.onFailure { model.message.value = R.string.error }
        }
    }
}

@Composable
private fun Details(model: EquilyModel, snapshot: Snapshot, charge: Charge, edit: () -> Unit, payment: () -> Unit, recurring: () -> Unit) {
    val summary = Ledger.summarize(listOf(charge), snapshot.movements.filter { it.chargeId == charge.id })
    Page(R.string.details) {
        Text(charge.description, style = MaterialTheme.typography.headlineSmall)
        Text(Money.format(charge.cents), style = MaterialTheme.typography.displaySmall)
        Text(charge.date.format(dateFormat)); Text(stringResource(R.string.unverified))
        charge.dueDate?.let { Text(stringResource(R.string.due) + ": " + it.format(dateFormat)) }
        if (charge.child.isNotBlank()) Text(charge.child)
        Text(decimal(charge.selfBasisPoints.toLong()) + " % / " + decimal((10000 - charge.selfBasisPoints).toLong()) + " %")
        Metric(R.string.your_share, summary.allocated.self); Metric(R.string.other_share, summary.allocated.other); Metric(R.string.outstanding, summary.outstanding)
        Metric(R.string.balance, summary.otherOwesSelf)
        val settlement = when {
            charge.status == EntryStatus.DRAFT -> R.string.draft
            summary.supplierPaid == 0L && summary.outstanding > 0 -> R.string.not_paid
            summary.outstanding == 0L && summary.otherOwesSelf == 0L && !summary.qualified -> R.string.settled
            else -> R.string.partial
        }
        Text(stringResource(settlement))
        Choice(R.string.status, EntryStatus.entries.filterNot { it == EntryStatus.ARCHIVED }, charge.status, { stringResource(statusResource(it)) }) { status -> model.perform { model.repository.save(Codec.record(charge.copy(status = status))) } }
        if (charge.notes.isNotBlank()) Text(charge.notes)
        Action(R.string.edit, Icons.Default.Edit, edit); Action(R.string.add_payment, Icons.Default.ShoppingCart, payment)
        if (charge.ruleId == null && snapshot.recurring.none { it.seedChargeId == charge.id }) Action(R.string.new_recurring, Icons.Default.DateRange, recurring)
        Action(if (charge.archived) R.string.resume else R.string.archive, Icons.Default.Delete) { model.perform { model.repository.save(Codec.record(charge.copy(archived = !charge.archived))) } }
        DocumentList(model, snapshot, charge.id)
        Text(stringResource(R.string.audit), style = MaterialTheme.typography.titleLarge)
        snapshot.records.filter { it.type == "audit" && JSONObject(it.body).optString("record") == charge.id }.sortedByDescending { JSONObject(it.body).optString("time") }.forEach { record ->
            val audit = JSONObject(record.body)
            Text(runCatching { java.time.Instant.parse(audit.getString("time")).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrDefault(""), style = MaterialTheme.typography.labelMedium)
            listOf("before", "after").forEach { phase ->
                runCatching { Codec.charge(Record(charge.id, "charge", audit.getString(phase))) }.getOrNull()?.let { version ->
                    Text(version.description + " · " + Money.format(version.cents) + " · " + decimal(version.selfBasisPoints.toLong()) + " % · " + stringResource(statusResource(version.status)), style = MaterialTheme.typography.bodySmall)
                    if (version.notes.isNotBlank()) Text(version.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun History(snapshot: Snapshot, detail: (String) -> Unit, payments: () -> Unit) {
    val search = text()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var status by remember { mutableStateOf<EntryStatus?>(null) }
    var category by remember { mutableStateOf("*") }
    var payer by remember { mutableStateOf<Party?>(null) }
    var annual by remember { mutableStateOf(false) }
    Page(R.string.history) {
        Field(R.string.search, search); Row(verticalAlignment = Alignment.CenterVertically) { Switch(annual, { annual = it }); Text(stringResource(R.string.yearly)) }; MonthSelector(month, annual) { month = it }
        Choice(R.string.status, listOf(null) + EntryStatus.entries, status, { it?.let { value -> stringResource(statusResource(value)) } ?: stringResource(R.string.all) }) { status = it }
        Choice(R.string.category, listOf("*") + categories, category, { if (it == "*") stringResource(R.string.all) else stringResource(categoryResources[categories.indexOf(it)]) }) { category = it }
        Choice(R.string.source, listOf(null) + Party.entries, payer, { it?.let { value -> stringResource(partyResource(value)) } ?: stringResource(R.string.all) }) { payer = it }
        Action(R.string.payment_history, Icons.Default.ShoppingCart, payments)
        val filtered = snapshot.charges.filter { charge -> (if (annual) charge.date.year == month.year else YearMonth.from(charge.date) == month) && (if (status == EntryStatus.ARCHIVED) charge.archived else status == null || charge.status == status) && (category == "*" || charge.category == category) && (payer == null || snapshot.movements.any { it.chargeId == charge.id && it.source == payer }) && (charge.description + charge.category + charge.child).contains(search.value, true) }.sortedByDescending { it.date }
        val ids = filtered.map { it.id }.toSet()
        val summary = Ledger.summarize(filtered, snapshot.movements.filter { it.chargeId in ids })
        Metric(R.string.your_share, summary.allocated.self); Metric(R.string.other_share, summary.allocated.other); Metric(R.string.outstanding, summary.outstanding)
        if (filtered.isEmpty()) Text(stringResource(R.string.empty))
        filtered.forEach { ExpenseCard(it) { detail(it.id) } }
    }
}

@Composable
private fun PaymentForm(model: EquilyModel, snapshot: Snapshot, selectedId: String?, existing: Movement? = null, done: () -> Unit) {
    var kind by remember { mutableStateOf(existing?.kind ?: if (selectedId == null) PaymentKind.TRANSFER else PaymentKind.SUPPLIER) }
    var source by remember { mutableStateOf(existing?.source ?: Party.SELF) }; var destination by remember { mutableStateOf(existing?.destination ?: Party.OTHER) }
    var chargeId by remember { mutableStateOf(existing?.chargeId ?: selectedId.orEmpty()) }
    val amount = text(existing?.cents?.let(::decimal).orEmpty()); val paymentDate = text((existing?.date ?: LocalDate.now()).format(dateFormat)); val reference = text(existing?.reference.orEmpty())
    var multiple by remember { mutableStateOf(existing?.allocations?.isNotEmpty() == true) }
    val allocations = remember { mutableStateMapOf<String, String>().apply { existing?.allocations?.forEach { (id, cents) -> put(id, decimal(cents)) } } }
    Page(R.string.add_payment) {
        Choice(R.string.kind, PaymentKind.entries, kind, { stringResource(kindResource(it)) }) { kind = it; if (it == PaymentKind.CONTRIBUTION) destination = Party.SHARED }
        Field(R.string.amount, amount, true); Field(R.string.date, paymentDate)
        if (kind in listOf(PaymentKind.SUPPLIER, PaymentKind.TRANSFER, PaymentKind.CONTRIBUTION)) Choice(R.string.source, if (kind == PaymentKind.SUPPLIER) Party.entries else listOf(Party.SELF, Party.OTHER), source, { stringResource(partyResource(it)) }) { source = it }
        if (kind != PaymentKind.SUPPLIER && kind != PaymentKind.CONTRIBUTION) Choice(R.string.destination, if (kind == PaymentKind.TRANSFER) listOf(Party.SELF, Party.OTHER) else listOf(Party.SELF, Party.OTHER, Party.SHARED), destination, { stringResource(partyResource(it)) }) { destination = it }
        if (kind != PaymentKind.CONTRIBUTION) {
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(multiple, { multiple = it }); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.multiple_allocations)) }
            if (multiple) snapshot.charges.filter { it.status != EntryStatus.DRAFT }.forEach { charge ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(charge.id in allocations, { checked -> if (checked) allocations[charge.id] = "" else allocations.remove(charge.id) })
                    Text(charge.description, Modifier.weight(1f))
                }
                if (charge.id in allocations) OutlinedTextField(allocations.getValue(charge.id), { allocations[charge.id] = it }, label = { Text(stringResource(R.string.amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            } else Choice(R.string.expense_link, (if (kind == PaymentKind.TRANSFER) listOf("") else emptyList()) + snapshot.charges.map { it.id }, chargeId, { id -> snapshot.charges.firstOrNull { it.id == id }?.description ?: stringResource(R.string.all) }) { chargeId = it }
        }
        Field(R.string.reference, reference)
        Save(model) { model.perform(done, notify = false) {
            val splits = if (multiple && kind != PaymentKind.CONTRIBUTION) allocations.mapValues { Money.parse(it.value) } else emptyMap()
            require(!multiple || kind == PaymentKind.CONTRIBUTION || splits.isNotEmpty())
            model.repository.save(Codec.record(Movement(id = existing?.id ?: UUID.randomUUID().toString(), kind = kind, cents = Money.parse(amount.value), date = date(paymentDate.value), source = if (kind in listOf(PaymentKind.REFUND, PaymentKind.CREDIT)) Party.UNKNOWN else source, destination = when (kind) { PaymentKind.CONTRIBUTION -> Party.SHARED; PaymentKind.SUPPLIER -> Party.UNKNOWN; else -> destination }, chargeId = if (kind != PaymentKind.CONTRIBUTION && !multiple) chargeId.takeIf { it.isNotBlank() } else null, reference = reference.value.trim(), allocations = splits)))
        } }
    }
}

@Composable
private fun Payments(snapshot: Snapshot, edit: (String) -> Unit, documents: (String) -> Unit) {
    Page(R.string.payment_history) { snapshot.payments.sortedByDescending { it.date }.forEach { movement ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(kindResource(movement.kind)), fontWeight = FontWeight.Bold); Text(Money.format(movement.cents)); Text(movement.date.format(dateFormat))
            Text(stringResource(partyResource(movement.source)) + " → " + stringResource(partyResource(movement.destination)))
            snapshot.charges.firstOrNull { it.id == movement.chargeId }?.let { Text(it.description) }
            movement.allocations.forEach { (id, cents) -> Text(snapshot.charges.firstOrNull { it.id == id }?.description.orEmpty() + " · " + Money.format(cents)) }
            if (movement.reference.isNotBlank()) Text(movement.reference)
            Action(R.string.edit, Icons.Default.Edit) { edit(movement.id) }
            Action(R.string.documents, Icons.Default.Add) { documents(movement.id) }
        } }
    } }
}

@Composable
private fun Recurrences(model: EquilyModel, snapshot: Snapshot, add: () -> Unit, edit: (String) -> Unit) {
    Page(R.string.recurring) {
        Text(stringResource(R.string.recurring_help)); Action(R.string.new_recurring, onClick = add)
        snapshot.recurring.forEach { rule -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text(rule.description, fontWeight = FontWeight.Bold); Text(Money.format(rule.cents)); Text(rule.first.format(dateFormat))
            Action(R.string.edit, Icons.Default.Edit) { edit(rule.id) }
            Text(stringResource(R.string.anchor_day) + " : " + rule.anchorDay)
            rule.end?.let { Text(stringResource(R.string.end_date) + " : " + it.format(dateFormat)) }
            rule.pauses.forEach { pause -> Text(stringResource(R.string.pause) + " : " + pause.from.format(dateFormat) + " → " + (pause.resumed?.format(dateFormat) ?: "…")) }
            if (rule.end == null || rule.end >= LocalDate.now()) Action(if (rule.paused) R.string.resume else R.string.pause, Icons.Default.Close) { model.perform { model.repository.setRecurringPaused(rule.id, !rule.paused); model.repository.refreshRecurring() } }
        } } }
    }
}
@Composable
private fun RecurringForm(model: EquilyModel, snapshot: Snapshot, existing: RecurringRule? = null, seeded: Charge? = null, done: () -> Unit) {
    val description = text(existing?.description ?: seeded?.description.orEmpty()); val amount = text((existing?.cents ?: seeded?.cents)?.let(::decimal).orEmpty()); val first = text((seeded?.date?.plusMonths(1) ?: LocalDate.now()).format(dateFormat)); val end = text(existing?.end?.format(dateFormat).orEmpty()); val split = text(decimal((existing?.selfBasisPoints ?: seeded?.selfBasisPoints ?: snapshot.dossier?.selfBasisPoints ?: 5000).toLong()))
    var category by remember { mutableStateOf(existing?.category ?: seeded?.category ?: "mortgage") }; var frequency by remember { mutableStateOf(existing?.frequency ?: Frequency.MONTHLY) }
    val anchor = text((existing?.anchorDay ?: seeded?.date?.dayOfMonth)?.toString().orEmpty()); val child = text(existing?.child ?: seeded?.child.orEmpty()); val notes = text(existing?.notes ?: seeded?.notes.orEmpty())
    Page(R.string.new_recurring) {
        Field(R.string.description, description); Field(R.string.amount, amount, true); Field(R.string.date, first); Field(R.string.end_date, end); Field(R.string.split, split, true)
        Field(R.string.anchor_day, anchor, true); Field(R.string.child, child); Field(R.string.notes, notes)
        Text(stringResource(R.string.recurring_revision_help), style = MaterialTheme.typography.bodySmall)
        Choice(R.string.category, categories, category, { stringResource(categoryResources[categories.indexOf(it)]) }) { category = it }
        Choice(R.string.frequency, Frequency.entries, frequency, { stringResource(when (it) { Frequency.MONTHLY -> R.string.monthly; Frequency.QUARTERLY -> R.string.quarterly; Frequency.YEARLY -> R.string.yearly }) }) {
            if (seeded != null && first.value == seeded.date.plusMonths(frequency.months).format(dateFormat)) first.value = seeded.date.plusMonths(it.months).format(dateFormat)
            frequency = it
        }
        Save(model) { model.perform(done, notify = false) {
            require(description.value.isNotBlank())
            val effective = date(first.value)
            val rule = RecurringRule(UUID.randomUUID().toString(), description.value.trim(), Money.parse(amount.value), effective, frequency, percent(split.value), category, optionalDate(end.value), paused = existing?.paused ?: false, pauses = if (existing?.paused == true) listOf(RecurringPause(effective)) else emptyList(), anchorDay = anchor.value.takeIf { it.isNotBlank() }?.toInt() ?: effective.dayOfMonth, child = child.value.trim(), notes = notes.value.trim(), seedChargeId = seeded?.id)
            if (seeded != null) require(effective > seeded.date && snapshot.recurring.none { it.seedChargeId == seeded.id } && seeded.ruleId == null)
            if (existing == null) model.repository.save(Codec.record(rule)) else model.repository.reviseRecurring(existing.id, rule)
            model.repository.refreshRecurring()
        } }
    }
}

@Composable
private fun Agreements(snapshot: Snapshot, add: () -> Unit, documents: (String) -> Unit) {
    Page(R.string.agreements) { Text(stringResource(R.string.rule_help)); Action(R.string.new_agreement, onClick = add); snapshot.agreements.sortedByDescending { it.effective }.forEach { agreement -> Text(agreement.title, style = MaterialTheme.typography.titleMedium); Text(agreement.effective.format(dateFormat) + " · " + decimal(agreement.selfBasisPoints.toLong()) + " %"); Text(agreement.notes); Action(R.string.documents, Icons.Default.Add) { documents(agreement.id) } } }
}
@Composable
private fun AgreementForm(model: EquilyModel, done: () -> Unit) {
    val title = text(); val split = text("50"); val effective = text(LocalDate.now().format(dateFormat)); val notes = text(); var category by remember { mutableStateOf("*") }
    Page(R.string.new_agreement) {
        Field(R.string.description, title); Field(R.string.split, split, true); Field(R.string.effective, effective); Field(R.string.notes, notes)
        Choice(R.string.category, listOf("*") + categories, category, { if (it == "*") stringResource(R.string.all) else stringResource(categoryResources[categories.indexOf(it)]) }) { category = it }
        Save(model) { model.perform(done, notify = false) { require(title.value.isNotBlank()); model.repository.save(Codec.record(Agreement(UUID.randomUUID().toString(), title.value.trim(), category, percent(split.value), date(effective.value), notes.value.trim()))) } }
    }
}

@Composable
private fun DocumentRow(model: EquilyModel, document: Document, removable: Boolean) {
    val context = LocalContext.current
    var viewing by remember { mutableStateOf(false) }
    if (viewing && !LocalAppLocked.current) DocumentViewer(model, document) { viewing = false }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(document.mime.substringAfter('/') + " · " + document.size / 1024 + " KB", Modifier.weight(1f))
        IconButton({ viewing = true }) { Icon(Icons.Default.Search, stringResource(R.string.view)) }
        if (removable) IconButton({ model.perform { model.attachments.remove(document) } }) { Icon(Icons.Default.Delete, stringResource(R.string.remove)) }
    }
}
@Composable
private fun DocumentViewer(model: EquilyModel, document: Document, close: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val preview by produceState<Pair<android.graphics.Bitmap, Int>?>(null, document.id, page) {
        value = null
        failed = false
        try { value = model.attachments.preview(document, page) } catch (_: Exception) { failed = true }
    }
    DisposableEffect(preview) { val bitmap = preview?.first; onDispose { bitmap?.recycle() } }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.documents)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed) Text(stringResource(R.string.error)) else if (preview == null) CircularProgressIndicator()
            preview?.let { (bitmap, count) ->
                Image(bitmap.asImageBitmap(), stringResource(R.string.documents), Modifier.fillMaxWidth().heightIn(max = 420.dp))
                if (count > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ page-- }, enabled = page > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
                    Text("${page + 1} / $count")
                    IconButton({ page++ }, enabled = page + 1 < count) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next)) }
                }
            }
        }
    }, confirmButton = { TextButton(close) { Text(stringResource(R.string.back)) } }, dismissButton = {
        TextButton({ runCatching { val uri = FileProvider.getUriForFile(context, "be.equily.app.files", model.attachments.file(document)); context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { model.message.value = R.string.error } }) { Text(stringResource(R.string.view)) }
    })
}
@Composable
private fun DocumentList(model: EquilyModel, snapshot: Snapshot, chargeId: String?) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.perform { model.attachments.import(it, chargeId) } } }
    Text(stringResource(R.string.documents), style = MaterialTheme.typography.titleLarge)
    Action(R.string.import_file, Icons.Default.Add) { picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }
    snapshot.documents.filter { chargeId == null || it.chargeId == chargeId }.forEach { DocumentRow(model, it, true) }
}
@Composable
private fun Documents(model: EquilyModel, snapshot: Snapshot, chargeId: String?) { Page(R.string.documents) { DocumentList(model, snapshot, chargeId) } }

@Composable
private fun ReportPage(model: EquilyModel, snapshot: Snapshot) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val disclaimer = stringResource(R.string.disclaimer)
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { model.perform { model.reports.write(it, snapshot, month, false, disclaimer) } } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { model.perform { model.reports.write(it, snapshot, month, true, disclaimer) } } }
    Page(R.string.reports) { MonthSelector(month) { month = it }; Text(disclaimer); Action(R.string.export_csv, Icons.Default.Share) { csv.launch("Equily-$month.csv") }; Action(R.string.export_pdf, Icons.Default.List) { pdf.launch("Equily-$month.pdf") } }
}
@Composable
private fun BackupPage(model: EquilyModel) {
    val passphrase = text(); var confirm by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { val secret = passphrase.value.toCharArray(); passphrase.value = ""; model.perform { model.backups.export(it, secret) } } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { val secret = passphrase.value.toCharArray(); passphrase.value = ""; model.perform { model.backups.restore(it, secret); model.repository.refreshRecurring() } } }
    Page(R.string.backup) {
        Text(stringResource(R.string.backup_help)); Field(R.string.passphrase, passphrase, password = true)
        Action(R.string.export_backup, Icons.Default.Done) { if (passphrase.value.length >= 12) export.launch("Equily-${LocalDate.now()}.equily") else model.message.value = R.string.error }
        Action(R.string.restore, Icons.Default.Refresh) { if (passphrase.value.isNotBlank()) confirm = true else model.message.value = R.string.error }
        if (confirm && !LocalAppLocked.current) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.restore)) }, text = { Text(stringResource(R.string.restore_warning)) }, confirmButton = { TextButton({ confirm = false; restore.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }) { Text(stringResource(R.string.restore)) } }, dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
@Composable
private fun Settings(model: EquilyModel, preferences: Preferences, canLock: () -> Boolean, navigate: (String) -> Unit) {
    Page(R.string.settings) {
        Choice(R.string.language, listOf("fr", "nl", "en"), preferences.language, { when (it) { "fr" -> "Français"; "nl" -> "Nederlands"; else -> "English" } }) { model.preference("language", it) }
        Choice(R.string.theme, listOf("system", "light", "dark"), preferences.theme, { stringResource(when (it) { "dark" -> R.string.dark; "light" -> R.string.light; else -> R.string.system }) }) { model.preference("theme", it) }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(preferences.lock, { if (!it || canLock()) model.lockEnabled(it) else model.message.value = R.string.error }); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.lock)) }
        Action(R.string.agreements, Icons.Default.Build) { navigate("agreements") }; Action(R.string.documents, Icons.Default.List) { navigate("documents") }
        Action(R.string.reports, Icons.Default.Info) { navigate("reports") }; Action(R.string.backup, Icons.Default.Lock) { navigate("backup") }
        Text(stringResource(R.string.privacy)); Text("Equily 0.1.0 · Android 8+")
    }
}
