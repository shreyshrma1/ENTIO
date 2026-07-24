# Commercial Account and Payment Authorization Policy

## Document Control

| Field | Value |
|---|---|
| Policy Title | Commercial Account and Payment Authorization Policy |
| Policy ID | MCB-OPS-214 |
| Version | 2.1 |
| Status | Approved |
| Policy Owner | Director of Treasury Operations |
| Approved By | Enterprise Risk Committee |
| Effective Date | 2026-08-01 |
| Review Date | 2027-08-01 |
| Supersedes | MCB-OPS-214, version 2.0 |
| Applies To | Meridian Community Bank commercial banking operations |
| Classification | Internal |

## 1. Purpose

This policy establishes how Meridian Community Bank opens and administers commercial deposit accounts and authorizes payments from those accounts. Its purpose is to protect customer funds, maintain reliable ownership and authorization records, and ensure that invoice payments and loan-related transfers receive review proportionate to their value and risk.

The policy is designed to preserve a clear distinction between the organization that owns an account, the people permitted to act for that organization, and the employees who approve or release a transaction.

## 2. Scope

This policy applies to:

- people or roles: commercial relationship managers, account-opening specialists, payment analysts, treasury operations managers, and internal auditors;
- departments: Commercial Banking, Treasury Operations, Accounts Payable Services, Financial Crime Compliance, and Internal Audit;
- systems: Meridian Account Registry, MeridianPay, and the customer document vault;
- products or services: commercial checking accounts, controlled-disbursement accounts, electronic payments, and scheduled loan payments;
- data: customer identity records, beneficial ownership records, signature authorities, payment instructions, invoices, and account activity;
- locations: all United States branches and operations centers;
- third parties: payment processors and verification vendors acting under a Meridian contract.

This policy excludes consumer deposit accounts, trust accounts administered by the Wealth Division, and card transactions governed by the Corporate Card Standard.

## 3. Policy Statement

Meridian must not activate a commercial account until the legal customer has been identified, ownership information has been reviewed, and at least one authorized representative has been approved. The account-opening specialist must record the legal owner separately from each person who may give instructions. A signer, delegate, or employee of the customer does not become an owner merely because that person can initiate a payment.

Payments must be supported by a business purpose and an identifiable destination. When a payment settles an invoice, the payment record must reference the invoice number and the customer account from which funds will be drawn. When a transfer services a Meridian loan, the payment record must reference the applicable loan agreement or servicing instruction.

No employee may both create and finally approve the same high-value payment. Treasury Operations may temporarily restrict an account when ownership records are incomplete, authorization appears inconsistent, or the payment destination cannot be verified. Restrictions must be documented and reviewed within one business day.

The customer remains responsible for the accuracy of instructions submitted by its approved representatives. Meridian remains responsible for applying its own authorization, screening, and recordkeeping controls before releasing funds.

## 4. Key Terms

| Term | Definition | Alternate Label | Related Term |
|---|---|---|---|
| Account Owner | The legal person or organization entitled to the funds and obligations recorded for an account. | Customer of record | Authorized Representative |
| Authorized Representative | A natural person approved to give specified instructions for an account owner. | Authorized signer | Payment Initiator |
| High-Value Payment | A single outgoing payment of USD 25,000 or more, including linked payments that form one business transaction. | HVP | Dual Approval |
| Account Restriction | A temporary control that limits selected account activity while a risk or record issue is reviewed. | Account hold | Exception Review |

For payment approval, “authorized signer” and “authorized representative” are used interchangeably. For account ownership reporting, “signer” must never be interpreted as “owner.” Section 13 records a later operating change that extends the representative concept to approved digital delegates.

## 5. Roles and Responsibilities

| Role | Responsibility | Decision Authority | Escalates To |
|---|---|---|---|
| Account-Opening Specialist | Verifies organizational records and records owners and representatives. | May approve a complete standard account package. | Commercial Operations Manager |
| Payment Analyst | Reviews payment purpose, supporting records, destination, and approval requirements. | May release payments below USD 25,000 when no exception is present. | Treasury Operations Manager |
| Treasury Operations Manager | Approves high-value payments, account restrictions, and time-limited control exceptions. | May approve payments up to USD 250,000 within assigned authority. | Director of Treasury Operations |
| Financial Crime Compliance Officer | Reviews sanctions, fraud, and unusual-activity concerns. | May require a restriction or enhanced review. | Chief Compliance Officer |

The relationship manager gathers customer information but does not make the final control decision. Account-Opening Specialists hand approved authorization records to Treasury Operations. Payment Analysts consult Financial Crime Compliance when a destination or payment pattern raises concern, and Internal Audit independently tests the completed record.

## 6. Policy Rules

### 6.1 Required Actions

| Rule ID | Actor | Required Action | Object | Condition | Deadline |
|---|---|---|---|---|---|
| MCB-214-001 | Account-Opening Specialist | Verify and record the legal owner and each authorized representative. | Commercial account package | Before account activation | Before end-of-day activation |
| MCB-214-002 | Payment Analyst | Match the instruction, supporting invoice or agreement, destination, and approval record. | Outgoing payment | Before release | Same business day |
| MCB-214-003 | Treasury Operations Manager | Provide a separate recorded approval. | High-value payment | Payment is USD 25,000 or more | Before release |
| MCB-214-004 | Payment Analyst | Associate the payment with the applicable invoice or loan servicing reference. | Business-purpose payment | A supporting record exists | Before release |

### 6.2 Prohibited Actions

| Rule ID | Actor | Prohibited Action | Object | Exception |
|---|---|---|---|---|
| MCB-214-005 | Any Meridian employee | Treat an authorized representative as the legal owner without supporting ownership evidence. | Commercial account | None |
| MCB-214-006 | Payment Initiator | Create and finally approve the same high-value payment. | High-value payment | None |
| MCB-214-007 | Payment Analyst | Divide one business transaction to avoid the high-value threshold. | Linked payments | None |

### 6.3 Permitted Actions

| Rule ID | Actor | Permitted Action | Object | Condition |
|---|---|---|---|---|
| MCB-214-008 | Treasury Operations Manager | Apply a temporary account restriction. | Commercial account | A documented authorization or destination concern exists. |
| MCB-214-009 | Payment Analyst | Release a payment below USD 25,000. | Standard payment | Required records are complete and no escalation trigger is present. |
| MCB-214-010 | Commercial Operations Manager | Accept an electronically signed authorization. | Account authorization record | Identity and signature validation have passed. |

### 6.4 Narrative Rules

Linked payments must be evaluated as one transaction when they share an invoice, agreement, beneficiary, destination account, or common instruction. A payment of USD 18,000 followed by USD 12,000 for the same invoice is therefore a USD 30,000 high-value payment for approval purposes.

An account restriction may prevent outgoing payments while still allowing incoming funds and loan credits. If the concern affects ownership rather than a single payment, the restriction applies to the account. If the concern affects only one destination, Treasury Operations should restrict that payment rather than unrelated account activity.

## 7. Business Objects and Records

| Object ID | Object Type | Name | Owner | Status | Related Object |
|---|---|---|---|---|---|
| CUST-44018 | Commercial customer record | Harbor Point Dental Group LLC | Commercial Banking | Active | ACCT-884210 |
| ACCT-884210 | Commercial checking account | Harbor Point Operating Account | Harbor Point Dental Group LLC | Active | AUTH-44018-03 |
| AUTH-44018-03 | Representative authorization | Elena Ruiz payment authority | Harbor Point Dental Group LLC | Active | ACCT-884210 |
| INV-44719 | Supplier invoice | Summit Medical Supply Invoice 44719 | Harbor Point Dental Group LLC | Approved for payment | PAY-902771 |
| PAY-902771 | Electronic payment instruction | Invoice 44719 settlement | Harbor Point Dental Group LLC | Released | INV-44719 |

Harbor Point Dental Group LLC owns the Harbor Point Operating Account. Elena Ruiz, Harbor Point’s finance director, may initiate payments from that account but is not recorded as its owner. Payment PAY-902771 settled Summit Medical Supply Invoice 44719 for USD 28,460. Because the payment exceeded the high-value threshold, Marcus Lee initiated the review and Treasury Operations Manager Priya Nair supplied the final approval.

## 8. Procedures

### Procedure: Review and Release a Commercial Payment

1. The Payment Analyst receives a payment instruction and its invoice, agreement, or servicing reference.
2. The Payment Analyst verifies that the initiator is authorized for the customer account and that the destination matches the supporting record.
3. MeridianPay creates a payment review record and links the account, instruction, supporting record, destination, and initiator.
4. The Payment Analyst approves or rejects a standard payment; a separate Treasury Operations Manager approves a high-value payment.
5. MeridianPay stores the decision, timestamps, reviewer identities, and release result.
6. The Payment Analyst escalates an ownership mismatch, unverifiable destination, suspected payment splitting, or sanctions alert to the Treasury Operations Manager and Financial Crime Compliance.

If an invoice covers several scheduled installments, each installment may be processed separately only when the contract itself establishes those installments. Urgent processing does not waive authorization or screening. A system outage requires use of the approved continuity log and retrospective entry into MeridianPay by the next business day.

## 9. Exceptions

| Exception ID | Condition | Requested By | Approved By | Compensating Action | Expiration |
|---|---|---|---|---|---|
| EXC-214-026 | MeridianPay unavailable during a documented continuity event | Treasury Operations Manager | Director of Treasury Operations | Two-person written approval, callback verification, and next-day system reconciliation | 2026-12-31 |

Exceptions are appropriate only when a required control cannot operate as designed and a documented alternative provides comparable protection. No exception may waive ownership verification, sanctions screening, or separation of initiation and final approval for high-value payments.

## 10. Monitoring and Enforcement

| Monitoring Activity | Performed By | Frequency | Evidence | Failure Response |
|---|---|---|---|---|
| High-value payment approval review | Treasury Quality Assurance | Weekly | Payment decision log and approval timestamps | Correct the record and assess unauthorized release exposure. |
| Commercial account ownership reconciliation | Commercial Operations Control Team | Monthly | Account registry and customer document comparison | Restrict affected account and remediate within five business days. |
| Payment-splitting analytics | Financial Crime Compliance | Daily | Linked-payment alert report | Investigate, hold pending items, and escalate confirmed evasion. |
| Independent policy testing | Internal Audit | Annually | Sample workpapers and control test results | Record a finding and assign corrective action. |

Material breaches may result in payment suspension, account restriction, employee corrective action, customer notification, loss assessment, or regulatory escalation as determined by Legal and Compliance.

## 11. Records and Retention

| Record Type | Created By | Stored In | Retention Period | Disposal Method |
|---|---|---|---|---|
| Commercial account package | Account-Opening Specialist | Customer document vault | Seven years after account closure | Approved secure deletion |
| Representative authorization | Commercial Operations | Customer document vault | Seven years after authority ends | Approved secure deletion |
| Payment review record | MeridianPay | Payment records repository | Seven years after payment date | Approved secure deletion |
| Control exception record | Treasury Operations Manager | Governance records library | Seven years after expiration | Approved secure deletion |

## 12. Examples

### Example 1: Compliant Scenario

On September 14, 2026, Elena Ruiz instructed Meridian to pay USD 28,460 from Harbor Point Operating Account ACCT-884210 to Summit Medical Supply for Invoice INV-44719. Payment Analyst Marcus Lee confirmed Elena’s active authority, matched the destination to the approved invoice, and created PAY-902771. Priya Nair separately approved the high-value payment before MeridianPay released it.

### Example 2: Non-Compliant Scenario

On October 2, 2026, an analyst received two instructions for USD 14,750 each from the same customer, on the same day, for one equipment invoice. The analyst released both payments without separate managerial approval because each instruction was below USD 25,000. The payments were one linked USD 29,500 transaction and should have been reviewed as a high-value payment.

### Example 3: Ambiguous Scenario

Northstar Catering authorized a payroll provider to submit payroll files but did not clearly authorize the provider to change the destination account for tax payments. The agreement calls the provider an “authorized payment agent,” while the account record limits it to “file submission.” Treasury Operations must pause the changed destination and obtain clarification because the two records support different interpretations.

## 13. Change and Evolution Section

### Current Meaning

An Authorized Representative is a natural person approved to give specified instructions for an account owner. Meridian records the person’s identity, authority scope, effective date, and any transaction limit.

### New Information

The 2026 Digital Treasury Pilot permits a corporate customer to authorize a registered payment service to transmit instructions using a service credential. The service acts under the customer’s authority, but it is not a natural person and cannot complete callback verification.

### Recommended Interpretation

The policy meaning should be extended by distinguishing a human Authorized Representative from an Authorized Payment Service. Both may transmit instructions, but only a human representative may satisfy a control that specifically requires personal confirmation or managerial approval. The pilot does not revise account ownership.

## 14. Related Documents

| Document ID | Title | Relationship | Effective Date |
|---|---|---|---|
| MCB-STD-118 | Accounts Payable Service Standard | Conflicts With: requires dual approval at USD 15,000 | 2025-11-01 |
| MCB-AML-041 | Customer and Payment Screening Standard | Supports | 2026-03-01 |
| MCB-OPS-214-A1 | Digital Treasury Pilot Amendment | Amends | 2026-10-01 |

Where MCB-STD-118 applies to Meridian-managed accounts-payable services, its stricter USD 15,000 threshold controls. For other commercial payments, this policy’s USD 25,000 threshold applies.

## 15. Change History

| Version | Date | Summary of Change | Reason | Approved By |
|---|---|---|---|---|
| 2.0 | 2025-08-01 | Consolidated account authority and payment approval rules. | Establish one commercial operations policy. | Enterprise Risk Committee |
| 2.1 | 2026-08-01 | Added linked-payment treatment, account restrictions, and digital-service interpretation. | Address control testing findings and new payment channels. | Enterprise Risk Committee |

## 16. Interpretation Guidance

This policy distinguishes an account owner from an authorized representative and recognizes an Authorized Payment Service as a separate type of delegate. These distinctions must be preserved in operating procedures, training, reporting, and control evidence.

The phrase “authorized payment agent” is not sufficient by itself to establish authority to change payment destinations. The USD 15,000 threshold in MCB-STD-118 applies only to Meridian-managed accounts-payable services, while the USD 25,000 threshold applies to other commercial payments. The Digital Treasury Pilot extends who may transmit payment instructions without changing account ownership or final approval responsibilities.
