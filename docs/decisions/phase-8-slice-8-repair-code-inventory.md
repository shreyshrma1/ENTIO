# Phase 8 Slice 8 Repair Code Inventory

Only deterministic finding codes listed here as `AUTO_REPAIRABLE` may enter automatic repair. Repairs remain private typed draft changes and require complete reanalysis.

| Finding code | Deterministic source | Entity/item references | Allowed typed repair | Business clarification | Automatic safety | Revalidation | Tests | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `invalid-language-tag` | `SemanticMetadataValidator` | finding source plus affected draft item/entity | replace the affected approved label/definition operation with the single deterministic normalized language-tag candidate | required if no single candidate exists | safe only with exactly one candidate supplied by deterministic validation | full package analysis | packet mapping, single-candidate application, reanalysis state | `AUTO_REPAIRABLE` |
| `duplicate-draft-edit` | `AiPrivateDraftWorkspace` conflict checks | both duplicate draft item IDs | remove the later duplicate typed draft item | no when identity is exact | safe when both item IDs are exact | full package analysis | deterministic later-item removal and history retention | `AUTO_REPAIRABLE` |
| `ambiguous-preferred-label` | `SemanticMetadataValidator` | affected entity and label item IDs | user-selected preferred-label update/removal | yes | unsafe automatically | full package analysis after user choice | automatic denial and clarification pause | `CLARIFICATION_REQUIRED` |
| `incompatible-property-domain` | `ProposalValidator` | property, expected domain, actual domain, item IDs | user-selected domain revision using existing typed edit | yes | unsafe automatically | full package analysis after user choice | automatic denial and clarification pause | `CLARIFICATION_REQUIRED` |
| `incompatible-property-range` | `ProposalValidator` | property, expected range, actual range, item IDs | user-selected range revision using existing typed edit | yes | unsafe automatically | full package analysis after user choice | automatic denial and clarification pause | `CLARIFICATION_REQUIRED` |
| `shacl-validation` | `ShaclValidationService` | shape, focus node, path, finding and item IDs | none without a more specific inventoried deterministic code | possibly | unsafe automatically | unchanged deterministic SHACL validation | automatic denial | `EXPLANATION_ONLY` |

Unknown codes and codes absent from this table are `UNSUPPORTED` and cannot be repaired automatically.
