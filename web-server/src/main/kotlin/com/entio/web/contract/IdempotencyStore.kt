package com.entio.web.contract

public sealed interface IdempotencyDecision {
    public data class Accepted(val key: String) : IdempotencyDecision
    public data class Replay(val key: String) : IdempotencyDecision
    public data class Conflict(val key: String) : IdempotencyDecision
}

public class InMemoryIdempotencyStore {
    private val fingerprints: MutableMap<String, String> = linkedMapOf()

    @Synchronized
    public fun begin(key: String, payloadFingerprint: String): IdempotencyDecision {
        require(key.isNotBlank()) { "idempotency-key-required" }
        require(payloadFingerprint.isNotBlank()) { "payload-fingerprint-required" }

        val existingFingerprint = fingerprints[key]
        return when {
            existingFingerprint == null -> {
                fingerprints[key] = payloadFingerprint
                IdempotencyDecision.Accepted(key)
            }
            existingFingerprint == payloadFingerprint -> IdempotencyDecision.Replay(key)
            else -> IdempotencyDecision.Conflict(key)
        }
    }
}
