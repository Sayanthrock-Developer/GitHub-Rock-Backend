package com.sayanthrock.githubrock.backend.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookVerifierTest {
    @Test
    fun `accepts valid sha256 signature`() {
        val verifier = WebhookVerifier("secret")
        val payload = "{\"zen\":\"Keep it logically awesome.\"}".encodeToByteArray()
        assertTrue(
            verifier.verify(
                payload,
                "sha256=b4d0fd3983e1d5612eaebe005a2092e7176a5e0e6a583899433148eb91c11b4e",
            )
        )
    }

    @Test
    fun `rejects missing and malformed signatures`() {
        val verifier = WebhookVerifier("secret")
        assertFalse(verifier.verify("payload".encodeToByteArray(), null))
        assertFalse(verifier.verify("payload".encodeToByteArray(), "sha1=abc"))
    }
}
