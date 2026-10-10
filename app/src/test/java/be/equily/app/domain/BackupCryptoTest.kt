package be.equily.app.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
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
    @Test fun segmentBoundariesAndEmptyArchivesRoundTrip() {
        val secret = "a long secret passphrase".toCharArray()
        for (size in listOf(0, 65479, 65480, 65536, 131072)) {
            val original = ByteArray(size) { (it % 251).toByte() }
            val encrypted = BackupCrypto.encrypt(original, secret)
            assertEquals(2, encrypted[6].toInt())
            assertArrayEquals(original, BackupCrypto.decrypt(encrypted, secret))
        }
    }
    @Test fun truncatedAppendedReorderedAndChangedHeadersAreRejected() {
        val secret = "a long secret passphrase".toCharArray()
        val encrypted = BackupCrypto.encrypt(ByteArray(150000) { (it % 251).toByte() }, secret)
        val changedSalt = encrypted.copyOf().apply { this[7] = (this[7].toInt() xor 1).toByte() }
        val unsupported = encrypted.copyOf().apply { this[6] = 3 }
        val truncatedSegment = encrypted.copyOf(23 + 65536)
        val reordered = encrypted.copyOf().apply {
            encrypted.copyInto(this, 23, 23 + 65536, 23 + 131072)
            encrypted.copyInto(this, 23 + 65536, 23, 23 + 65536)
        }
        for (invalid in listOf(encrypted.copyOf(encrypted.size - 1), encrypted + byteArrayOf(0), changedSalt, unsupported, truncatedSegment, reordered)) {
            assertTrue(runCatching { BackupCrypto.decrypt(invalid, secret) }.isFailure)
        }
    }
    @Test fun legacyVersionOneRemainsReadableAndAuthenticated() {
        val secret = "a long secret passphrase".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(12) { (it + 32).toByte() }
        val header = byteArrayOf(69, 81, 85, 73, 76, 89, 1) + salt + nonce
        val specification = PBEKeySpec(secret, salt, 600000, 256)
        val material = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded }
        finally { specification.clearPassword() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(material, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        material.fill(0)
        val original = "Version one archive".toByteArray()
        val encrypted = header + cipher.doFinal(original)
        assertArrayEquals(original, BackupCrypto.decrypt(encrypted, secret))
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertTrue(runCatching { BackupCrypto.decrypt(encrypted, secret) }.isFailure)
    }
    @Test fun ninetyMegabytesUseBoundedStreamsInsteadOfArchiveSizedArrays() {
        val file = File.createTempFile("equily-stream-", ".equily")
        val expectedDigest = MessageDigest.getInstance("SHA-256")
        val actualDigest = MessageDigest.getInstance("SHA-256")
        val secret = "a long secret passphrase".toCharArray()
        val size = 90_000_000L
        var remaining = size
        val input = object : InputStream() {
            override fun read(): Int = if (remaining-- > 0) 42 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                assertTrue(length <= 65536)
                if (remaining == 0L) return -1
                val count = minOf(remaining, length.toLong()).toInt()
                buffer.fill(42, offset, offset + count)
                expectedDigest.update(buffer, offset, count)
                remaining -= count
                return count
            }
        }
        var restored = 0L
        val output = object : OutputStream() {
            override fun write(value: Int) { actualDigest.update(value.toByte()); restored++ }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                assertTrue(length <= 65536)
                actualDigest.update(buffer, offset, length)
                restored += length
            }
        }
        try {
            file.outputStream().buffered().use { BackupCrypto.encrypt(input, it, secret) }
            file.inputStream().buffered().use { BackupCrypto.decrypt(it, output, secret) }
            assertEquals(size, restored)
            assertArrayEquals(expectedDigest.digest(), actualDigest.digest())
        } finally { file.delete() }
    }
    @Test fun limitedCopyRejectsOversizedInputWithoutWritingPastLimit() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(17000)).copyLimited(output, 9000) }.isFailure)
        assertTrue(output.size() <= 9000)
    }
}
