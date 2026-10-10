package be.equily.app.domain

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    @Test fun roundTripAndRandomization() {
        val original = "Complete local archive".toByteArray()
        val passphrase = "a long secret passphrase".toCharArray()
        val first = BackupCrypto.encrypt(original, passphrase)
        val second = BackupCrypto.encrypt(original, passphrase)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(original, BackupCrypto.decrypt(first, passphrase))
    }
    @Test fun wrongPasswordAndTamperingFail() {
        val archive = BackupCrypto.encrypt("private".toByteArray(), "a long secret passphrase".toCharArray())
        assertTrue(runCatching { BackupCrypto.decrypt(archive, "wrong password".toCharArray()) }.isFailure)
        archive[archive.lastIndex] = (archive.last().toInt() xor 1).toByte()
        assertTrue(runCatching { BackupCrypto.decrypt(archive, "a long secret passphrase".toCharArray()) }.isFailure)
    }
}
