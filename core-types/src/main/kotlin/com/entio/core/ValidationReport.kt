package com.entio.core

public data class ValidationReport(
    public val status: ValidationStatus,
    public val issues: List<ValidationIssue>,
) {
    public val ok: Boolean
        get() = status == ValidationStatus.Valid
}

public data class ValidationIssue(
    public val severity: ValidationSeverity,
    public val code: String,
    public val message: String,
    public val source: String? = null,
)

public enum class ValidationSeverity {
    Error,
    Warning,
    Info,
}

public enum class ValidationStatus {
    Valid,
    Invalid,
}
