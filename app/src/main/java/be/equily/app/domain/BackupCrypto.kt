package be.equily.app.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

fun InputStream.copyLimited(output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return total
        require(count.toLong() <= limit - total) { "File too large" }
        output.write(buffer, 0, count)
        total += count
    }
}

private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var total = 0L
    override fun read(): Int = `in`.read().also { if (it >= 0) consumed(1) }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also { if (it > 0) consumed(it) }
    private fun consumed(count: Int) {
        require(count.toLong() <= limit - total) { "File too large" }
        total += count
    }
}

object BackupCrypto {
    const val MAX_CLEAR_BYTES = 1_000_000_000L
    const val MAX_ENCRYPTED_BYTES = MAX_CLEAR_BYTES + 1_000_000L
    private const val ITERATIONS = 600000
    private const val LEGACY_LIMIT = 100_000_000L
    private val magic = byteArrayOf(69, 81, 85, 73, 76, 89)
    private fun key(passphrase: CharArray, salt: ByteArray): ByteArray {
        val specification = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded }
        finally { specification.clearPassword() }
    }
    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        require(passphrase.size >= 12) { "Use at least 12 characters" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val header = magic + byteArrayOf(2) + salt
        val material = key(passphrase, salt)
        try {
            val primitive = AesGcmHkdfStreaming(material, "HmacSHA256", 32, 65536, 0)
            output.write(header)
            primitive.newEncryptingStream(output, header).use { input.copyLimited(it, MAX_CLEAR_BYTES) }
        } finally { material.fill(0) }
    }
    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val archive = DataInputStream(LimitedInputStream(input, MAX_ENCRYPTED_BYTES))
        val actualMagic = ByteArray(magic.size).also(archive::readFully)
        require(actualMagic.contentEquals(magic)) { "Unsupported backup format" }
        val version = archive.readUnsignedByte()
        require(version in 1..2) { "Unsupported backup format" }
        val salt = ByteArray(16).also(archive::readFully)
        val material = key(passphrase, salt)
        try {
            val header = actualMagic + byteArrayOf(version.toByte()) + salt
            if (version == 2) {
                val primitive = AesGcmHkdfStreaming(material, "HmacSHA256", 32, 65536, 0)
                primitive.newDecryptingStream(archive, header).use { it.copyLimited(output, MAX_CLEAR_BYTES) }
            } else {
                val nonce = ByteArray(12).also(archive::readFully)
                val encrypted = ByteArrayOutputStream()
                archive.copyLimited(encrypted, LEGACY_LIMIT - header.size - nonce.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(material, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(header + nonce)
                output.write(cipher.doFinal(encrypted.toByteArray()))
            }
        } finally { material.fill(0) }
    }
    fun encrypt(cleartext: ByteArray, passphrase: CharArray): ByteArray = ByteArrayOutputStream().also {
        encrypt(ByteArrayInputStream(cleartext), it, passphrase)
    }.toByteArray()
    fun decrypt(archive: ByteArray, passphrase: CharArray): ByteArray = ByteArrayOutputStream().also {
        decrypt(ByteArrayInputStream(archive), it, passphrase)
    }.toByteArray()
}
