# Document Analysis And Adaptive Recovery

## Purpose

Entio reads business documents and prepares evidence-backed ontology changes for
human review.

The feature has one simple goal:

> Let the model understand the documents, let Kotlin turn supported meaning into
> safe operations, and never apply anything without human approval.

The workflow should preserve useful meaning without asking one model response to
do everything at once.

## The Simple Flow

```text
documents
→ extract text
→ discover meaning in each document
→ connect meaning across documents
→ compare the connected meaning with the ontology
→ produce a semantic plan
→ compile supported meaning in Kotlin
→ human review
→ existing proposal and apply workflow
```

Each step has one responsibility.

### 1. Extract text

Entio extracts text and exact evidence locations from the uploaded documents.
The model does not decide whether evidence locations are valid.

### 2. Discover meaning

The model reads each document and identifies concepts, relationships, facts,
requirements, controls, rules, and document metadata.

Every discovery must point to evidence that Entio can verify.

### 3. Connect the discoveries

The model decides how discoveries relate to one another. For example, it may
recognize that a payment has a source account, requires approval, and produces
an approval record.

This is semantic work. Kotlin does not try to infer these relationships from
words or labels.

### 4. Compare with the ontology

The model and deterministic matching services compare the connected document
meaning with the current ontology. The result may confirm existing meaning,
extend it, identify a conflict, or recommend new meaning.

### 5. Produce a semantic plan

The model describes the concepts and relationships that should be represented.
It does not write RDF and does not choose low-level Entio operations.

### 6. Compile in Kotlin

Kotlin checks the semantic plan and compiles supported meaning into the existing
typed operations.

Kotlin may order explicit dependencies, but it does not invent semantics. If the
model explicitly says that a shape targets a new class, Kotlin can create the
class before creating the shape. If the model never identifies the class,
Kotlin must not guess that it exists.

### 7. Review and apply

The result remains a draft. A person reviews the proposed groups, evidence,
confidence, and warnings before anything can enter the existing proposal and
apply workflow.

## Why Chunking Exists

A document set can contain more connected meaning than one model response can
reliably return.

Chunking divides the discovery inventory into smaller semantic modeling
requests. It does not discard discoveries or shorten evidence merely to fit an
arbitrary character count.

Entio does not impose a fixed input-character limit. Whole extracted blocks and
complete verified stage inputs remain available. Safety comes from existing
document, discovery, evidence, output-token, call-attempt, and wall-time bounds.

Initial chunks are sized according to the amount of structured output they are
likely to require. This is only a practical estimate. The provider may still be
unable to complete a particular chunk.

## Adaptive Recovery

Recovery should follow a small set of rules.

### Output limit or provider HTTP 500

If connected modeling reaches its output limit or the provider returns HTTP 500:

1. keep every chunk that already succeeded;
2. split only the failed chunk into two balanced children;
3. process the children independently;
4. combine the successful child models;
5. verify that every discovery is still represented or explicitly reported.

The failed parent is not sent repeatedly before splitting. Smaller children are
the recovery mechanism.

### Network interruption or timeout

A network interruption or timeout may receive one exact-input retry. If the
retry fails, the task stops safely.

### Authorization, quota, or schema failure

Entio does not split or retry requests rejected because of authorization,
quota, unsupported models, or invalid request schemas. Those failures require a
credential, model, provider, or code correction.

### A single discovery still fails

Entio cannot split a chunk containing one discovery. The task stops and reports
the safe failure instead of deleting or weakening that discovery.

### The approved call budget is exhausted

Entio stops before starting work that cannot finish within the remaining budget.
It does not silently omit later chunks.

## HTTP 500 In Plain Language

An HTTP 500 response means the model provider accepted the request but failed
while processing it.

It does not mean that Kotlin rejected the ontology plan. It also does not, by
itself, prove that the credential, request schema, or document evidence is
wrong.

For connected modeling, the practical response is to split the failed work and
try smaller pieces. Provider request IDs may be retained in local diagnostics
for support, but credentials and Authorization headers must never be logged.

## Call Accounting

Entio tracks two different quantities:

- A **logical call** is a distinct stage or chunk of work.
- A **provider attempt** is one HTTP attempt, including a network retry.

A retry consumes another provider attempt but does not become a new logical
stage. Splitting a failed chunk creates two new logical calls.

Keeping these counts separate prevents a temporary provider error from
incorrectly consuming the entire semantic-work budget.

## Model And Kotlin Responsibilities

| Responsibility | Owner |
| --- | --- |
| Understand document meaning | Model |
| Decide which concepts and relationships belong together | Model |
| Identify ambiguity or unsupported complex meaning | Model |
| Verify evidence IDs and locations | Kotlin |
| Verify references and required dependencies | Kotlin |
| Order explicitly identified prerequisites | Kotlin |
| Compile supported semantic patterns into typed operations | Kotlin |
| Detect duplicates, stale state, collisions, and unsupported mappings | Kotlin |
| Approve or apply a change | Human reviewer through the existing workflow |

The key boundary is simple: the model supplies meaning; Kotlin supplies
repeatable safety.

## Example

Suppose a document says that a payment requires approval and that the approval
is recorded.

The model may return:

- a `Payment` class;
- a `PaymentApprovalRecord` class;
- a `hasApprovalRecord` relationship from payment to approval record; and
- a validation shape targeting `Payment`.

Kotlin can compile these in dependency order:

1. create `Payment`;
2. create `PaymentApprovalRecord`;
3. create `hasApprovalRecord`;
4. assign its domain and range;
5. create the validation shape.

Kotlin knows this order because the semantic plan contains explicit typed
references. Kotlin is not being asked to understand the business meaning of
payments or approval.

## Safety Boundaries That Do Not Change

- Documents and provider output remain untrusted.
- The model has no tools, filesystem access, direct write path, or approval
  authority.
- Kotlin remains authoritative for supported ontology behavior.
- Unsupported complex rules remain review-only.
- No raw RDF fallback is allowed.
- Every executable result must pass deterministic verification.
- Every result remains subject to human review.
- Accepted work uses the existing proposal, approval, atomic apply, reload, and
  rollback workflow.

## Design Principle

When a model response is too large or the provider cannot process it, Entio
should divide the work. It should not truncate meaning, invent missing
semantics, or add more prompt rules in an attempt to make one oversized response
perfect.
