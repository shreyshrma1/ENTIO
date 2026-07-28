# Phase 11.5 Slice 10: Grouped Review UI

Status: Complete

Date: 2026-07-27

## Decision

The document workspace presents each Phase 11.5 recommendation as one connected
review unit. The server supplies its status, exact ordered operations, dependencies,
provenance, confidence dimensions, critic dispositions, review-only findings,
optional leaves, and individual confirmation gates. The browser displays those
decisions but does not infer ontology meaning or operation safety.

The review surface supports acceptance, rejection, clarification, reconsideration,
safe split requests, server-validated optional-leaf exclusion, and explicit
individual confirmation. Review-only findings are clearly separated from changes
that can enter a proposal.

## Experience

- Task status includes the bounded multi-stage analysis timeline, safe duration,
  retry, and failure information.
- Exact changes are shown in dependency order with their target ontology source.
- Evidence opens as bounded extracted text with its document location.
- Advanced model and source details remain behind disclosure.
- Cards and controls reflow inside the document pane when the assistant is open.
- Buttons, dialogs, status messages, and review sections use accessible names and
  remain keyboard reachable.

## Verification

Server route and serialization tests, React interaction and accessibility tests,
and the production web build pass. Tests cover connected dependencies, confidence,
critique, review-only findings, individual confirmation, optional-leaf exclusion,
safe evidence rendering, responsive containment, and legacy review behavior.
