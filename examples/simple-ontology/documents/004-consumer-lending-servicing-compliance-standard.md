# Consumer Lending Servicing Compliance Standard

## Document Control

| Field | Value |
|---|---|
| Document Title | Consumer Lending Servicing Compliance Standard |
| Document ID | MCB-GRC-307 |
| Version | 1.3 |
| Status | Approved |
| Owner | Consumer Compliance Office |
| Business Unit | Consumer Lending |
| Jurisdiction | United States |
| Effective Date | 2026-09-01 |
| Review Date | 2027-03-01 |
| Supersedes | MCB-GRC-307, version 1.2 |
| Classification | Internal |
| Approval Authority | Chief Compliance Officer |

## 1. Executive Summary

This standard governs Meridian Community Bank’s compliance controls for consumer-loan servicing. It covers payment posting, periodic account information, borrower requests, military-service protections, complaint handling, application-record retention, and evidence that the controls operated as expected.

Consumer Lending owns day-to-day servicing, while the Consumer Compliance Office interprets regulatory requirements and approves exceptions. The expected outcome is that each borrower receives accurate treatment under the applicable agreement and law, each payment is associated with the correct loan, and Meridian can demonstrate the decisions it made.

The standard uses regulatory minimums as a floor. A longer internal retention period or a more protective customer treatment may apply when approved by Legal and Compliance. Operational convenience does not justify reducing a regulatory or contractual protection.

## 2. Scope

This document applies to:

- Organizations: Meridian Community Bank and contracted consumer-loan servicing vendors;
- Business units: Consumer Lending, Loan Operations, Customer Care, Legal, Consumer Compliance, and Internal Audit;
- Systems: Meridian Loan Servicing Platform, Meridian Account Registry, ComplianceCase, and the enterprise records vault;
- Data types: loan agreements, borrower records, payment records, account details, servicing notices, military-service documents, complaints, and control evidence;
- Processes: loan boarding, payment posting, statement generation, rate adjustment, complaint investigation, exception approval, and record retention;
- Locations or jurisdictions: United States operations;
- Third parties: approved statement printers, payment processors, and servicing vendors.

This document does not apply to commercial credit, credit cards, residential mortgage servicing, or loans serviced entirely by another institution under a contract assigning compliance responsibility to that institution.

## 3. Regulatory and Control Context

### 3.1 Applicable Requirements

| Requirement ID | Source | Requirement Name | Description | Mandatory | Effective Date |
|---|---|---|---|---|---|
| REQ-ECOA-RET | Equal Credit Opportunity Act, Regulation B, 12 CFR 1002.12 | Application record retention | Retain covered consumer application and action records for at least 25 months after notice of action or incompleteness. | Yes | Current |
| REQ-TILA-STMT | Truth in Lending Act, Regulation Z, 12 CFR Part 1026 | Consumer credit account information | Provide applicable consumer-credit disclosures and periodic account information clearly, accurately, and on time. | Yes | Current |
| REQ-SCRA-RATE | Servicemembers Civil Relief Act, 50 USC 3937 | Pre-service obligation interest protection | Apply the statutory interest-rate protection to eligible pre-service obligations after receipt and validation of the required notice or military-service evidence. | Yes | Current |
| REQ-MCB-COMP | Meridian Customer Complaint Policy MCB-CMP-110 | Complaint investigation | Record, investigate, respond to, and trend covered servicing complaints. | Yes | 2026-01-01 |

### 3.2 Narrative Context

Regulation B establishes record-retention duties for covered credit applications. Meridian’s seven-year lending-record schedule is longer than the general 25-month consumer application minimum and therefore governs ordinary disposal unless Legal approves a documented hold or another rule requires longer retention.

Regulation Z governs disclosures and account information for covered consumer credit. The exact statement obligation depends on the product and transaction. This standard does not assume that one statement format applies to every loan; Product Compliance must map each product to its approved disclosure and servicing schedule.

The SCRA can cap interest on qualifying pre-service obligations at six percent during military service when statutory conditions are met. Meridian’s Military Benefits Team validates the request and applicable service period before Loan Operations changes the rate. A customer-service employee may explain the process but may not decide eligibility alone.

## 4. Governance Roles and Responsibilities

| Role ID | Role | Responsibilities | Accountable To | Required Competence |
|---|---|---|---|---|
| ROLE-CCO | Consumer Compliance Officer | Owns interpretations, approves control exceptions, and reports material issues. | Chief Compliance Officer | Consumer financial regulation and control governance |
| ROLE-LOM | Loan Operations Manager | Operates servicing controls and corrects payment or statement errors. | Head of Consumer Lending | Loan servicing and operational risk |
| ROLE-MBT | Military Benefits Specialist | Validates military-service evidence and applies approved protections. | Consumer Compliance Officer | SCRA procedures and evidence review |
| ROLE-CTA | Compliance Testing Analyst | Independently tests controls and validates remediation. | Director of Compliance Testing | Testing methodology and consumer regulations |

### 4.1 Narrative Responsibilities

The Consumer Compliance Officer approves control exceptions. The Loan Operations Manager may correct an obvious posting error but must refer any disputed legal interpretation to Compliance. Military Benefits Specialists coordinate with Loan Operations so that an approved rate change is applied to the correct loan and effective period.

Compliance Testing remains independent of the teams that operate the controls. Material findings are reported to the Consumer Compliance Committee, and overdue high-severity remediation is escalated to the Enterprise Risk Committee.

## 5. Assets, Systems, Data, and Processes

| Asset ID | Name | Type | Owner | Classification | Criticality | Related Process |
|---|---|---|---|---|---|---|
| AST-LSP-01 | Meridian Loan Servicing Platform | Business application | Loan Operations Manager | Confidential | High | Payment posting and loan maintenance |
| AST-REG-02 | Meridian Account Registry | Customer and account record | Director of Deposit Operations | Confidential | High | Payment account verification |
| AST-CASE-03 | ComplianceCase | Case-management system | Consumer Compliance Officer | Restricted | High | Complaints, benefits reviews, and exceptions |
| AST-VAULT-04 | Enterprise records vault | Records repository | Records Management | Confidential | Medium | Evidence retention |

The servicing platform records loan balances, rates, due dates, and payment activity. The account registry identifies the customer account used for authorized transfers. ComplianceCase links requests, complaints, decisions, and supporting evidence. The records vault preserves final notices, test samples, approvals, and remediation evidence.

## 6. Risks

| Risk ID | Risk Statement | Cause | Event | Impact | Likelihood | Severity | Owner | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-POST-01 | A valid customer payment may be associated with the wrong loan. | Incomplete reference or manual selection error | Misapplied payment | Incorrect balance, fees, and customer harm | Medium | High | Loan Operations Manager | Open |
| RISK-RATE-02 | An eligible military-service protection may be delayed or calculated incorrectly. | Evidence handoff or rate-rule error | Incorrect interest treatment | Customer harm and legal exposure | Low | High | Consumer Compliance Officer | Mitigated |
| RISK-REC-03 | Meridian may be unable to demonstrate a servicing decision. | Missing or prematurely disposed evidence | Unsupported decision | Failed examination or unresolved complaint | Medium | Medium | Records Management Director | Mitigated |
| RISK-STMT-04 | A borrower may receive inaccurate periodic account information. | Data mapping or timing failure | Incorrect statement | Confusion, complaint, or regulatory breach | Medium | High | Loan Operations Manager | Open |

Payment-posting and statement risks are connected: a misapplied payment can produce both an incorrect loan balance and an inaccurate subsequent statement.

## 7. Controls

| Control ID | Control Name | Control Type | Objective | Owner | Frequency | Evidence | Related Risks | Related Requirements |
|---|---|---|---|---|---|---|---|---|
| CTRL-PAY-01 | Payment-to-loan validation | Preventive | Associate each payment with the intended loan before posting. | Loan Operations Manager | Each payment | Validation result and posting record | RISK-POST-01, RISK-STMT-04 | REQ-TILA-STMT |
| CTRL-STMT-02 | Statement data reconciliation | Detective | Detect balance, rate, due-date, and payment discrepancies before statement release. | Servicing Control Team | Each statement cycle | Reconciliation report and exception queue | RISK-STMT-04 | REQ-TILA-STMT |
| CTRL-SCRA-03 | Military benefit dual review | Preventive | Validate eligibility, effective period, and rate calculation before applying a protection. | Military Benefits Team Lead | Each request | Evidence checklist and approval record | RISK-RATE-02 | REQ-SCRA-RATE |
| CTRL-RET-04 | Lending record retention hold | Preventive | Prevent disposal before the approved retention period ends. | Records Management Director | Daily automated enforcement; annual review | Retention report and deletion log | RISK-REC-03 | REQ-ECOA-RET |
| CTRL-CMP-05 | Servicing complaint trend review | Detective | Identify repeated servicing defects and control failures. | Consumer Compliance Officer | Monthly | Complaint trend report | All listed risks | REQ-MCB-COMP |

CTRL-PAY-01 requires both a valid loan reference and a customer or approved-source match. If either validation fails, the payment enters suspense rather than posting automatically. CTRL-STMT-02 depends on completed payment processing for the statement period; unresolved exceptions must be assessed before release.

## 8. Compliance Obligations

The organization must:

1. post each accepted payment to the intended loan using the approved effective-date rules;
2. provide accurate account and payment information for each covered consumer-credit product;
3. investigate credible borrower disputes and preserve the records used to resolve them;
4. apply approved military-service protections to eligible obligations for the validated period;
5. retain covered lending and compliance evidence for the applicable legal and internal period.

The organization must not:

1. dispose of a lending record subject to a regulatory retention period, active complaint, investigation, litigation hold, or approved extended retention;
2. reject a military-benefit request solely because the customer used Meridian’s secure message center rather than postal mail;
3. change a loan rate or balance without a recorded reason and authorized decision.

The organization may:

1. place an unmatched payment in a suspense account while researching the intended loan;
2. correct a confirmed servicing error and issue revised customer information without waiting for the next ordinary statement cycle;
3. use a longer internal retention period than a regulatory minimum.

## 9. Evidence and Records

| Evidence ID | Evidence Type | Produced By | Supports Control | Retention Period | Storage Location |
|---|---|---|---|---|---|
| EVID-PAY-01 | Payment validation and posting record | Meridian Loan Servicing Platform | CTRL-PAY-01 | Seven years after loan closure | Enterprise records vault |
| EVID-STMT-02 | Statement-cycle reconciliation report | Servicing Control Team | CTRL-STMT-02 | Seven years after report date | Enterprise records vault |
| EVID-SCRA-03 | Military benefit review package | Military Benefits Specialist | CTRL-SCRA-03 | Seven years after benefit end | ComplianceCase |
| EVID-RET-04 | Retention enforcement and deletion report | Records Management | CTRL-RET-04 | Seven years after report date | Governance records library |
| EVID-CMP-05 | Monthly complaint trend report | Consumer Compliance Office | CTRL-CMP-05 | Seven years after report date | ComplianceCase |

Control evidence must identify the relevant record, operator, reviewer when required, result, and timestamp. The control owner reviews exceptions, while Compliance reviews material or repeated failures. Records Management applies approved retention schedules and suspends disposal when a legal or compliance hold is active.

## 10. Exceptions and Waivers

| Exception ID | Requested By | Affected Requirement | Affected Control | Justification | Approver | Expiration Date | Status |
|---|---|---|---|---|---|---|---|
| EXC-307-011 | Loan Operations Manager | REQ-TILA-STMT | CTRL-STMT-02 | Legacy portfolio feed cannot complete pre-release reconciliation; daily post-release comparison and five-day correction apply. | Consumer Compliance Officer | 2026-12-31 | Approved |

Exceptions are allowed only for an internal control design or timing requirement; they cannot waive a customer’s legal protection. The request must identify affected products, duration, risk, compensating controls, and a remediation owner. High-risk exceptions require Legal review and Chief Compliance Officer approval.

## 11. Monitoring, Testing, and Reporting

| Activity ID | Activity | Performed By | Frequency | Input | Output | Escalation Trigger |
|---|---|---|---|---|---|---|
| MON-PAY-01 | Sample payment-posting accuracy | Compliance Testing Analyst | Quarterly | Payment and loan records | Control test report | Any customer-impacting error or error rate above 1% |
| MON-SCRA-02 | Military benefit timeliness and calculation review | Compliance Testing Analyst | Quarterly | Benefit review packages and loan history | Control test report | Any missed eligible period or rate above approved cap |
| MON-STMT-03 | Statement exception trend review | Servicing Control Team | Monthly | Reconciliation exceptions | Trend report | Three similar defects in one cycle |
| MON-RET-04 | Retention schedule certification | Records Management | Annually | Repository schedules and deletion logs | Certification | Early deletion or missing legal hold |

Compliance Testing validates both control execution and the reliability of evidence. A confirmed customer-impacting defect requires impact analysis, correction, complaint review, and consideration of broader remediation. High-severity findings overdue by more than ten business days are reported to the Enterprise Risk Committee.

## 12. Findings and Remediation

| Finding ID | Description | Severity | Related Control | Owner | Due Date | Status |
|---|---|---|---|---|---|---|
| FIND-2026-17 | Seven payments received without a complete loan reference remained in suspense longer than the internal two-business-day target. | Medium | CTRL-PAY-01 | Loan Operations Manager | 2026-10-15 | Remediation in progress |
| FIND-2026-22 | Legacy statement reconciliation evidence did not identify the reviewer for two monthly cycles. | Medium | CTRL-STMT-02 | Servicing Control Team Lead | 2026-11-01 | Open |

FIND-2026-17 requires a required-reference validation at intake, daily suspense aging, and documented outreach after one business day. Closure requires thirty consecutive days with no payment aged beyond two business days without a recorded investigation.

FIND-2026-22 depends on completion of the legacy-feed exception plan. Closure requires system-captured reviewer identity and successful independent testing of two statement cycles.

## 13. Key Dates and Thresholds

| Item | Value | Unit | Applies To |
|---|---|---|---|
| Document review frequency | 6 | months | MCB-GRC-307 |
| Consumer application regulatory minimum | 25 | months | Covered Regulation B application records |
| Meridian lending record retention | 7 | years after loan closure | Loan and servicing records |
| Payment suspense investigation target | 2 | business days | Unmatched accepted payments |
| High-severity remediation escalation | 10 | business days overdue | Open high-severity findings |
| Military-service interest threshold | 6 | percent per year | Eligible pre-service obligations during the protected period |

## 14. Conflicts, Ambiguities, and Interpretations

The Enterprise Records Schedule describes the seven-year lending period as beginning at “account closure,” while Loan Operations traditionally begins it at “final payment.” These dates are usually close but are not always the same. For this standard, account closure means the date on which the loan is paid, charged off, transferred with no remaining servicing duty, or otherwise closed in the system after all adjustments. A final payment alone does not start disposal when an unresolved adjustment or complaint remains.

The Payment Operations Manual defines a “business day” as a day on which the payment unit processes transactions. The Customer Complaint Policy defines it as Monday through Friday excluding federal holidays. Until the manuals are aligned, the more customer-protective deadline applies to investigation and correction commitments.

Version 1.2 used “servicing exception” for both a failed transaction and an approved waiver from a control. Version 1.3 revises this usage: a failed or unmatched transaction is an Operational Exception, while an approved temporary departure from a control is a Control Exception. Existing records retain their original labels but must be interpreted using their context.

## 15. Change History

| Version | Date | Change | Reason | Approved By |
|---|---|---|---|---|
| 1.2 | 2025-09-01 | Added military-benefit and payment-suspense controls. | Consolidate servicing compliance requirements. | Chief Compliance Officer |
| 1.3 | 2026-09-01 | Split exception terminology, clarified retention start date, and added statement evidence requirements. | Resolve audit findings and inconsistent operating interpretations. | Chief Compliance Officer |

## 16. Appendices

### Appendix A: Terms and Definitions

| Term | Definition | Source |
|---|---|---|
| Borrower | A person obligated under a consumer loan agreement. | Approved product agreement |
| Customer account | A deposit or payment account associated with a customer and used in an authorized servicing process. | Meridian Account Governance Standard |
| Loan payment | Money accepted for application to a specified loan obligation. | Loan Operations Manual |
| Operational Exception | A transaction, data, or processing event that did not complete as expected. | MCB-GRC-307, version 1.3 |
| Control Exception | A documented, approved, and temporary departure from an internal control requirement. | MCB-GRC-307, version 1.3 |
| Military benefit review | The evidence and decision process used to determine and apply an eligible statutory protection. | Military Benefits Procedure |

### Appendix B: Relationship Examples

- The Loan Operations Manager owns the payment-to-loan validation control.
- Payment-to-loan validation mitigates the risk of a payment being applied to the wrong loan.
- A payment validation record demonstrates operation of the payment control.
- Regulation B retention requirements apply to covered application-record processes.
- FIND-2026-17 affects the payment-suspense process and its servicing records.
- MCB-GRC-307 version 1.3 supersedes version 1.2.

On September 3, 2026, borrower Amina Patel submitted Payment PMT-77421-0903 for USD 612.40 through Deposit Account DA-55190 for Consumer Loan CL-77421. The servicing platform matched the borrower, loan reference, and approved payment source before posting the payment. The resulting validation record supports CTRL-PAY-01.

### Appendix C: Known Interpretation Variations

“Authorized payment account” and “approved payment source” refer to the same servicing concept in this document. “Exception” is used historically for both a processing failure and an approved waiver, but Section 14 replaces that broad meaning with Operational Exception and Control Exception.

The Payment Operations Manual and Customer Complaint Policy conflict over the meaning of “business day.” The seven-year internal retention rule extends the regulatory minimum described in Section 3 rather than replacing it. The approved legacy-feed amendment temporarily changes when reconciliation occurs but does not waive accurate customer treatment.
