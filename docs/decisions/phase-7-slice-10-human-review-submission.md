# Phase 7 Slice 10 Human Review Submission

## ExecPlan Slice

Slice 10: Human Review Submission, AI Attribution, And Audit.

## Goal

Move one current, fully analyzed private AI draft into Entio's existing human proposal-review workflow without granting the model review, approval, application, or rollback authority.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- `web-server/src/main/kotlin/com/entio/web/CollaborationHub.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTypedEditCapabilities.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiReviewSubmissionService.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiReviewSubmissionServiceTest.kt`
- `docs/decisions/phase-7-slice-10-human-review-submission.md`

## Implementation Result

- Added an explicit human-triggered submission service for fully analyzed private AI drafts.
- Revalidated user permission, project and conversation scope, source scope, project baseline, draft revision and fingerprint, deterministic analysis fingerprints, run state, and per-item AI attribution before submission.
- Added a synchronized private-draft transition that imports one exact revision and locks it as submitted only after the shared review workflow accepts it.
- Added an atomic shared-staging import that prepares every typed edit and a valid ordinary proposal before mutating shared state.
- Rejected submissions when the shared review queue already contains incompatible staged work instead of silently merging proposals.
- Preserved AI markers, submitting user, conversation and run references, rationale, semantic diff, deterministic analysis references, and item attribution in the submission result.
- Added an in-memory submission audit and a safe collaboration event containing proposal metadata without private conversation messages, provider text, or draft contents.
- Kept submission separate from approval and application; no ontology source is written during this transition.

## Tests Added Or Updated

- Verified submission requires an explicit user action.
- Verified a current valid draft creates exactly one ordinary review proposal and does not mutate the Turtle source.
- Verified the submitted draft is locked against further mutation.
- Verified missing permission and incomplete drafts do not enter shared staging.
- Verified stale draft, preview, and analysis fingerprints fail without partial import.
- Verified blocked drafts and shared-staging conflicts leave existing shared work unchanged.
- Verified attribution, rationale, semantic analysis references, and review state survive in the submission result.
- Verified collaborators receive only safe submitted-proposal metadata and no private draft or conversation content.
- Re-ran the complete server suite to preserve existing human review, application, and rollback behavior.

## Verification

- `./gradlew :web-server:test` - passed.
- `./gradlew test` - passed.
- `./gradlew build` - passed.
- `git diff --check` - passed.

## Git Commit

A focused Slice 10 commit was created on `feature/phase-7-slice-10-human-review-submission`.

## Assumptions And Limitations

- Shared staging remains an in-memory single-proposal queue. A private AI draft receives a structured conflict when unrelated shared work is already present.
- Submission audit records remain in server memory and follow the Phase 7 persistence boundary.
- HTTP routes and review-screen DTO exposure are deferred to Slice 11; this slice provides the server-owned service and structured result.
- Collaboration publication occurs only after the authoritative review proposal exists and contains safe proposal metadata rather than private AI content.

## Notable Decisions

- The model capability registry does not expose submission, approval, rejection, application, or rollback tools.
- Atomicity is enforced by holding the private-draft mutation lock while the synchronized shared-staging transaction prepares and accepts the complete draft.
- Existing human proposal review remains authoritative; AI attribution is additional provenance, not approval authority.
