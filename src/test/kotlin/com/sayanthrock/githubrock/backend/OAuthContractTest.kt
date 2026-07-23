package com.sayanthrock.githubrock.backend

import com.sayanthrock.githubrock.backend.model.GitHubTokenResponse
import com.sayanthrock.githubrock.backend.service.GITHUB_ROCK_OAUTH_SCOPES
import com.sayanthrock.githubrock.backend.service.toDevicePollResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuthContractTest {
    @Test
    fun `backend requests every Android GitHub scope`() {
        val scopes = GITHUB_ROCK_OAUTH_SCOPES.split(' ').toSet()
        assertTrue(
            setOf(
                "repo",
                "workflow",
                "read:user",
                "user:email",
                "read:org",
                "notifications",
                "user:follow",
            ).all(scopes::contains)
        )
    }

    @Test
    fun `authorized token response preserves refresh metadata`() {
        val result = GitHubTokenResponse(
            accessToken = "access",
            tokenType = "bearer",
            scope = GITHUB_ROCK_OAUTH_SCOPES,
            expiresIn = 28_800L,
            refreshToken = "refresh",
            refreshTokenExpiresIn = 15_811_200L,
        ).toDevicePollResponse()

        assertEquals("authorized", result.state)
        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertEquals(28_800L, result.expiresIn)
        assertEquals(15_811_200L, result.refreshTokenExpiresIn)
    }

    @Test
    fun `pending and slow down states remain explicit`() {
        assertEquals(
            "pending",
            GitHubTokenResponse(error = "authorization_pending").toDevicePollResponse().state,
        )
        assertEquals(
            "slow_down",
            GitHubTokenResponse(error = "slow_down", interval = 10).toDevicePollResponse().state,
        )
    }
}
