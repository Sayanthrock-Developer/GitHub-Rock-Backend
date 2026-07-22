package com.sayanthrock.githubrock.backend.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookVerifier(private val secret: String) {
    fun verify(payload: ByteArray, signatureHeader: String?): Boolean {
        if (secret.isBlank() || signatureHeader.isNullOrBlank() || !signatureHeader.startsWith("sha256=")) return false
        val expected = hmacSha256(payload)
        val provided = signatureHeader.removePrefix("sha256=").lowercase()
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            provided.toByteArray(StandardCharsets.US_ASCII),
        )
    }

    private fun hmacSha256(payload: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload).joinToString("") { "%02x".format(it) }
    }
}
