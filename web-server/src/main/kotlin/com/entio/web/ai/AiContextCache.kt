package com.entio.web.ai

public data class AiContextCacheKey(
    val ownerId: String?,
    val projectId: String,
    val projectFingerprint: String,
    val retrievalPolicyVersion: String = AI_RETRIEVAL_POLICY_VERSION,
    val reasoningFingerprint: String? = null,
    val shaclFingerprint: String? = null,
    val draftFingerprint: String? = null,
    val resource: String,
)

public class AiContextCache {
    private val values: MutableMap<AiContextCacheKey, Any> = linkedMapOf()

    @Synchronized
    public fun <T : Any> getOrPut(key: AiContextCacheKey, producer: () -> T): T {
        require(key.projectFingerprint.isNotBlank())
        require(key.resource.isNotBlank())
        @Suppress("UNCHECKED_CAST")
        return values.getOrPut(key, producer) as T
    }

    @Synchronized
    public fun invalidateProject(projectId: String): Int {
        val keys = values.keys.filter { it.projectId == projectId }
        keys.forEach(values::remove)
        return keys.size
    }

    @Synchronized
    public fun size(): Int = values.size
}
