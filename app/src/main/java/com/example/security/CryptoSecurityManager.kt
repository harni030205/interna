package com.example.security

import android.util.Base64
import com.example.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-grade cryptographic utilities for Interna:
 * - PBKDF2-HMAC-SHA256 salted password hashing (never stores plaintext passwords).
 * - Constant-time byte array verification to prevent timing side-channel attacks.
 * - SHA-256 one-way hashing for email verification codes and password recovery tokens.
 * - HMAC-SHA256 role grant signing so administrator privileges cannot be forged by
 *   simply entering an email address or mutating client state.
 */
class CryptoSecurityManager(
    private val configuredAdminProvisionToken: String = runCatching { BuildConfig.ADMIN_PROVISION_TOKEN }.getOrDefault("")
) {
    private val secureRandom = SecureRandom()

    companion object {
        const val INITIAL_AUTHORIZED_ADMIN_EMAIL = "harinirinaianjara@gmail.com"
        const val UNCONFIGURED_PLACEHOLDER = "CONFIGURE_IN_SECRETS_PANEL"
        private const val MIN_PROVISION_TOKEN_LENGTH = 10
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 65_536
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    data class HashedPassword(
        val hashBase64: String,
        val saltBase64: String
    )

    fun hashPassword(plainPassword: CharArray): HashedPassword {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val derived = deriveKey(plainPassword, salt)
        return HashedPassword(
            hashBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verifyPassword(plainPassword: CharArray, expectedHashBase64: String, saltBase64: String): Boolean {
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            val actual = deriveKey(plainPassword, salt)
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun generateNumericCode(digits: Int = 6): String {
        val max = Math.pow(10.0, digits.toDouble()).toInt()
        val value = secureRandom.nextInt(max)
        return value.toString().padStart(digits, '0')
    }

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.trim().toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyCodeHash(candidateCode: String, storedHashHex: String): Boolean {
        if (storedHashHex.isBlank()) return false
        val candidateBytes = sha256Hex(candidateCode).toByteArray(StandardCharsets.UTF_8)
        val expectedBytes = storedHashHex.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(candidateBytes, expectedBytes)
    }

    fun isAdminProvisioningConfigured(): Boolean {
        val trimmed = configuredAdminProvisionToken.trim()
        return trimmed.length >= MIN_PROVISION_TOKEN_LENGTH &&
            trimmed != UNCONFIGURED_PLACEHOLDER
    }

    /**
     * Verifies the one-time secret provisioning token for initial administrator bootstrap
     * of harinirinaianjara@gmail.com using constant-time byte comparison.
     */
    fun verifyOneTimeProvisionSecret(
        normalizedEmail: String,
        suppliedToken: String
    ): Boolean {
        if (!normalizedEmail.equals(INITIAL_AUTHORIZED_ADMIN_EMAIL, ignoreCase = true)) return false
        if (!isAdminProvisioningConfigured()) return false

        val expectedBytes = configuredAdminProvisionToken.trim().toByteArray(StandardCharsets.UTF_8)
        val suppliedBytes = suppliedToken.trim().toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, suppliedBytes)
    }

    /**
     * Verifies the secret provisioning token for an already-registered verified user.
     * Never grants admin role merely because a user registered with harinirinaianjara@gmail.com.
     */
    fun verifyAdminProvisionToken(
        normalizedEmail: String,
        emailVerified: Boolean,
        suppliedToken: String
    ): Boolean {
        if (!emailVerified) return false
        return verifyOneTimeProvisionSecret(normalizedEmail, suppliedToken)
    }

    fun signRoleGrant(userId: String, normalizedEmail: String, role: String): String {
        val signingKey = if (isAdminProvisioningConfigured()) {
            configuredAdminProvisionToken.trim()
        } else {
            "interna-user-role-integrity-v1"
        }
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingKey.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        val payload = "$userId:${normalizedEmail.lowercase()}:$role"
        val signature = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun verifyRoleGrantSignature(
        userId: String,
        normalizedEmail: String,
        role: String,
        signatureBase64: String
    ): Boolean {
        if (role == "ADMIN" && !isAdminProvisioningConfigured()) return false
        val expected = signRoleGrant(userId, normalizedEmail, role)
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            signatureBase64.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
