package me.eroi.lolidaily.muzei.api

import java.util.Base64
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthenticationTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sessionRefreshPolicyUsesFiveDayWindow() {
        val now = 1_000_000L
        val outsideWindow = Session("token", now + Session.REFRESH_WINDOW_MS + 1L)
        val atBoundary = Session("token", now + Session.REFRESH_WINDOW_MS)
        val expired = Session("token", now)

        assertTrue(outsideWindow.isValidAt(now))
        assertFalse(outsideWindow.shouldRefreshAt(now))
        assertTrue(atBoundary.shouldRefreshAt(now))
        assertFalse(expired.isValidAt(now))
        assertFalse(expired.shouldRefreshAt(now))
    }

    @Test
    fun refreshedJwtProvidesExpiryAndBangumiUsername() {
        val expiresAt = System.currentTimeMillis() + 86_400_000L
        val token = jwt(expiresAt = expiresAt, username = "alice")

        val session = SessionManager.sessionFromJwt(token)

        assertEquals(expiresAt, session?.expiresAt)
        assertEquals("alice", session?.let(SessionManager::getUsername))
    }

    @Test
    fun malformedJwtIsRejected() {
        assertNull(SessionManager.sessionFromJwt("not-a-jwt"))
    }

    @Test
    fun refreshRequestReplacesSessionFromResponseJwt() {
        val expiresAt = System.currentTimeMillis() + 86_400_000L
        val refreshedToken = jwt(expiresAt = expiresAt, username = "alice")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"jwt\":\"$refreshedToken\"}"),
        )

        val result =
            LoliApiClient.refreshSession(
                baseUrl = server.url("/").toString(),
                token = "old-token",
            )

        assertTrue(result is SessionRefreshResult.Success)
        assertEquals(refreshedToken, (result as SessionRefreshResult.Success).session.token)
        assertEquals(expiresAt, result.session.expiresAt)
        val request = server.takeRequest()
        assertEquals("/api/v1/oauth/refresh", request.path)
        assertEquals("Bearer old-token", request.getHeader("Authorization"))
    }

    @Test
    fun refreshUnauthorizedIsReportedSeparately() {
        server.enqueue(MockResponse().setResponseCode(401))

        val result =
            LoliApiClient.refreshSession(
                baseUrl = server.url("/").toString(),
                token = "expired-token",
            )

        assertSame(SessionRefreshResult.Unauthorized, result)
    }

    @Test
    fun cookieNamesIgnoreEmptySegmentsAndDuplicateNames() {
        assertEquals(
            linkedSetOf("chii_auth", "theme"),
            WebCookieStore.cookieNames("chii_auth=abc; theme=dark; chii_auth=new; ; invalid"),
        )
    }

    @Test
    fun bangumiLoginPageIsReportedAsAuthenticationRequired() {
        assertTrue(
            BangumiApiClient.isAuthenticationRequiredPage(
                "<script>CHOBITS_UID = 0;</script><a href=\"/login\">Login</a>",
            ),
        )
        assertFalse(
            BangumiApiClient.isAuthenticationRequiredPage(
                "<script>CHOBITS_UID = 42;</script><input name=\"formhash\" value=\"abc\">",
            ),
        )
    }

    private fun jwt(
        expiresAt: Long,
        username: String,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"none\"}".toByteArray())
        val payload =
            encoder.encodeToString(
                "{\"expiresAt\":$expiresAt,\"bgmUsername\":\"$username\"}".toByteArray(),
            )
        return "$header.$payload.signature"
    }
}
