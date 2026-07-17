package com.entio.web.ai

public enum class AiCredentialTestStatus {
    NOT_CONFIGURED,
    NOT_TESTED,
    PASSED,
    FAILED,
}

public data class AiCredentialRequest(
    val providerId: String,
    val apiKey: String,
)

public data class AiCredentialStatusResponse(
    val apiVersion: String = "v1",
    val configured: Boolean,
    val providerId: String?,
    val testStatus: AiCredentialTestStatus,
)

public data class AiCredentialTestResponse(
    val apiVersion: String = "v1",
    val status: AiCredentialTestStatus,
    val message: String,
)

public sealed interface AiProviderTestResult {
    public data class Passed(val message: String) : AiProviderTestResult

    public data class Failed(val message: String) : AiProviderTestResult
}

/** Provider-neutral boundary. Implementations must not return or log the credential. */
public interface AiProviderClient {
    public val providerId: String

    public suspend fun test(apiKey: String): AiProviderTestResult
}

/** Development provider boundary used until a real provider adapter is explicitly approved. */
public class DevelopmentAiProviderClient(
    override val providerId: String = "provider-neutral",
) : AiProviderClient {
    override suspend fun test(apiKey: String): AiProviderTestResult = when {
        apiKey.isBlank() -> AiProviderTestResult.Failed("The credential is empty.")
        apiKey.contains("reject", ignoreCase = true) -> AiProviderTestResult.Failed("The provider rejected the credential.")
        else -> AiProviderTestResult.Passed("The provider credential was accepted by the development boundary.")
    }
}

/** Server-only credential storage contract. Secrets are available only inside the callback. */
public interface AiCredentialStore {
    public fun save(userId: String, providerId: String, apiKey: String)

    public fun remove(userId: String)

    public fun providerFor(userId: String): String?

    public fun <T> withCredential(userId: String, block: (providerId: String, apiKey: String) -> T): T?

    public suspend fun <T> withCredentialSuspending(
        userId: String,
        block: suspend (providerId: String, apiKey: String) -> T,
    ): T?

    public fun clearAll()
}

public class InMemoryAiCredentialStore : AiCredentialStore {
    private data class StoredCredential(val providerId: String, val apiKey: String)

    private val credentials: MutableMap<String, StoredCredential> = linkedMapOf()

    @Synchronized
    override fun save(userId: String, providerId: String, apiKey: String) {
        credentials[userId] = StoredCredential(providerId, apiKey)
    }

    @Synchronized
    override fun remove(userId: String) {
        credentials.remove(userId)
    }

    @Synchronized
    override fun providerFor(userId: String): String? = credentials[userId]?.providerId

    @Synchronized
    override fun <T> withCredential(userId: String, block: (providerId: String, apiKey: String) -> T): T? {
        return credentials[userId]?.let { block(it.providerId, it.apiKey) }
    }

    override suspend fun <T> withCredentialSuspending(
        userId: String,
        block: suspend (providerId: String, apiKey: String) -> T,
    ): T? {
        val credential = synchronized(this) { credentials[userId] }
        return credential?.let { block(it.providerId, it.apiKey) }
    }

    @Synchronized
    override fun clearAll() {
        credentials.clear()
    }
}

public class AiCredentialService(
    private val store: AiCredentialStore,
    private val provider: AiProviderClient,
) {
    private val lastTests: MutableMap<String, AiCredentialTestStatus> = linkedMapOf()

    @Synchronized
    public fun status(userId: String): AiCredentialStatusResponse {
        val providerId = store.providerFor(userId)
        return AiCredentialStatusResponse(
            configured = providerId != null,
            providerId = providerId,
            testStatus = if (providerId == null) AiCredentialTestStatus.NOT_CONFIGURED else lastTests[userId] ?: AiCredentialTestStatus.NOT_TESTED,
        )
    }

    @Synchronized
    public fun save(userId: String, request: AiCredentialRequest): AiCredentialStatusResponse {
        if (request.providerId != provider.providerId) {
            throw AiCredentialFailure("unsupported-provider", "The requested provider is not available in this server boundary.")
        }
        if (request.apiKey.isBlank()) {
            throw AiCredentialFailure("missing-credential", "A non-empty credential is required.")
        }
        store.save(userId, request.providerId, request.apiKey)
        lastTests.remove(userId)
        return status(userId)
    }

    public suspend fun test(userId: String): AiCredentialTestResponse {
        val result = store.withCredentialSuspending(userId) { providerId, apiKey ->
            if (providerId != provider.providerId) {
                AiProviderTestResult.Failed("The configured provider is not available in this server boundary.")
            } else {
                provider.test(apiKey)
            }
        } ?: throw AiCredentialFailure("missing-credential", "Configure a credential before testing it.")
        return when (result) {
            is AiProviderTestResult.Passed -> {
                synchronized(this) { lastTests[userId] = AiCredentialTestStatus.PASSED }
                AiCredentialTestResponse(status = AiCredentialTestStatus.PASSED, message = result.message)
            }
            is AiProviderTestResult.Failed -> {
                synchronized(this) { lastTests[userId] = AiCredentialTestStatus.FAILED }
                AiCredentialTestResponse(status = AiCredentialTestStatus.FAILED, message = result.message)
            }
        }
    }

    @Synchronized
    public fun remove(userId: String): AiCredentialStatusResponse {
        store.remove(userId)
        lastTests.remove(userId)
        return status(userId)
    }

    @Synchronized
    public fun logout(userId: String) {
        store.remove(userId)
        lastTests.remove(userId)
    }

    @Synchronized
    public fun clearAll() {
        store.clearAll()
        lastTests.clear()
    }

    public fun <T> withCredential(userId: String, block: (providerId: String, apiKey: String) -> T): T? =
        store.withCredential(userId, block)

    public suspend fun <T> withCredentialSuspending(
        userId: String,
        block: suspend (providerId: String, apiKey: String) -> T,
    ): T? = store.withCredentialSuspending(userId, block)
}

public class AiCredentialFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)
