package com.entio.validation

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.RdfLiteral
import com.entio.core.ResolvedOntologySource
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.semantic.ProposalCreator
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProposalValidatorTest {
    private val proposalCreator = ProposalCreator()
    private val validator = ProposalValidator()

    @Test
    fun returnsValidReportForValidProposal(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.ok)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun emptyChangeSetsRemainRejectedByCoreContract(): Unit {
        assertFailsWith<IllegalArgumentException> {
            ChangeSet(changes = emptyList())
        }
    }

    @Test
    fun duplicateAdditionReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val existingTriple = typeTriple("Customer")
        val project = project(source = source, graph = GraphState(setOf(existingTriple)))
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                changes = listOf(GraphChange(GraphChangeKind.Addition, existingTriple)),
            ),
            allowPreviewFailure = true,
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertFalse(report.ok)
        assertEquals("duplicate-triple-addition", report.issues.single().code)
        assertEquals("changeSet.changes[0]", report.issues.single().source)
    }

    @Test
    fun missingRemovalReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                changes = listOf(GraphChange(GraphChangeKind.Removal, labelTriple("Customer", "Customer"))),
            ),
            allowPreviewFailure = true,
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-triple-removal", report.issues.single().code)
        assertEquals("changeSet.changes[0]", report.issues.single().source)
    }

    @Test
    fun staleProposalReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)
        source.path.writeText("@prefix ex: <https://example.com/> .\nex:Account a ex:Class .\n")

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("stale-proposal-baseline", report.issues.single().code)
        assertEquals(proposal.id, report.issues.single().source)
    }

    @Test
    fun missingTargetSourceReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project).copy(targetSourceId = "missing")

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals(
            listOf("missing-target-source"),
            report.issues.map { issue -> issue.code },
        )
    }

    @Test
    fun missingPreviewReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project).copy(preview = null)

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-proposal-preview", report.issues.single().code)
        assertEquals(proposal.id, report.issues.single().source)
    }

    @Test
    fun semanticEquivalenceFailureReturnsDeterministicIssueWhenAvailable(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)

        val report = validator.validateProposal(
            proposal = proposal,
            currentProject = project,
            semanticEquivalenceResult = SemanticEquivalenceResult.NotEquivalent("Preview graph changed after round trip."),
        )

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("semantic-equivalence-failed", report.issues.single().code)
        assertEquals("Preview graph changed after round trip.", report.issues.single().message)
    }

    @Test
    fun verificationFailedStatusReturnsDeterministicIssueWhenAvailable(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project).copy(status = ChangeProposalStatus.VerificationFailed)

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("proposal-verification-failed", report.issues.single().code)
    }

    @Test
    fun combinesExistingProjectValidationIssues(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)
        val projectReport = ValidationReport(
            status = ValidationStatus.Invalid,
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "invalid-turtle",
                    message = "Ontology source could not be parsed as Turtle.",
                    source = "simple",
                ),
            ),
        )

        val report = validator.validateProposal(
            proposal = proposal,
            currentProject = project,
            projectValidationReport = projectReport,
        )

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("invalid-turtle", report.issues.single().code)
    }

    @Test
    fun returnsIssuesInStableSortedOrder(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)
            .copy(preview = null, status = ChangeProposalStatus.VerificationFailed)

        val first = validator.validateProposal(proposal, project)
        val second = validator.validateProposal(proposal, project)

        assertEquals(first.issues, second.issues)
        assertEquals(
            listOf("missing-proposal-preview", "proposal-verification-failed"),
            first.issues.map { issue -> issue.code },
        )
    }

    @Test
    fun missingReferencedResourceIsRejectedForObjectAssertion(): Unit {
        val source = tempSource()
        val customer = iri("Customer")
        val ownsAccount = iri("ownsAccount")
        val graph = GraphState(
            setOf(
                typeTriple("Customer"),
                propertyTypeTriple(ownsAccount, OWL_OBJECT_PROPERTY),
            ),
        )
        val project = project(source = source, graph = graph)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(
                        GraphChangeKind.Addition,
                        GraphTriple(customer, ownsAccount, iri("missing-account")),
                    ),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-referenced-resource", report.issues.single().code)
    }

    @Test
    fun objectPropertyCannotReceiveDatatypeAssertion(): Unit {
        val source = tempSource()
        val property = iri("ownsAccount")
        val graph = GraphState(
            setOf(
                typeTriple("Customer"),
                propertyTypeTriple(property, OWL_OBJECT_PROPERTY),
            ),
        )
        val project = project(source = source, graph = graph)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(
                        GraphChangeKind.Addition,
                        GraphTriple(iri("Customer"), property, RdfLiteral("not-a-resource")),
                    ),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertTrue(report.issues.any { issue -> issue.code == "incompatible-property-kind" })
    }

    @Test
    fun invalidTypedLiteralIsRejectedAgainstKnownDatatypeRange(): Unit {
        val source = tempSource()
        val property = iri("accountNumber")
        val graph = GraphState(
            setOf(
                typeTriple("Customer"),
                propertyTypeTriple(property, OWL_DATATYPE_PROPERTY),
                GraphTriple(property, RDFS_RANGE, XSD_INTEGER),
            ),
        )
        val project = project(source = source, graph = graph)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(
                        GraphChangeKind.Addition,
                        GraphTriple(
                            iri("Customer"),
                            property,
                            RdfLiteral("not-an-integer", datatypeIri = XSD_INTEGER),
                        ),
                    ),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertTrue(report.issues.any { issue -> issue.code == "invalid-literal" })
    }

    @Test
    fun knownDomainAndRangeMustMatchExplicitResourceTypes(): Unit {
        val source = tempSource()
        val property = iri("ownsAccount")
        val graph = GraphState(
            setOf(
                typeTriple("Customer"),
                typeTriple("Account"),
                propertyTypeTriple(property, OWL_OBJECT_PROPERTY),
                GraphTriple(property, RDFS_DOMAIN, iri("Account")),
                GraphTriple(property, RDFS_RANGE, iri("Account")),
            ),
        )
        val project = project(source = source, graph = graph)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(
                        GraphChangeKind.Addition,
                        GraphTriple(iri("Customer"), property, iri("Customer")),
                    ),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertTrue(report.issues.any { issue -> issue.code == "incompatible-property-domain" })
        val rangeIssue = report.issues.single { issue -> issue.code == "incompatible-property-range" }
        assertEquals(
            "Object 'https://example.com/Customer' is not an instance of the declared range 'https://example.com/Account' for property 'https://example.com/ownsAccount'.",
            rangeIssue.message,
        )
    }

    @Test
    fun genericRdfPropertyCanReceiveClassDomainAndRange(): Unit {
        val source = tempSource()
        val property = iri("ownsAccount")
        val account = iri("Account")
        val customer = iri("Customer")
        val graph = GraphState(
            setOf(
                typeTriple("Account"),
                typeTriple("Customer"),
                propertyTypeTriple(property, RDF_PROPERTY),
            ),
        )
        val project = project(source = source, graph = graph)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(GraphChangeKind.Addition, GraphTriple(property, RDFS_DOMAIN, account)),
                    GraphChange(GraphChangeKind.Addition, GraphTriple(property, RDFS_RANGE, customer)),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Valid, report.status)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun shaclVocabularyPredicatesAreAcceptedAsStructuralTriples(): Unit {
        val source = tempSource()
        val shape = iri("CustomerShape")
        val customer = iri("Customer")
        val shTargetClass = Iri("http://www.w3.org/ns/shacl#targetClass")
        val graph = GraphState(
            setOf(
                typeTriple("Customer"),
                GraphTriple(shape, Iri(RDF_TYPE), Iri("http://www.w3.org/ns/shacl#NodeShape")),
                GraphTriple(shape, shTargetClass, customer),
            ),
        )
        val project = project(source = source, graph = graph)
        val replacement = iri("FiboCustomer")
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(GraphChangeKind.Addition, GraphTriple(shape, shTargetClass, replacement)),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.issues.none { issue -> issue.code == "missing-referenced-resource" })
    }

    @Test
    fun ontologyImportsMayReferenceExternalOntologyWithoutLocalDeclarations(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(
                        GraphChangeKind.Addition,
                        GraphTriple(
                            subject = Iri("https://example.com/entio/simple#"),
                            predicate = Iri(OWL_IMPORTS),
                            objectTerm = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/"),
                        ),
                    ),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.issues.none { issue -> issue.code == "missing-referenced-resource" })
    }

    @Test
    fun validatesNewPropertyMetadataAndDatatypeRangeAgainstCompleteDraft(): Unit {
        val source = tempSource()
        val loan = iri("Loan")
        val loanAmount = iri("loanAmount")
        val changes = ChangeSet(
            listOf(
                GraphChange(GraphChangeKind.Addition, GraphTriple(loan, Iri(RDF_TYPE), OWL_CLASS)),
                GraphChange(GraphChangeKind.Addition, GraphTriple(loanAmount, Iri(RDF_TYPE), OWL_DATATYPE_PROPERTY)),
                GraphChange(GraphChangeKind.Addition, GraphTriple(loanAmount, RDFS_DOMAIN, loan)),
                GraphChange(GraphChangeKind.Addition, GraphTriple(loanAmount, RDFS_RANGE, XSD_DECIMAL)),
                GraphChange(
                    GraphChangeKind.Addition,
                    GraphTriple(loanAmount, Iri("http://www.w3.org/2004/02/skos/core#definition"), RdfLiteral("The amount borrowed under a loan.")),
                ),
            ),
        )
        val project = project(source = source)
        val report = validator.validateProposal(proposal(project = project, changeSet = changes), project)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.issues.none { issue -> issue.code == "missing-semantic-target" })
        assertTrue(report.issues.none { issue -> issue.code == "missing-referenced-resource" })
    }

    @Test
    fun selfSubclassIsRejected(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val customer = iri("Customer")
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                listOf(
                    GraphChange(
                        GraphChangeKind.Addition,
                        GraphTriple(customer, RDFS_SUBCLASS_OF, customer),
                    ),
                ),
            ),
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("self-subclass", report.issues.single().code)
    }

    private fun proposal(
        project: EntioProject,
        changeSet: ChangeSet = ChangeSet(
            changes = listOf(GraphChange(GraphChangeKind.Addition, labelTriple("Customer", "Customer"))),
        ),
        allowPreviewFailure: Boolean = false,
    ): ChangeProposal {
        val result = proposalCreator.createProposal(
            project = project,
            targetSourceId = "simple",
            changeSet = changeSet,
            id = "proposal-1",
            title = "Add customer label",
        )

        return when (result) {
            is EntioResult.Success -> result.value
            is EntioResult.Failure -> if (allowPreviewFailure) {
                proposal(project).copy(changeSet = changeSet)
            } else {
                error(result.message)
            }
        }
    }

    private fun project(
        source: ResolvedOntologySource,
        graph: GraphState = GraphState(setOf(typeTriple("Customer"))),
    ): EntioProject =
        EntioProject(
            config = EntioProjectConfig(
                name = "simple",
                ontologySources = listOf(
                    OntologySourceReference(
                        id = source.id,
                        path = source.path.fileName.toString(),
                        format = OntologyFormat.Turtle,
                    ),
                ),
            ),
            resolvedSources = listOf(source),
            ontologies = emptyList(),
            symbols = emptyList(),
            graph = graph,
        )

    private fun tempSource(
        id: String = "simple",
        content: String = "@prefix ex: <https://example.com/> .\nex:Customer a ex:Class .\n",
    ): ResolvedOntologySource {
        val directory = Files.createTempDirectory("entio-proposal-validation")
        val path = directory.resolve("$id.ttl")
        path.writeText(content)
        return ResolvedOntologySource(
            id = id,
            path = path,
            format = OntologyFormat.Turtle,
        )
    }

    private fun typeTriple(localName: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            objectTerm = Iri("http://www.w3.org/2000/01/rdf-schema#Class"),
        )

    private fun propertyTypeTriple(property: Iri, type: Iri): GraphTriple =
        GraphTriple(
            subject = property,
            predicate = Iri(RDF_TYPE),
            objectTerm = type,
        )

    private fun iri(localName: String): Iri = Iri("https://example.com/$localName")

    private fun labelTriple(localName: String, label: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectTerm = RdfLiteral(label),
        )

    private companion object {
        private const val RDF_TYPE: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val OWL_IMPORTS: String = "http://www.w3.org/2002/07/owl#imports"
        private val RDF_PROPERTY: Iri = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property")
        private val RDFS_SUBCLASS_OF: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val RDFS_DOMAIN: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val OWL_OBJECT_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val OWL_DATATYPE_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#DatatypeProperty")
        private val OWL_CLASS: Iri = Iri("http://www.w3.org/2002/07/owl#Class")
        private val XSD_INTEGER: Iri = Iri("http://www.w3.org/2001/XMLSchema#integer")
        private val XSD_DECIMAL: Iri = Iri("http://www.w3.org/2001/XMLSchema#decimal")
    }
}
