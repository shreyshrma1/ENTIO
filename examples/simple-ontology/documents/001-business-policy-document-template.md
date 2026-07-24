# Business Policy Document Template

## Document Control

| Field | Value |
|---|---|
| Policy Title | {{POLICY_TITLE}} |
| Policy ID | {{POLICY_ID}} |
| Version | {{VERSION}} |
| Status | {{Draft / Approved / Superseded}} |
| Policy Owner | {{POLICY_OWNER}} |
| Approved By | {{APPROVER}} |
| Effective Date | {{EFFECTIVE_DATE}} |
| Review Date | {{REVIEW_DATE}} |
| Supersedes | {{PREVIOUS_POLICY_OR_NONE}} |
| Applies To | {{BUSINESS_UNIT_OR_ORGANIZATION}} |
| Classification | {{CLASSIFICATION}} |

## 1. Purpose

{{Explain why this policy exists, the problem it addresses, and the intended outcome.}}

## 2. Scope

This policy applies to:

- people or roles: {{ROLES}}
- departments: {{DEPARTMENTS}}
- systems: {{SYSTEMS}}
- products or services: {{PRODUCTS}}
- data: {{DATA_TYPES}}
- locations: {{LOCATIONS}}
- third parties: {{THIRD_PARTIES}}

This policy excludes:

{{NARRATIVE_EXCLUSIONS}}

## 3. Policy Statement

{{Write 3–6 natural-language paragraphs containing obligations, permissions, prohibitions, responsibilities, exceptions, and dependencies.}}

## 4. Key Terms

| Term | Definition | Alternate Label | Related Term |
|---|---|---|---|
| {{TERM_1}} | {{DEFINITION}} | {{ALT_LABEL}} | {{RELATED_TERM}} |
| {{TERM_2}} | {{DEFINITION}} | {{ALT_LABEL}} | {{RELATED_TERM}} |

{{Use these terms naturally and include one ambiguity or later change in meaning.}}

## 5. Roles and Responsibilities

| Role | Responsibility | Decision Authority | Escalates To |
|---|---|---|---|
| {{ROLE_1}} | {{RESPONSIBILITY}} | {{AUTHORITY}} | {{ESCALATION_ROLE}} |
| {{ROLE_2}} | {{RESPONSIBILITY}} | {{AUTHORITY}} | {{ESCALATION_ROLE}} |

{{Describe collaboration and handoffs between roles.}}

## 6. Policy Rules

### 6.1 Required Actions

| Rule ID | Actor | Required Action | Object | Condition | Deadline |
|---|---|---|---|---|---|
| {{RULE-001}} | {{ROLE}} | {{ACTION}} | {{OBJECT}} | {{CONDITION}} | {{DEADLINE}} |

### 6.2 Prohibited Actions

| Rule ID | Actor | Prohibited Action | Object | Exception |
|---|---|---|---|---|
| {{RULE-002}} | {{ROLE}} | {{ACTION}} | {{OBJECT}} | {{EXCEPTION_OR_NONE}} |

### 6.3 Permitted Actions

| Rule ID | Actor | Permitted Action | Object | Condition |
|---|---|---|---|---|
| {{RULE-003}} | {{ROLE}} | {{ACTION}} | {{OBJECT}} | {{CONDITION}} |

### 6.4 Narrative Rules

{{Expand selected rules in prose and include facts or relationships not stated in tables.}}

## 7. Business Objects and Records

| Object ID | Object Type | Name | Owner | Status | Related Object |
|---|---|---|---|---|---|
| {{OBJ-001}} | {{OBJECT_TYPE}} | {{NAME}} | {{OWNER}} | {{STATUS}} | {{RELATED_OBJECT}} |

{{Describe the objects and their relationships.}}

## 8. Procedures

### Procedure: {{PROCEDURE_NAME}}

1. {{ROLE}} receives {{INPUT}}.
2. {{ROLE}} verifies {{CONDITION}}.
3. {{SYSTEM_OR_ROLE}} creates {{RECORD}}.
4. {{ROLE}} approves or rejects {{OBJECT}}.
5. {{SYSTEM}} stores {{OUTPUT}}.
6. {{ROLE}} escalates {{EXCEPTION}} to {{ESCALATION_ROLE}}.

{{Describe variations, edge cases, and dependencies.}}

## 9. Exceptions

| Exception ID | Condition | Requested By | Approved By | Compensating Action | Expiration |
|---|---|---|---|---|---|
| {{EXC-001}} | {{CONDITION}} | {{REQUESTER}} | {{APPROVER}} | {{ACTION}} | {{DATE}} |

{{Explain when exceptions are appropriate and prohibited.}}

## 10. Monitoring and Enforcement

| Monitoring Activity | Performed By | Frequency | Evidence | Failure Response |
|---|---|---|---|---|
| {{ACTIVITY}} | {{ROLE}} | {{FREQUENCY}} | {{EVIDENCE}} | {{RESPONSE}} |

{{Explain how compliance is checked and breaches are handled.}}

## 11. Records and Retention

| Record Type | Created By | Stored In | Retention Period | Disposal Method |
|---|---|---|---|---|
| {{RECORD_TYPE}} | {{ROLE_OR_SYSTEM}} | {{LOCATION}} | {{PERIOD}} | {{METHOD}} |

## 12. Examples

### Example 1: Compliant Scenario

{{Write a short story involving named individuals, organizations, systems, dates, amounts, and relationships that comply with the policy.}}

### Example 2: Non-Compliant Scenario

{{Write a short story where one or more rules are violated.}}

### Example 3: Ambiguous Scenario

{{Write a scenario where the policy supports more than one interpretation.}}

## 13. Change and Evolution Section

### Current Meaning

{{Describe the current definition or rule.}}

### New Information

{{Introduce a later business fact, amendment, or operational practice that changes the meaning slightly.}}

### Recommended Interpretation

{{State whether the concept should be confirmed, extended, revised, split, merged, treated as conflicting, or superseded.}}

## 14. Related Documents

| Document ID | Title | Relationship | Effective Date |
|---|---|---|---|
| {{DOC-001}} | {{TITLE}} | {{Supports / Amends / Supersedes / Conflicts With}} | {{DATE}} |

## 15. Change History

| Version | Date | Summary of Change | Reason | Approved By |
|---|---|---|---|---|
| {{1.0}} | {{DATE}} | {{INITIAL_RELEASE}} | {{REASON}} | {{APPROVER}} |
| {{1.1}} | {{DATE}} | {{CHANGE}} | {{REASON}} | {{APPROVER}} |

## 16. Deliberate Test Content

Include a mixture of:

- structured tables;
- narrative paragraphs;
- explicit entity names;
- implied relationships;
- dates, amounts, identifiers, and statuses;
- alternate labels;
- a definition revised later in the document;
- one conflict with another policy;
- one amendment;
- one individual fact that should become an ontology assertion;
- one concept that should reuse an existing ontology entity;
- one concept that may need to be created locally.
