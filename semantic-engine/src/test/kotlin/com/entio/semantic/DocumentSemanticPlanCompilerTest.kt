package com.entio.semantic

import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentCompilationStatus
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentSemanticOutcome
import com.entio.core.DocumentSemanticPlan
import com.entio.core.DocumentSemanticPlanItem
import com.entio.core.DocumentSemanticRecommendationGroup
import com.entio.core.DocumentSemanticReference
import com.entio.core.DocumentSemanticReferenceRole
import com.entio.core.DocumentSemanticReferenceTarget
import com.entio.core.DocumentTemporaryReferenceKind
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentSemanticPlanCompilerTest {
    @Test
    fun `compiles a recommended datatype range and preserves its review marker`(): Unit {
        val payment = item("payment", DocumentSemanticItemKind.Class, "Payment")
        val reference = item("reference", DocumentSemanticItemKind.DatatypeProperty, "servicing reference")
        val domain = item(
            "reference-domain",
            DocumentSemanticItemKind.DatatypePropertyDomain,
            "servicing reference domain",
            refs(
                DocumentSemanticReferenceRole.Property to reference.id,
                DocumentSemanticReferenceRole.Domain to payment.id,
            ),
        )
        val range = item(
            "reference-range",
            DocumentSemanticItemKind.DatatypePropertyRange,
            "servicing reference datatype",
            refs(DocumentSemanticReferenceRole.Property to reference.id),
            datatypeIntent = "http://www.w3.org/2001/XMLSchema#string",
            modelRecommended = true,
            reviewerInputRequired = true,
        )

        val result = compile(payment, reference, domain, range)

        assertEquals(DocumentCompilationStatus.Compiled, result.status, result.failures.toString())
        val rangeOperation = result.operations.single {
            it.kind == DocumentPlanOperationKind.SetPropertyRange
        }
        assertTrue(rangeOperation.modelRecommended)
        assertTrue(rangeOperation.reviewerInputRequired)
        assertTrue(
            rangeOperation.operands.any {
                it == com.entio.core.DocumentPlanOperand.ExistingEntity(
                    Iri("http://www.w3.org/2001/XMLSchema#string"),
                )
            },
        )
    }

    @Test
    fun `compiles connected classes and an object property with domain and range`(): Unit {
        val customer = item("customer", DocumentSemanticItemKind.Class, "Customer")
        val account = item("account", DocumentSemanticItemKind.Class, "Account")
        val property = item("owns", DocumentSemanticItemKind.ObjectProperty, "owns account")
        val domain = item(
            "owns-domain",
            DocumentSemanticItemKind.ObjectPropertyDomain,
            "owns account domain",
            refs(
                DocumentSemanticReferenceRole.Property to property.id,
                DocumentSemanticReferenceRole.Domain to customer.id,
            ),
        )
        val range = item(
            "owns-range",
            DocumentSemanticItemKind.ObjectPropertyRange,
            "owns account range",
            refs(
                DocumentSemanticReferenceRole.Property to property.id,
                DocumentSemanticReferenceRole.Range to account.id,
            ),
        )

        val result = compile(customer, account, property, domain, range)

        assertEquals(DocumentCompilationStatus.Compiled, result.status, result.failures.toString())
        assertEquals(
            listOf(
                DocumentPlanOperationKind.CreateClass,
                DocumentPlanOperationKind.CreateClass,
                DocumentPlanOperationKind.CreateObjectProperty,
                DocumentPlanOperationKind.SetPropertyDomain,
                DocumentPlanOperationKind.SetPropertyRange,
            ),
            result.operations.map { it.kind },
        )
        assertEquals(3, result.references.size)
    }

    @Test
    fun `adds explicitly referenced declarations that a semantic group omitted`(): Unit {
        val payment = item("payment", DocumentSemanticItemKind.Class, "Payment")
        val approvedBy = item("approved-by", DocumentSemanticItemKind.ObjectProperty, "approved by")
        val domain = item(
            "approved-by-domain",
            DocumentSemanticItemKind.ObjectPropertyDomain,
            "approved by domain",
            refs(
                DocumentSemanticReferenceRole.Property to approvedBy.id,
                DocumentSemanticReferenceRole.Domain to payment.id,
            ),
        )
        val plan = DocumentSemanticPlan(
            workKey = DocumentAnalysisWorkKey("a".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            items = listOf(payment, approvedBy, domain).sortedBy(DocumentSemanticPlanItem::stableOrderingKey),
            groups = listOf(group("group-domain", listOf(domain))),
        )

        val result = compiler.compile(plan, context()).single()

        assertEquals(DocumentCompilationStatus.Compiled, result.status, result.failures.toString())
        assertEquals(
            setOf(DocumentPlanOperationKind.CreateClass, DocumentPlanOperationKind.CreateObjectProperty),
            result.operations.take(2).map(DocumentPlanOperation::kind).toSet(),
        )
        assertEquals(DocumentPlanOperationKind.SetPropertyDomain, result.operations.last().kind)
        assertTrue(result.operations.last().dependsOnOperationIds.size == 2)
    }

    @Test
    fun `compiles supported work while retaining unsupported meaning in its bundle`(): Unit {
        val report = item(
            "complaint-report",
            DocumentSemanticItemKind.Class,
            "Complaint Trend Report",
            modelRecommended = true,
            reviewerInputRequired = true,
        )
        val rule = item(
            "complaint-rule",
            DocumentSemanticItemKind.ComplexRule,
            "Monthly complaint review rule",
            refs(DocumentSemanticReferenceRole.Related to report.id),
            outcome = DocumentSemanticOutcome.ReviewOnly,
        )
        val orderedItems = listOf(report, rule).sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
        val plan = DocumentSemanticPlan(
            workKey = DocumentAnalysisWorkKey("a".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            items = orderedItems,
            groups = listOf(
                DocumentSemanticRecommendationGroup(
                    id = "complaint-bundle",
                    title = "Model Complaint Trend Report",
                    description = "Compile supported work and retain the rule.",
                    itemIds = orderedItems.map(DocumentSemanticPlanItem::id).sorted(),
                    reviewOnlyItemIds = listOf(rule.id),
                    discoveryIds = listOf("discovery-1"),
                    evidenceIds = listOf(evidenceId),
                    outcome = DocumentSemanticOutcome.Executable,
                    rationale = "The class is editable and the unsupported rule remains visible.",
                    confidence = DocumentConfidenceDimensions(90, 85, 80),
                ),
            ),
        )

        val result = compiler.compile(plan, context()).single()

        assertEquals(DocumentCompilationStatus.Compiled, result.status, result.failures.toString())
        assertEquals(listOf(DocumentPlanOperationKind.CreateClass), result.operations.map(DocumentPlanOperation::kind))
        assertTrue(result.operations.single().modelRecommended)
        assertTrue(result.operations.single().reviewerInputRequired)
    }

    @Test
    fun `compiles bounded explicit dependency closures within the throughput budget`(): Unit {
        val items = (1..50).flatMap { index ->
            val target = item("target-$index", DocumentSemanticItemKind.Class, "Target $index")
            val shape = item(
                "shape-$index",
                DocumentSemanticItemKind.NodeShape,
                "Shape $index",
                refs(DocumentSemanticReferenceRole.TargetClass to target.id),
            )
            listOf(target, shape)
        }
        val shapes = items.filter { it.kind == DocumentSemanticItemKind.NodeShape }
        val plan = DocumentSemanticPlan(
            workKey = DocumentAnalysisWorkKey("a".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            items = items.sortedBy(DocumentSemanticPlanItem::stableOrderingKey),
            groups = shapes.mapIndexed { index, shape ->
                group("throughput-group-${index + 1}", listOf(shape))
            }.sortedBy(DocumentSemanticRecommendationGroup::stableOrderingKey),
        )
        var compiledCount = 0

        val durationMillis = measureTimeMillis {
            repeat(100) {
                val results = compiler.compile(plan, context())
                compiledCount += results.count { it.status == DocumentCompilationStatus.Compiled }
            }
        }

        assertEquals(5_000, compiledCount)
        assertTrue(durationMillis < 10_000, "Dependency completion took ${durationMillis}ms for 100 bounded plans.")
    }

    @Test
    fun `compiles datatype property range hierarchy individual type and assertions`(): Unit {
        val person = item("person", DocumentSemanticItemKind.Class, "Person")
        val employee = item("employee", DocumentSemanticItemKind.Class, "Employee")
        val manager = item("manager", DocumentSemanticItemKind.Individual, "Manager One")
        val reportsTo = item("reports-to", DocumentSemanticItemKind.ObjectProperty, "reports to")
        val salary = item("salary", DocumentSemanticItemKind.DatatypeProperty, "salary")
        val hierarchy = item(
            "hierarchy",
            DocumentSemanticItemKind.SubclassRelationship,
            "Employee is a Person",
            refs(
                DocumentSemanticReferenceRole.Subclass to employee.id,
                DocumentSemanticReferenceRole.Superclass to person.id,
            ),
        )
        val type = item(
            "manager-type",
            DocumentSemanticItemKind.IndividualType,
            "Manager type",
            refs(
                DocumentSemanticReferenceRole.Individual to manager.id,
                DocumentSemanticReferenceRole.Type to employee.id,
            ),
        )
        val objectAssertion = item(
            "reports-assertion",
            DocumentSemanticItemKind.ObjectPropertyAssertion,
            "Manager reports to manager",
            refs(
                DocumentSemanticReferenceRole.Subject to manager.id,
                DocumentSemanticReferenceRole.Predicate to reportsTo.id,
                DocumentSemanticReferenceRole.Object to manager.id,
            ),
        )
        val valueAssertion = item(
            "salary-value",
            DocumentSemanticItemKind.DatatypeValueAssertion,
            "Manager salary",
            refs(
                DocumentSemanticReferenceRole.Subject to manager.id,
                DocumentSemanticReferenceRole.Predicate to salary.id,
            ),
            literal = RdfLiteral("25000"),
        )
        val datatypeRange = item(
            "salary-range",
            DocumentSemanticItemKind.DatatypePropertyRange,
            "Salary range",
            listOf(
                DocumentSemanticReference(
                    DocumentSemanticReferenceRole.Property,
                    DocumentSemanticReferenceTarget.SemanticItem(salary.id),
                ),
                DocumentSemanticReference(
                    DocumentSemanticReferenceRole.Range,
                    DocumentSemanticReferenceTarget.Alignment("xsd-decimal"),
                ),
            ).sortedBy(DocumentSemanticReference::stableOrderingKey),
            datatypeIntent = "decimal",
        )
        val context = context(
            aligned = mapOf(
                "xsd-decimal" to DocumentCompilerEntity(
                    Iri("http://www.w3.org/2001/XMLSchema#decimal"),
                    DocumentTemporaryReferenceKind.Class,
                    "imported",
                    false,
                ),
            ),
        )

        val result = compile(
            person,
            employee,
            manager,
            reportsTo,
            salary,
            hierarchy,
            type,
            objectAssertion,
            valueAssertion,
            datatypeRange,
            context = context,
        )

        assertEquals(DocumentCompilationStatus.Compiled, result.status, result.failures.toString())
        assertTrue(DocumentPlanOperationKind.AddSuperclass in result.operations.map { it.kind })
        assertTrue(DocumentPlanOperationKind.AssignType in result.operations.map { it.kind })
        assertTrue(DocumentPlanOperationKind.AddObjectPropertyAssertion in result.operations.map { it.kind })
        assertTrue(DocumentPlanOperationKind.AddDatatypePropertyAssertion in result.operations.map { it.kind })
        assertTrue(DocumentPlanOperationKind.SetPropertyRange in result.operations.map { it.kind })
    }

    @Test
    fun `reuses an exact existing entity and prevents a generated duplicate`(): Unit {
        val customer = item("customer", DocumentSemanticItemKind.Class, "Customer")
        val existing = DocumentCompilerEntity(
            Iri("https://example.com/Customer"),
            DocumentTemporaryReferenceKind.Class,
            "simple",
            true,
        )
        val reused = compile(
            customer,
            context = context(
                aligned = mapOf("alignment-customer" to existing),
                itemAlignments = mapOf(customer.id to "alignment-customer"),
            ),
        )
        assertEquals(DocumentCompilationStatus.Blocked, reused.status)
        assertEquals("semantic-group-empty", reused.failures.single().safeCode)

        val duplicate = compile(
            customer,
            context = context(existingEntities = mapOf(existing.iri to existing.kind)),
        )
        assertEquals("duplicate-entity", duplicate.failures.single().safeCode)
    }

    @Test
    fun `extends an approved external class locally without writing the external source`(): Unit {
        val localClass = item("local-payment", DocumentSemanticItemKind.Class, "Local Payment")
        val hierarchy = item(
            "external-hierarchy",
            DocumentSemanticItemKind.SubclassRelationship,
            "Local payment extends external payment",
            listOf(
                DocumentSemanticReference(
                    DocumentSemanticReferenceRole.Subclass,
                    DocumentSemanticReferenceTarget.SemanticItem(localClass.id),
                ),
                DocumentSemanticReference(
                    DocumentSemanticReferenceRole.Superclass,
                    DocumentSemanticReferenceTarget.Alignment("external-payment"),
                ),
            ).sortedBy(DocumentSemanticReference::stableOrderingKey),
        )
        val result = compile(
            localClass,
            hierarchy,
            context = context(
                aligned = mapOf(
                    "external-payment" to DocumentCompilerEntity(
                        Iri("https://external.example/Payment"),
                        DocumentTemporaryReferenceKind.Class,
                        "external",
                        false,
                    ),
                ),
            ),
        )

        assertEquals(DocumentCompilationStatus.Compiled, result.status)
        assertTrue(result.operations.all { operation ->
            operation.operands.filterIsInstance<com.entio.core.DocumentPlanOperand.SourceId>()
                .single().value == "simple"
        })
    }

    @Test
    fun `compiles supported required relationship and numeric threshold constraints`(): Unit {
        val account = item("account", DocumentSemanticItemKind.Class, "Account")
        val balance = item("balance", DocumentSemanticItemKind.DatatypeProperty, "balance")
        val nodeShape = item(
            "account-shape",
            DocumentSemanticItemKind.NodeShape,
            "Account shape",
            refs(DocumentSemanticReferenceRole.TargetClass to account.id),
        )
        val propertyShape = item(
            "balance-shape",
            DocumentSemanticItemKind.PropertyShape,
            "Required balance",
            refs(
                DocumentSemanticReferenceRole.Shape to nodeShape.id,
                DocumentSemanticReferenceRole.Path to balance.id,
            ),
        )
        val required = item(
            "balance-required",
            DocumentSemanticItemKind.ShaclConstraint,
            "Balance is required",
            refs(DocumentSemanticReferenceRole.ConstraintTarget to propertyShape.id),
            datatypeIntent = "MinCount:1",
        )

        val requiredResult = compile(account, balance, nodeShape, propertyShape, required)

        assertEquals(DocumentCompilationStatus.Compiled, requiredResult.status)
        assertTrue(DocumentPlanOperationKind.CreateNodeShape in requiredResult.operations.map { it.kind })
        assertTrue(DocumentPlanOperationKind.CreatePropertyShape in requiredResult.operations.map { it.kind })

        val thresholdShape = item(
            "threshold-shape",
            DocumentSemanticItemKind.PropertyShape,
            "Minimum balance threshold",
            refs(
                DocumentSemanticReferenceRole.Shape to nodeShape.id,
                DocumentSemanticReferenceRole.Path to balance.id,
            ),
        )
        val threshold = item(
            "balance-threshold",
            DocumentSemanticItemKind.ShaclConstraint,
            "Minimum balance",
            refs(DocumentSemanticReferenceRole.ConstraintTarget to thresholdShape.id),
            datatypeIntent = "MinInclusive:25000",
        )
        val thresholdResult = compile(account, balance, nodeShape, thresholdShape, threshold)
        assertEquals(DocumentCompilationStatus.Compiled, thresholdResult.status)
        assertTrue(DocumentPlanOperationKind.CreatePropertyShape in thresholdResult.operations.map { it.kind })
    }

    @Test
    fun `keeps complete complex rules review only without weakening their meaning`(): Unit {
        listOf(
            "separation of duties",
            "linked payment aggregation",
            "conditional applicability",
            "temporal sequencing",
        ).forEachIndexed { index, label ->
            val rule = item(
                "rule-$index",
                DocumentSemanticItemKind.ComplexRule,
                label,
                refs(DocumentSemanticReferenceRole.Related to "existing-class", alignmentTargets = true),
                outcome = DocumentSemanticOutcome.ReviewOnly,
            )
            val result = compile(
                rule,
                outcome = DocumentSemanticOutcome.ReviewOnly,
                context = context(
                    aligned = mapOf(
                        "existing-class" to DocumentCompilerEntity(
                            Iri("https://example.com/Account"),
                            DocumentTemporaryReferenceKind.Class,
                            "simple",
                            true,
                        ),
                    ),
                ),
            )
            assertEquals(DocumentCompilationStatus.ReviewOnly, result.status)
            assertTrue(result.operations.isEmpty())
        }
    }

    @Test
    fun `blocks bad domain type and assertion targets`(): Unit {
        val property = item("property", DocumentSemanticItemKind.ObjectProperty, "owns")
        val individual = item("individual", DocumentSemanticItemKind.Individual, "Example")
        val badDomain = item(
            "bad-domain",
            DocumentSemanticItemKind.ObjectPropertyDomain,
            "Bad domain",
            refs(
                DocumentSemanticReferenceRole.Property to property.id,
                DocumentSemanticReferenceRole.Domain to individual.id,
            ),
        )
        assertEquals(
            "semantic-reference-kind-mismatch",
            compile(property, individual, badDomain).failures.single().safeCode,
        )

        val badType = item(
            "bad-type",
            DocumentSemanticItemKind.IndividualType,
            "Bad type",
            refs(
                DocumentSemanticReferenceRole.Individual to individual.id,
                DocumentSemanticReferenceRole.Type to property.id,
            ),
        )
        assertEquals(
            "semantic-reference-kind-mismatch",
            compile(property, individual, badType).failures.single().safeCode,
        )
    }

    @Test
    fun `does not compile administrative metadata as a business concept`(): Unit {
        val metadata = item("metadata", DocumentSemanticItemKind.Class, "Document Version")
        val result = compile(
            metadata,
            context = context(administrativeDiscoveryIds = setOf("discovery-1")),
        )

        assertEquals(DocumentCompilationStatus.Blocked, result.status)
        assertEquals("administrative-metadata-not-executable", result.failures.single().safeCode)
    }

    @Test
    fun `declares a missing supporting concept instead of substituting an unrelated class`(): Unit {
        val supporting = item("supporting", DocumentSemanticItemKind.Class, "Approval Record")
        val payment = item("payment", DocumentSemanticItemKind.Class, "Payment")
        val property = item("approval", DocumentSemanticItemKind.ObjectProperty, "has approval record")
        val domain = item(
            "approval-domain",
            DocumentSemanticItemKind.ObjectPropertyDomain,
            "Approval domain",
            refs(
                DocumentSemanticReferenceRole.Property to property.id,
                DocumentSemanticReferenceRole.Domain to payment.id,
            ),
        )
        val range = item(
            "approval-range",
            DocumentSemanticItemKind.ObjectPropertyRange,
            "Approval range",
            refs(
                DocumentSemanticReferenceRole.Property to property.id,
                DocumentSemanticReferenceRole.Range to supporting.id,
            ),
        )

        val result = compile(supporting, payment, property, domain, range)

        assertEquals(DocumentCompilationStatus.Compiled, result.status)
        assertTrue(result.references.any { it.semanticItemId == supporting.id })
        assertTrue(result.references.none { it.finalIri.value.contains("Unrelated") })
    }

    @Test
    fun `orders declarations before use and records exact dependencies`(): Unit {
        val parent = item("parent", DocumentSemanticItemKind.Class, "Parent")
        val child = item("child", DocumentSemanticItemKind.Class, "Child")
        val hierarchy = item(
            "hierarchy",
            DocumentSemanticItemKind.SubclassRelationship,
            "Child is a Parent",
            refs(
                DocumentSemanticReferenceRole.Subclass to child.id,
                DocumentSemanticReferenceRole.Superclass to parent.id,
            ),
        )

        val operations = compile(parent, child, hierarchy).operations
        val hierarchyOperation = operations.single { it.kind == DocumentPlanOperationKind.AddSuperclass }
        val declarationIds = operations.filter { it.kind == DocumentPlanOperationKind.CreateClass }.map { it.id }.sorted()

        assertEquals(declarationIds, hierarchyOperation.dependsOnOperationIds)
        assertTrue(operations.indexOf(hierarchyOperation) > operations.indexOfFirst {
            it.id == declarationIds.last()
        })
    }

    @Test
    fun `splits only independent dependency closures and blocks an oversized atomic closure`(): Unit {
        val independent = (1..21).map { index ->
            item("class-$index", DocumentSemanticItemKind.Class, "Independent $index")
        }
        val split = compiler.compile(plan(independent, DocumentSemanticOutcome.Executable), context())

        assertEquals(2, split.size)
        assertEquals(listOf(20, 1), split.map { it.expandedTypedEditCount })
        assertTrue(split.all { it.sourceGroupId == "group-1" })

        val classes = (1..11).map { index ->
            item("chain-class-$index", DocumentSemanticItemKind.Class, "Chain $index")
        }
        val relationships = classes.zipWithNext().mapIndexed { index, (child, parent) ->
            item(
                "chain-link-$index",
                DocumentSemanticItemKind.SubclassRelationship,
                "Chain link $index",
                refs(
                    DocumentSemanticReferenceRole.Subclass to child.id,
                    DocumentSemanticReferenceRole.Superclass to parent.id,
                ),
            )
        }
        val blocked = compiler.compile(
            plan(classes + relationships, DocumentSemanticOutcome.Executable),
            context(),
        ).single()

        assertEquals(DocumentCompilationStatus.Blocked, blocked.status)
        assertEquals("atomic-group-exceeds-limit", blocked.failures.single().safeCode)
    }

    @Test
    fun `blocks stale fingerprints and duplicate current work while compiling large tasks`(): Unit {
        val customer = item("customer", DocumentSemanticItemKind.Class, "Customer")
        val staleOntology = compile(
            customer,
            context = context(
                expectedOntologyFingerprint = "expected",
                currentOntologyFingerprint = "changed",
            ),
        )
        assertEquals("stale-ontology", staleOntology.failures.single().safeCode)

        val staleWork = compile(
            customer,
            context = context(
                expectedCurrentWorkFingerprint = "expected",
                currentWorkFingerprint = "changed",
            ),
        )
        assertEquals("stale-current-work", staleWork.failures.single().safeCode)

        val baseline = compile(customer)
        val duplicateKey = operationKey(baseline.operations.single())
        val duplicate = compile(
            customer,
            context = context(existingOperationKeys = setOf(duplicateKey)),
        )
        assertEquals("duplicate-or-no-op", duplicate.failures.single().safeCode)

        val items = (1..101).map { index ->
            item("task-class-$index", DocumentSemanticItemKind.Class, "Task Class $index")
        }
        val groups = items.chunked(20).mapIndexed { index, groupItems ->
            group("group-${index.toString().padStart(2, '0')}", groupItems)
        }
        val taskPlan = DocumentSemanticPlan(
            DocumentAnalysisWorkKey("b".repeat(64)),
            listOf("discovery-1"),
            emptyList(),
            items.sortedBy(DocumentSemanticPlanItem::stableOrderingKey),
            groups.sortedBy(DocumentSemanticRecommendationGroup::stableOrderingKey),
        )
        val compiled = compiler.compile(taskPlan, context())

        assertEquals(6, compiled.size)
        assertTrue(compiled.all { it.status == DocumentCompilationStatus.Compiled })
        assertEquals(101, compiled.sumOf { it.expandedTypedEditCount })
        assertTrue(compiled.all { it.expandedTypedEditCount <= 20 })
    }

    private val compiler = DocumentSemanticPlanCompiler()
    private val evidenceId = DocumentEvidenceId("evidence-1")

    private fun compile(
        vararg items: DocumentSemanticPlanItem,
        outcome: DocumentSemanticOutcome = DocumentSemanticOutcome.Executable,
        context: DocumentSemanticCompilerContext = context(),
    ) = compiler.compile(plan(items.toList(), outcome), context).single()

    private fun plan(
        items: List<DocumentSemanticPlanItem>,
        outcome: DocumentSemanticOutcome,
    ): DocumentSemanticPlan {
        val orderedItems = items.sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
        val group = group("group-1", items, outcome)
        return DocumentSemanticPlan(
            DocumentAnalysisWorkKey("a".repeat(64)),
            listOf("discovery-1"),
            emptyList(),
            orderedItems,
            listOf(group),
        )
    }

    private fun group(
        id: String,
        items: List<DocumentSemanticPlanItem>,
        outcome: DocumentSemanticOutcome = DocumentSemanticOutcome.Executable,
    ): DocumentSemanticRecommendationGroup =
        DocumentSemanticRecommendationGroup(
            id = id,
            title = "Compile verified meaning",
            description = "Compile the connected semantic model.",
            itemIds = items.map(DocumentSemanticPlanItem::id).sorted(),
            discoveryIds = listOf("discovery-1"),
            evidenceIds = listOf(evidenceId),
            outcome = outcome,
            rationale = "The verified evidence supports this group.",
            confidence = DocumentConfidenceDimensions(90, 85, 80),
        )

    private fun item(
        id: String,
        kind: DocumentSemanticItemKind,
        label: String,
        references: List<DocumentSemanticReference> = emptyList(),
        literal: RdfLiteral? = null,
        datatypeIntent: String? = null,
        outcome: DocumentSemanticOutcome = DocumentSemanticOutcome.Executable,
        modelRecommended: Boolean = false,
        reviewerInputRequired: Boolean = false,
    ): DocumentSemanticPlanItem = DocumentSemanticPlanItem(
        id = id,
        kind = kind,
        label = label,
        definition = "Verified meaning for $label.",
        literalValue = literal,
        datatypeIntent = datatypeIntent,
        references = references,
        discoveryIds = listOf("discovery-1"),
        evidenceIds = listOf(evidenceId),
        rationale = "The evidence supports this semantic item.",
        outcome = outcome,
        confidence = DocumentConfidenceDimensions(90, 85, 80),
        modelRecommended = modelRecommended,
        reviewerInputRequired = reviewerInputRequired,
    )

    private fun refs(
        vararg roles: Pair<DocumentSemanticReferenceRole, String>,
        alignmentTargets: Boolean = false,
    ): List<DocumentSemanticReference> = roles.map { (role, id) ->
        DocumentSemanticReference(
            role,
            if (alignmentTargets) {
                DocumentSemanticReferenceTarget.Alignment(id)
            } else {
                DocumentSemanticReferenceTarget.SemanticItem(id)
            },
        )
    }.sortedBy(DocumentSemanticReference::stableOrderingKey)

    private fun context(
        existingEntities: Map<Iri, DocumentTemporaryReferenceKind> = emptyMap(),
        aligned: Map<String, DocumentCompilerEntity> = emptyMap(),
        itemAlignments: Map<String, String> = emptyMap(),
        administrativeDiscoveryIds: Set<String> = emptySet(),
        expectedOntologyFingerprint: String = "current",
        currentOntologyFingerprint: String = expectedOntologyFingerprint,
        expectedCurrentWorkFingerprint: String = "current",
        currentWorkFingerprint: String = expectedCurrentWorkFingerprint,
        existingOperationKeys: Set<String> = emptySet(),
    ): DocumentSemanticCompilerContext = DocumentSemanticCompilerContext(
        targetSourceId = "simple",
        iriNamespace = "https://example.com",
        existingEntities = existingEntities,
        alignedEntities = aligned,
        itemAlignmentIds = itemAlignments,
        administrativeDiscoveryIds = administrativeDiscoveryIds,
        expectedOntologyFingerprint = expectedOntologyFingerprint,
        currentOntologyFingerprint = currentOntologyFingerprint,
        expectedCurrentWorkFingerprint = expectedCurrentWorkFingerprint,
        currentWorkFingerprint = currentWorkFingerprint,
        existingOperationKeys = existingOperationKeys,
    )

    private fun operationKey(operation: com.entio.core.DocumentPlanOperation): String =
        "${operation.kind.name}:${operation.declaration?.value.orEmpty()}:" +
            operation.operands.filterNot { it is com.entio.core.DocumentPlanOperand.SourceId }.joinToString("|")
}
