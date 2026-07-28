# Phase 11.5 Slice 11: Verification

Status: Complete

Date: 2026-07-27

## Decision

Phase 11.5 is covered by a permanent deterministic benchmark that references the
two existing example PDFs in place. The benchmark manifest records accepted
semantic aliases, required evidence terms and illustrative individuals, prohibited
executable modeling patterns, and meanings that must remain review-only.

The complete test suite verifies the multi-stage contracts and the path from
document upload through connected review and the existing private-draft and
proposal workflow. No deterministic test requires a provider credential.

## Accuracy And Throughput

The benchmark requires evidence for:

- `Payment` and accepted payment aliases;
- `PaymentApprovalRecord` and accepted approval-record aliases;
- the USD 25,000 separate-approval rule;
- consumer-loan servicing controls;
- payment-to-loan validation and suspense investigation;
- the approved illustrative people, organizations, accounts, and invoice.

It protects against metadata-derived `Compliance Status`, an invented
`Customer`-to-`Loan` servicing relationship, payment rules attached to `Account`,
an `Account`-to-`Invoice` approval relationship, and promotion of illustrative
facts into unsupported executable changes. Linked-payment aggregation, separation
of duties, temporal sequencing, and conditional applicability remain review-only.

The deterministic logical-call budget is 6 calls for one document, 7 for two
documents, and 15 for ten documents. All remain within the approved task limit.
Tests also cover retry reserves, cancellation, timeout, malformed output,
unverifiable evidence, unresolved references, stale graphs, and task limits.

## Security And Compatibility

The full suite covers project and user isolation, prompt injection as untrusted
document data, response-schema rejection, safe error redaction, no ontology-source
write before approval, legacy task readability, and regression behavior across
the assistant, shared staging, reasoning, SHACL, ontology map, CLI, VS Code,
proposal apply, reload, and rollback paths.

The change-set verifier now uses Entio's actual approved SHACL constraint set:
minimum and maximum counts, datatype, class, minimum and maximum inclusive values,
and pattern.

## Verification

The following commands passed:

```text
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

The web dependency audit reported no production vulnerabilities. The controlled
real-provider smoke test was not run because the current verification process did
not have access to a verified server-memory credential. No credential, complete
prompt, raw document payload, or provider response was recorded.
