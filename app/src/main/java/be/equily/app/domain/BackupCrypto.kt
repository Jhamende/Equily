package be.equily.app.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val ITERATIONS = 600000
    private val magic = byteArrayOf(69, 81, 85, 73, 76, 89, 1)
    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val specification = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded, "AES") }
        finally { specification.clearPassword() }
    }
    fun encrypt(cleartext: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 12) { "Use at least 12 characters" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val header = magic + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { it.write(header); it.write(cipher.doFinal(cleartext)) }
        }.toByteArray()
    }
    fun decrypt(archive: ByteArray, passphrase: CharArray): ByteArray {
        require(archive.size in (magic.size + 16 + 12 + 16)..100_000_000)
        val input = DataInputStream(ByteArrayInputStream(archive))
        val actualMagic = ByteArray(magic.size).also(input::readFully)
        require(actualMagic.contentEquals(magic)) { "Unsupported backup format" }
        val salt = ByteArray(16).also(input::readFully)
        val nonce = ByteArray(12).also(input::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(actualMagic + salt + nonce)
        return cipher.doFinal(input.readBytes())
    }
}
