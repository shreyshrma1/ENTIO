package com.entio.core

public sealed interface EntioResult<out T> {
    public data class Success<out T>(
        public val value: T,
    ) : EntioResult<T>

    public data class Failure(
        public val message: String,
        public val issues: List<ValidationIssue> = emptyList(),
        public val cause: Throwable? = null,
    ) : EntioResult<Nothing>
}
