package com.entio.web.ai.models

import com.entio.web.ai.AiAuditRecord
import com.entio.web.ai.AiResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiModelSecurityRegressionTest {
    @Test
    fun malformedAliasesUnsupportedInventoryAndSecretsNeverReachSettingsProjection(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        val secret = "security-regression-secret"
        fixture.provider.inventory(
            secret,
            "gpt-5.6-sol",
            "gpt-latest",
            "text-embedding-3-large",
            "https://attacker.example/v1/responses",
            "model\nheader-injection",
            "",
        )

        val settings = fixture.credentialSettings.saveCredential("alice", "openai", secret)

        assertEquals(listOf("gpt-5.6-sol"), settings.candidates.map(AiSelectableModelDescriptor::modelId))
        assertFalse(settings.toString().contains(secret))
        assertEquals(5, settings.unsupportedProviderModelCount)
    }

    @Test
    fun responseRunAndAuditContractsContainNoSecretOrAuthorizationPayloadFields() {
        val fields = listOf(AiResponse::class.java, AiUserProviderSettings::class.java, AiAuditRecord::class.java)
            .flatMap { type -> type.declaredFields.map { it.name.lowercase() } }
        assertFalse(fields.any { it.contains("apikey") || it.contains("authorization") || it == "secret" })
        assertTrue(fields.none { it in setOf("providerbody", "rawproviderpayload", "responseheaders") })
    }
}
