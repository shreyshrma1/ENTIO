package com.entio.web

import com.entio.web.ai.InMemoryAiCredentialStore
import com.entio.web.ai.DevelopmentAiProviderClient
import com.entio.web.contract.WebApplicationDependencies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class AiCredentialTest {
    @Test
    fun credentialLifecycleIsServerOnlyAndExplicitlyTested(): Unit = testApplication {
        application { module(developmentDependencies()) }

        val initial = client.get("/api/v1/ai/credential-status")
        assertEquals(HttpStatusCode.OK, initial.status)
        assertContains(initial.bodyAsText(), "NOT_CONFIGURED")

        val secret = "development-secret-123"
        val saved = client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"provider-neutral","apiKey":"$secret"}""")
        }
        assertEquals(HttpStatusCode.OK, saved.status)
        assertContains(saved.bodyAsText(), "NOT_TESTED")
        assertFalse(saved.bodyAsText().contains(secret))

        val tested = client.post("/api/v1/ai/credentials/test")
        assertEquals(HttpStatusCode.OK, tested.status)
        assertContains(tested.bodyAsText(), "PASSED")
        assertFalse(tested.bodyAsText().contains(secret))

        val otherUser = client.get("/api/v1/ai/credential-status") {
            headers.append("X-Entio-User", "bob")
        }
        assertContains(otherUser.bodyAsText(), "NOT_CONFIGURED")

        val removed = client.delete("/api/v1/ai/credentials")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertContains(removed.bodyAsText(), "NOT_CONFIGURED")
    }

    @Test
    fun providerFailuresAreRedactedAndLogoutRemovesTheCredential(): Unit = testApplication {
        application { module(developmentDependencies()) }

        val rejectedSecret = "reject-this-secret"
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"provider-neutral","apiKey":"$rejectedSecret"}""")
        }
        val tested = client.post("/api/v1/ai/credentials/test")
        assertEquals(HttpStatusCode.OK, tested.status)
        assertContains(tested.bodyAsText(), "FAILED")
        assertContains(tested.bodyAsText(), "provider rejected")
        assertFalse(tested.bodyAsText().contains(rejectedSecret))

        val logout = client.post("/api/v1/session/logout")
        assertEquals(HttpStatusCode.OK, logout.status)
        assertContains(logout.bodyAsText(), "LOGGED_OUT")
        assertContains(client.get("/api/v1/ai/credential-status").bodyAsText(), "NOT_CONFIGURED")
    }

    @Test
    fun clearingTheInMemoryStoreModelsServerRestartDestruction(): Unit {
        val store = InMemoryAiCredentialStore()
        store.save("alice", "provider-neutral", "secret")
        assertEquals("provider-neutral", store.providerFor("alice"))
        store.clearAll()
        assertEquals(null, store.providerFor("alice"))
    }

    private fun developmentDependencies(): WebApplicationDependencies = WebApplicationDependencies(
        aiProvider = DevelopmentAiProviderClient(),
    )
}
