# Phase 13 Locked Benchmark V1 First-Run Record

Status: Recorded failure with one approved conformance correction

Date: 2026-08-08

Benchmark: `docs/decisions/phase-13-retrieval-benchmark-v1.json`

Benchmark SHA-256: `d47230480bb458b1d65e0fdd4326d82f7b880f0497493f1f512386e32f25dab1`

## Purpose

This record preserves the first execution of the untouched Phase 13 locked
acceptance set against the completed Slice 4 hybrid recommendation service.
The locked judgments were not inspected to tune ranking weights or confidence
thresholds before this run.

## First-run result

The run stopped Slice 4 because recall@10 missed its approved gate.

| Metric | First run | Gate | Result |
| --- | ---: | ---: | --- |
| Recall@10 | `0.8333333333` | at least `0.85` | Fail |
| Precision@3 | `0.75` | at least `0.70` | Pass |
| Requested-kind correctness | `1.00` | `1.00` | Pass |
| Hard-negative action suppression | `1.00` | `1.00` | Pass |
| No-match correctness | `1.00` | at least `0.80` | Pass |
| Repeated frozen-input ordering | identical | identical | Pass |

The missed relevant entities were `Lender` for `locked-03` and `Bond` for
`locked-05`. All other relevant locked entities appeared within the first ten
results.

## Observed conformance defect

The implementation normalized descriptor token coverage by query-token count.
For a one-token query, every descriptor containing that token therefore
received full lexical relevance. That erased the intended distinction between
the exact preferred label `Bond` and longer specialized labels containing the
word “bond,” allowing specialized bond classes to push the base `Bond` class
below rank ten.

## Approved correction and constraints

The repository owner approved one correction: for a one-token query, only an
exact preferred label, exact alternate label, or exact acronym may produce full
lexical relevance. Non-exact descriptor token coverage must remain below full
relevance.

The correction may not change ranking weights, confidence thresholds,
candidate limits, benchmark cases, relevant IRIs, hard negatives, or metric
definitions. Development and regression sets must run before one unchanged
locked-set rerun. A second locked failure restores the Slice 4 stop condition.

The rerun outcome belongs in the Slice 4 completion record; this file remains
the immutable record of the first failed run.
