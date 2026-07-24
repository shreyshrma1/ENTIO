# GRC Compliance Document Template

## Document Control

| Field | Value |
|---|---|
| Document Title | {{DOCUMENT_TITLE}} |
| Document ID | {{DOCUMENT_ID}} |
| Version | {{VERSION}} |
| Status | {{Draft / Approved / Superseded}} |
| Owner | {{DOCUMENT_OWNER}} |
| Business Unit | {{BUSINESS_UNIT}} |
| Jurisdiction | {{JURISDICTION}} |
| Effective Date | {{EFFECTIVE_DATE}} |
| Review Date | {{REVIEW_DATE}} |
| Supersedes | {{PREVIOUS_DOCUMENT_ID_OR_NONE}} |
| Classification | {{CLASSIFICATION}} |
| Approval Authority | {{APPROVER_NAME_OR_ROLE}} |

## 1. Executive Summary

{{Write 2–4 paragraphs explaining the purpose of the compliance document, the regulated activity, the main obligations, and the expected business outcome.}}

## 2. Scope

This document applies to:

- Organizations: {{ORGANIZATIONS}}
- Business units: {{BUSINESS_UNITS}}
- Systems: {{SYSTEMS}}
- Data types: {{DATA_TYPES}}
- Processes: {{PROCESSES}}
- Locations or jurisdictions: {{LOCATIONS}}
- Third parties: {{THIRD_PARTY_SCOPE}}

This document does not apply to:

{{NARRATIVE_EXCLUSIONS}}

## 3. Regulatory and Control Context

### 3.1 Applicable Requirements

| Requirement ID | Source | Requirement Name | Description | Mandatory | Effective Date |
|---|---|---|---|---|---|
| {{REQ-001}} | {{REGULATOR_OR_STANDARD}} | {{REQUIREMENT_NAME}} | {{SHORT_DESCRIPTION}} | {{Yes/No}} | {{DATE}} |
| {{REQ-002}} | {{REGULATOR_OR_STANDARD}} | {{REQUIREMENT_NAME}} | {{SHORT_DESCRIPTION}} | {{Yes/No}} | {{DATE}} |

### 3.2 Narrative Context

{{Explain why these requirements matter, how they apply, and any interpretation decisions. Include classes, roles, obligations, relationships, and exceptions in natural language.}}

## 4. Governance Roles and Responsibilities

| Role ID | Role | Responsibilities | Accountable To | Required Competence |
|---|---|---|---|---|
| {{ROLE-001}} | {{ROLE_NAME}} | {{RESPONSIBILITIES}} | {{ACCOUNTABLE_TO}} | {{COMPETENCE}} |
| {{ROLE-002}} | {{ROLE_NAME}} | {{RESPONSIBILITIES}} | {{ACCOUNTABLE_TO}} | {{COMPETENCE}} |

### 4.1 Narrative Responsibilities

{{Describe delegation, escalation, and collaboration. Include sentences such as “The Compliance Officer approves control exceptions.”}}

## 5. Assets, Systems, Data, and Processes

| Asset ID | Name | Type | Owner | Classification | Criticality | Related Process |
|---|---|---|---|---|---|---|
| {{AST-001}} | {{ASSET_NAME}} | {{TYPE}} | {{OWNER_ROLE}} | {{CLASSIFICATION}} | {{HIGH/MEDIUM/LOW}} | {{PROCESS_NAME}} |

{{Describe how assets, data, systems, and processes interact.}}

## 6. Risks

| Risk ID | Risk Statement | Cause | Event | Impact | Likelihood | Severity | Owner | Status |
|---|---|---|---|---|---|---|---|---|
| {{RISK-001}} | {{RISK_STATEMENT}} | {{CAUSE}} | {{EVENT}} | {{IMPACT}} | {{LIKELIHOOD}} | {{SEVERITY}} | {{OWNER}} | {{STATUS}} |

{{Explain how risks arise, how they affect objectives, and how risks may be connected.}}

## 7. Controls

| Control ID | Control Name | Control Type | Objective | Owner | Frequency | Evidence | Related Risks | Related Requirements |
|---|---|---|---|---|---|---|---|---|
| {{CTRL-001}} | {{CONTROL_NAME}} | {{PREVENTIVE / DETECTIVE / CORRECTIVE}} | {{OBJECTIVE}} | {{OWNER_ROLE}} | {{FREQUENCY}} | {{EVIDENCE_TYPE}} | {{RISK_IDS}} | {{REQ_IDS}} |

{{Describe how controls operate, including exceptions, timing, dependencies, and expected evidence.}}

## 8. Compliance Obligations

The organization must:

1. {{OBLIGATION_1}}
2. {{OBLIGATION_2}}

The organization must not:

1. {{PROHIBITION_1}}

The organization may:

1. {{PERMISSION_1}}

## 9. Evidence and Records

| Evidence ID | Evidence Type | Produced By | Supports Control | Retention Period | Storage Location |
|---|---|---|---|---|---|
| {{EVID-001}} | {{EVIDENCE_TYPE}} | {{ROLE_OR_SYSTEM}} | {{CTRL_ID}} | {{RETENTION}} | {{LOCATION}} |

{{Explain how evidence is created, reviewed, approved, stored, and disposed of.}}

## 10. Exceptions and Waivers

| Exception ID | Requested By | Affected Requirement | Affected Control | Justification | Approver | Expiration Date | Status |
|---|---|---|---|---|---|---|---|
| {{EXC-001}} | {{REQUESTER}} | {{REQ_ID}} | {{CTRL_ID}} | {{JUSTIFICATION}} | {{APPROVER}} | {{DATE}} | {{STATUS}} |

{{Describe when exceptions are allowed and what compensating controls are required.}}

## 11. Monitoring, Testing, and Reporting

| Activity ID | Activity | Performed By | Frequency | Input | Output | Escalation Trigger |
|---|---|---|---|---|---|---|
| {{MON-001}} | {{ACTIVITY}} | {{ROLE}} | {{FREQUENCY}} | {{INPUT}} | {{OUTPUT}} | {{TRIGGER}} |

{{Describe control testing and escalation.}}

## 12. Findings and Remediation

| Finding ID | Description | Severity | Related Control | Owner | Due Date | Status |
|---|---|---|---|---|---|---|
| {{FIND-001}} | {{DESCRIPTION}} | {{SEVERITY}} | {{CTRL_ID}} | {{OWNER}} | {{DATE}} | {{STATUS}} |

{{Explain remediation, dependencies, acceptance criteria, and closure.}}

## 13. Key Dates and Thresholds

| Item | Value | Unit | Applies To |
|---|---|---|---|
| {{REVIEW_FREQUENCY}} | {{12}} | {{months}} | {{DOCUMENT_OR_CONTROL}} |
| {{RETENTION_PERIOD}} | {{7}} | {{years}} | {{RECORD_TYPE}} |
| {{ESCALATION_THRESHOLD}} | {{VALUE}} | {{UNIT}} | {{RISK_OR_EVENT}} |

## 14. Conflicts, Ambiguities, and Interpretations

{{Include at least one passage where requirements or interpretations conflict or differ by jurisdiction, date, product, or business unit.}}

## 15. Change History

| Version | Date | Change | Reason | Approved By |
|---|---|---|---|---|
| {{1.0}} | {{DATE}} | {{INITIAL_RELEASE}} | {{REASON}} | {{APPROVER}} |
| {{1.1}} | {{DATE}} | {{REVISION}} | {{REASON}} | {{APPROVER}} |

## 16. Appendices

### Appendix A: Terms and Definitions

| Term | Definition | Source |
|---|---|---|
| {{TERM}} | {{DEFINITION}} | {{DOCUMENT_OR_STANDARD}} |

### Appendix B: Relationship Examples

- {{ROLE}} owns {{CONTROL}}.
- {{CONTROL}} mitigates {{RISK}}.
- {{EVIDENCE}} demonstrates operation of {{CONTROL}}.
- {{REQUIREMENT}} applies to {{PROCESS}}.
- {{FINDING}} affects {{ASSET}}.
- {{DOCUMENT}} supersedes {{DOCUMENT}}.

### Appendix C: Deliberate Test Variations

Include:

- two labels for the same concept;
- one term used with slightly different meanings;
- a later section that changes an earlier definition;
- one explicit conflict;
- one implied relationship;
- one ambiguous statement;
- one FIBO-relevant concept;
- one amendment that supersedes an earlier rule.
