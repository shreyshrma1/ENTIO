package com.entio.web.ai

import com.entio.web.StagingWorkflowService
import com.entio.web.ReadOnlyProjectAdapter
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebPermission
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AiConversationServiceTest {
    private val now: Instant = Instant.parse("2026-07-17T14:00:00Z")

    @Test
    fun intentClassifierRecognizesNaturalDefinitionAndHierarchyEdits(): Unit {
        val classifier = AiIntentClassifier()

        listOf(
            "Add a clearer ontology definition to Account.",
            "Write a concise ontology definition for Invoice.",
            "Actually, make the Account definition more precise.",
            "Add the alternate label Current Account to Checking Account.",
            "Create class Savings Account as subclass of Account.",
        ).forEach { prompt ->
            assertEquals(AiConversationIntent.SEMANTIC_REQUEST, classifier.classify(prompt).intent, prompt)
        }
    }

    @Test
    fun intentClassifierRequiresAPlanForOntologyWideDefinitionReview(): Unit {
        val result = AiIntentClassifier().classify(
            "Review all class definitions for consistency and propose improvements across the ontology.",
        )

        assertEquals(AiConversationIntent.SEMANTIC_REQUEST, result.intent)
    }

    @Test
    fun focusedRequestPreparesPrivateDraftAndReturnsToolOutputInCallOrder(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-1", "Receivable Account")), responseId = "response-1"),
            completed(calls = listOf(createClassCall("call-2", "Payable Account")), responseId = "response-2"),
            completed(text = "I prepared two typed class edits for review.", responseId = "response-3"),
        )
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Receivable Account and then create class Payable Account."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status)
        assertEquals("I prepared two typed class edits for review.", result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        assertEquals(listOf("Receivable Account", "Payable Account"), draft.items.map { (it.operation as AiTypedDraftOperation).request.label })
        assertEquals(listOf("call-1"), provider.requests[1].toolOutputs.map(OpenAiToolOutput::callId))
        assertEquals(listOf("call-2"), provider.requests[2].toolOutputs.map(OpenAiToolOutput::callId))
        assertEquals(listOf("call-1"), provider.requests[1].functionCalls.map(OpenAiFunctionCall::callId))
        assertEquals(listOf("call-2"), provider.requests[2].functionCalls.map(OpenAiFunctionCall::callId))
        assertTrue(provider.requests.all { it.userInput.contains("Create class Receivable Account") })
        assertTrue(provider.requests.all { it.previousResponseId == null })
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun definitionRequestCreatesAReviewablePrivateDraftWithoutMutatingSharedStaging(): Unit = runBlocking {
        val definition = "An account is a record used to organize and track financial activity for a party."
        val provider = FakeToolProvider(
            completed(calls = listOf(addDefinitionCall("definition-1", "ontology", "Account", definition))),
            completed(text = "I drafted a definition for Account for your review."),
        )
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Add a definition for the account class that makes sense"),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        assertEquals("I drafted a definition for Account for your review.", result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        val request = (draft.items.single().operation as AiTypedDraftOperation).request
        assertEquals("add-definition", request.editType)
        assertEquals("Account", request.targetLabel)
        assertEquals(definition, request.value)
        assertTrue(provider.requests.first().trustedPolicy.contains("exact allowed source IDs when calling tools: simple, shapes"))
        assertTrue(provider.requests.first().trustedPolicy.contains("proactively inspect relevant local entities"))
        assertTrue(provider.requests.first().trustedPolicy.contains("infer a plausible domain and draft wording"))
        assertTrue(provider.requests.first().trustedPolicy.contains("ontological, entity-centered perspective"))
        assertTrue(provider.requests.first().trustedPolicy.contains("never loop over an already covered target"))
        assertTrue(provider.requests.first().trustedPolicy.contains("follow every relevant hierarchy node whose childCount is greater than zero"))
        assertTrue(provider.requests.first().trustedPolicy.contains("separate asserted relationships from cautious domain inference"))
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun alternateLabelRequestUsesTheSamePrivateTypedDraftPath(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(
                calls = listOf(
                    simpleCall(
                        "alternate-label",
                        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                        """{"sourceId":"simple","editType":"add-alternate-label","rationale":"Use the common banking term.","targetLabel":"Customer","value":"Client"}""",
                    ),
                ),
            ),
            completed(text = "I staged the alternate label for Customer for human review."),
        )
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Add the alternate label Client to Customer."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        val request = (draft.items.single().operation as AiTypedDraftOperation).request
        assertEquals("add-alternate-label", request.editType)
        assertEquals("Customer", request.targetLabel)
        assertEquals("Client", request.value)
        assertTrue(request.aiGenerated)
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun pluralDefinitionRequestStagesOneOrdinaryTypedEditForEveryClass(): Unit = runBlocking {
        val definitions = linkedMapOf(
            "Account" to "A record used to organize financial activity for a party.",
            "Checking Account" to "An account that supports deposits, withdrawals, and payment transactions.",
            "Customer" to "A person or organization that receives services from the institution.",
            "Invoice" to "A document stating amounts owed for supplied goods or services.",
        )
        val responses = definitions.map { (label, definition) ->
            completed(calls = listOf(addDefinitionCall("definition-${label.lowercase().replace(' ', '-')}", "simple", label, definition)))
        } + completed(text = "I staged definitions for all four classes for your review.")
        val provider = FakeToolProvider(*responses.toTypedArray())
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Write definitions for all classes in this ontology"),
        )

        assertEquals(AiConversationIntent.SEMANTIC_REQUEST, result.intent)
        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        val requests = draft.items.map { (it.operation as AiTypedDraftOperation).request }
        assertEquals(definitions.keys.toList(), requests.map { it.targetLabel })
        assertEquals(definitions.values.toList(), requests.map { it.value })
        assertTrue(requests.all { it.editType == "add-definition" && it.aiGenerated })
        assertTrue(provider.requests.first().trustedPolicy.contains("use entio_draft_add_definition"))
        assertTrue(provider.requests.first().trustedPolicy.contains("always supply the exact targetLabel"))
        assertTrue(provider.requests.first().trustedPolicy.contains("leave all edits unapproved for human review"))
        definitions.keys.toList().dropLast(1).forEachIndexed { index, handledLabel ->
            val output = provider.requests[index + 1].toolOutputs.single().output
            assertTrue(output.contains("\"targetLabel\":\"$handledLabel\""), output)
            assertTrue(output.contains("\"items\""), output)
        }
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun repeatedAllClassDefinitionTargetIsRejectedWithoutCreatingAnotherRevision(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(
                calls = listOf(
                    addDefinitionCall("definition-account", "simple", "Account", "A financial record of a customer's balance and transactions."),
                    addDefinitionCall("definition-account-repeat", "simple", "Account", "A second wording that must not replace the first during an all-class request."),
                    addDefinitionCall("definition-checking", "simple", "Checking Account", "An account that supports frequent deposits, withdrawals, and payments."),
                    addDefinitionCall("definition-customer", "simple", "Customer", "A person or organization that receives financial services."),
                    addDefinitionCall("definition-invoice", "simple", "Invoice", "A document stating amounts owed for supplied goods or services."),
                ),
            ),
            completed(text = "I staged definitions for all four classes for your review."),
        )
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Write definitions for all classes in this ontology"),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        assertEquals(listOf("Account", "Checking Account", "Customer", "Invoice"), draft.items.map {
            (it.operation as AiTypedDraftOperation).request.targetLabel
        })
        assertEquals(4, draft.revisions.size)
        assertTrue(provider.requests[1].toolOutputs[1].output.contains("definition-target-already-covered"))
    }

    @Test
    fun objectAndPropertyDefinitionRequestStagesWorkbenchObjectsAndProperties(): Unit = runBlocking {
        val targets = linkedMapOf(
            "Invoice 20874" to "A numbered invoice record representing a bill issued for goods or services.",
            "Shrey" to "A customer individual who owns accounts in the example ontology.",
            "Account 101" to "A numbered account individual with an opening date and recorded activity.",
            "owns account" to "An object property relating a customer or checking account to an account it owns.",
            "date opened" to "A datatype property recording the date on which an account was opened.",
        )
        val provider = FakeToolProvider(
            completed(
                calls = targets.map { (label, definition) ->
                    addDefinitionCall("definition-${label.replace(' ', '-').lowercase()}", "simple", label, definition)
                },
            ),
            completed(text = "I staged definitions for Account 101 and Shrey."),
        )
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Please add definitions to all objects and properties"),
        )

        assertEquals(AiConversationIntent.SEMANTIC_REQUEST, result.intent)
        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        assertTrue(result.answer.contains("**Invoice 20874**"), result.answer)
        assertTrue(result.answer.contains("**Shrey**"), result.answer)
        assertTrue(result.answer.contains("**Account 101**"), result.answer)
        assertTrue(result.answer.contains("**owns account**"), result.answer)
        assertTrue(result.answer.contains("**date opened**"), result.answer)
        targets.forEach { (label, definition) ->
            assertTrue(result.answer.contains("**$label**: $definition"), result.answer)
        }
        assertFalse(result.answer == "I staged definitions for Account 101 and Shrey.")
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        val requests = draft.items.map { (it.operation as AiTypedDraftOperation).request }
        assertEquals(targets.keys.toList(), requests.map { it.targetLabel })
        assertTrue(requests.none { it.targetLabel == "Account" || it.targetLabel == "Checking Account" || it.targetLabel == "Customer" })
        assertTrue(requests.all { it.editType == "add-definition" && it.aiGenerated })
    }

    @Test
    fun objectAndPropertyDefinitionRequestRejectsClassDriftAndRecoversFromMalformedToolArguments(): Unit = runBlocking {
        val correctTargets = linkedMapOf(
            "Invoice 20874" to "A numbered invoice record representing a bill issued for goods or services.",
            "Shrey" to "A customer individual who owns accounts in the example ontology.",
            "Account 101" to "A numbered account individual with an opening date and recorded activity.",
            "owns account" to "An object property relating a customer or checking account to an account it owns.",
            "date opened" to "A datatype property recording the date on which an account was opened.",
        )
        val provider = FakeToolProvider(
            completed(
                calls = listOf(
                    addDefinitionCall("wrong-account", "simple", "Account", "A financial account."),
                    addDefinitionCall("wrong-checking", "simple", "Checking Account", "A checking account."),
                    addDefinitionCall("wrong-customer", "simple", "Customer", "A customer."),
                    addDefinitionCall("wrong-invoice", "simple", "Invoice", "An invoice."),
                ),
            ),
            completed(
                calls = listOf(
                    simpleCall(
                        "malformed-definition",
                        AiTypedEditCapabilityAdapter.ADD_DEFINITION_CAPABILITY,
                        """{"sourceId":"simple","entityIri":"https://example.com/entio/simple#Shrey","value":"A customer individual.","rationale":"Define the object."}""",
                    ),
                ),
            ),
            completed(
                calls = correctTargets.map { (label, definition) ->
                    addDefinitionCall("correct-${label.replace(' ', '-').lowercase()}", "simple", label, definition)
                },
            ),
            completed(text = "I staged definitions for all requested objects and properties for your review."),
        )
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Write definitions for all objects and properties"),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        assertEquals(correctTargets.size, result.run.draftEditCount)
        assertEquals(correctTargets.keys.toList(), draft.items.map { (it.operation as AiTypedDraftOperation).request.targetLabel })
        assertTrue(draft.items.none { (it.operation as AiTypedDraftOperation).request.targetLabel in setOf("Account", "Checking Account", "Customer", "Invoice") })
        assertTrue(provider.requests[1].toolOutputs.any { it.output.contains("definition-target-not-requested") })
        assertTrue(provider.requests[2].toolOutputs.single().output.contains("unknown-argument"))
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun broadLoanModelingRequestStagesEveryNamedWorkbenchEditFamily(): Unit = runBlocking {
        val loanIri = "https://example.com/entio/simple#Loan"
        val provider = FakeToolProvider(
            completed(
                calls = listOf(
                    simpleCall(
                        "loan-class",
                        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                        """{"sourceId":"simple","editType":"create-class","rationale":"Add the Loan concept.","label":"Loan"}""",
                    ),
                    simpleCall(
                        "loan-object-property",
                        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                        """{"sourceId":"simple","editType":"create-object-property","rationale":"Relate a loan to its borrower.","label":"has borrower"}""",
                    ),
                    simpleCall(
                        "loan-datatype-property",
                        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                        """{"sourceId":"simple","editType":"create-datatype-property","rationale":"Record the loan amount.","label":"loan amount","datatypeIri":"http://www.w3.org/2001/XMLSchema#decimal"}""",
                    ),
                ),
            ),
            completed(
                calls = listOf(
                    simpleCall(
                        "loan-individual",
                        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                        """{"sourceId":"simple","editType":"create-individual","rationale":"Add an example loan instance.","individualLabel":"Loan Example","classIri":"$loanIri"}""",
                    ),
                    simpleCall(
                        "loan-assertion",
                        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                        """{"sourceId":"simple","editType":"add-object-property-assertion","rationale":"Connect the example customer to an account.","subjectLabel":"Shrey","propertyLabel":"owns account","objectLabel":"Account 101"}""",
                    ),
                    simpleCall(
                        "loan-shacl",
                        AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY,
                        """{"sourceId":"shapes","editType":"shacl-create-property-shape","rationale":"Require an account relationship.","shapeLabel":"Loan Account Shape","targetClassLabel":"Account","pathLabel":"owns account","constraintKind":"min-count","constraintValue":"1","validationMessage":"A loan must identify an account."}""",
                    ),
                ),
            ),
            completed(text = "I staged the Loan classes, properties, example entity, assertion, and SHACL constraint for human review."),
        )
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Model a Loan with classes, properties, example entities, assertions, and SHACL constraints."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        val requests = draft.items.map { (it.operation as AiTypedDraftOperation).request }
        assertEquals(
            setOf(
                "create-class",
                "create-object-property",
                "create-datatype-property",
                "create-individual",
                "add-object-property-assertion",
                "shacl-create-property-shape",
            ),
            requests.map { it.editType }.toSet(),
        )
        assertTrue(requests.all { it.aiGenerated })
        assertTrue(provider.requests.first().trustedPolicy.contains("entio_draft_add_ontology_edit"))
        assertTrue(provider.requests.first().trustedPolicy.contains("entio_draft_add_shacl_edit"))
        assertTrue(provider.requests.first().trustedPolicy.contains("dependent edits must be sequenced"))
        assertTrue(provider.requests[1].toolOutputs.any { it.output.contains(loanIri) })
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun allClassDefinitionRequestCannotCompleteWhileInvoiceIsMissing(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(addDefinitionCall("definition-account", "simple", "Account", "A financial record of a customer's balance and transactions."))),
            completed(calls = listOf(addDefinitionCall("definition-checking", "simple", "Checking Account", "An account that supports frequent deposits, withdrawals, and payments."))),
            completed(calls = listOf(addDefinitionCall("definition-customer", "simple", "Customer", "A person or organization that receives financial services."))),
            completed(text = "I added definitions for all classes."),
            completed(calls = listOf(addDefinitionCall("definition-invoice", "simple", "Invoice", "A document stating amounts owed for supplied goods or services."))),
            completed(text = "I added definitions for all four classes."),
        )
        val fixture = fixture(provider, stripDefinitionsFromCopiedOntology = true)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Write definitions for all classes in this ontology"),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        assertTrue(result.answer.contains("**Invoice**"), result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        assertEquals(listOf("Account", "Checking Account", "Customer", "Invoice"), draft.items.map {
            (it.operation as AiTypedDraftOperation).request.targetLabel
        })
        assertTrue(provider.requests[4].userInput.contains("ENTIO_SERVER_COMPLETION_REQUIREMENT"))
        assertTrue(provider.requests[4].userInput.contains("Invoice"))
    }

    @Test
    fun createClassRequestRecoversFromWrongToolChoiceAndCannotCompleteWithoutTheClassEdit(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(addDefinitionCall("wrong-tool", "simple", "Savings Account", "A savings account."))),
            completed(text = "I created Savings Account."),
            completed(calls = listOf(createClassCall("create-savings", "Savings Account"))),
            completed(text = "I staged the Savings Account class for review."),
        )
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Savings Account."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        assertEquals(listOf("Savings Account"), draft.items.map { (it.operation as AiTypedDraftOperation).request.label })
        assertTrue(provider.requests[1].toolOutputs.single().output.contains("unknown-label"))
        assertTrue(provider.requests[2].userInput.contains("ENTIO_SERVER_COMPLETION_REQUIREMENT"))
        assertTrue(provider.requests[2].userInput.contains("create-class"))
    }

    @Test
    fun redundantAllDefinitionRequestCompletesWithoutDraftingDuplicateDefinitions(): Unit = runBlocking {
        val provider = FakeToolProvider()
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Write definitions for all classes in this ontology"),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status)
        assertTrue(result.answer.contains("already have asserted definitions"))
        assertNull(result.draftId)
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun alreadyAssertedSuperclassRequestCompletesAsANoOp(): Unit = runBlocking {
        val provider = FakeToolProvider()
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Add superclass Account to Checking Account."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status)
        assertTrue(result.answer.contains("already has Account as a direct superclass"))
        assertNull(result.draftId)
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun currentOntologyQuestionReceivesEntioProjectAndSourceContextWithoutRedundantClarification(): Unit = runBlocking {
        val provider = FakeToolProvider(completed(text = "The current ontology contains Account and related financial concepts."))
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Can you explain the classes in this ontology?"),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status)
        assertEquals(AiConversationIntent.SEMANTIC_REQUEST, result.intent)
        assertNull(result.draftId)
        assertTrue(provider.requests.single().trustedPolicy.contains("do not ask which ontology the user means"))
        assertTrue(provider.requests.single().trustedPolicy.contains("ontology-first workbench"))
        assertTrue(provider.requests.single().userInput.contains("ENTIO_CURRENT_CONTEXT_DATA"))
        assertTrue(provider.requests.single().userInput.contains("\"projectId\":\"simple\""))
        assertTrue(provider.requests.single().userInput.contains("\"id\":\"simple\""))
        assertTrue(provider.requests.single().userInput.contains("\"roles\":["))
        assertTrue(provider.requests.single().userInput.contains("\"label\":\"Checking Account\""))
        assertTrue(provider.requests.single().userInput.contains("\"predicateLabel\":\"owns account\""))
        assertTrue(provider.requests.single().trustedPolicy.contains("Use the bounded ontology overview"))
        assertTrue(provider.requests.single().trustedPolicy.contains("Minimize provider and capability calls"))
    }

    @Test
    fun runBindsOneModelAcrossToolLoopAndFutureRunUsesNewSelection(): Unit = runBlocking {
        val bindings = MutableBindingResolver("gpt-5.6-sol")
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("create-1", "Runtime Bound Account"))),
            completed(text = "First run complete."),
            completed(text = "Second run complete."),
            onRespond = { if (bindings.resolveCount == 1) bindings.modelId = "gpt-5.6-terra" },
        )
        val fixture = fixture(provider, modelBindings = bindings)

        fixture.service.send(fixture.scope, fixture.conversation.id, AiConversationTurnRequest("Create class Runtime Bound Account."))
        fixture.service.send(fixture.scope, fixture.conversation.id, AiConversationTurnRequest("Explain the current screen context again."))

        assertEquals(listOf("gpt-5.6-sol", "gpt-5.6-sol", "gpt-5.6-terra"), provider.selectedModelIds)
        assertEquals(listOf("gpt-5.6-sol", "gpt-5.6-terra"), fixture.audits.list("alice", "simple").map(AiAuditRecord::modelId))
        assertTrue(fixture.audits.list("alice", "simple").all { it.compatibilityState == com.entio.web.ai.models.AiModelCompatibilityState.AVAILABLE_AND_COMPATIBLE })
        assertTrue(fixture.audits.list("alice", "simple").all { it.catalogVersion == "catalog-test" && it.requestPolicyVersion == "request-test" })
    }

    @Test
    fun modelAccessLossFailsSafelyAndPreservesPrivateDraft(): Unit = runBlocking {
        val bindings = MutableBindingResolver("gpt-5.6-sol")
        val provider = FakeToolProvider(OpenAiResponsesResult.Failed(OpenAiProviderFailure(OpenAiFailureCode.MODEL_UNAVAILABLE, "Model unavailable.", false)))
        val fixture = fixture(provider, modelBindings = bindings)

        val result = fixture.service.send(fixture.scope, fixture.conversation.id, AiConversationTurnRequest("Create class Account."))

        assertEquals(AiRunStatus.FAILED, result.run.status)
        assertNotNull(result.draftId)
        assertNotNull(fixture.drafts.get("alice", "simple", fixture.conversation.id, result.draftId!!))
        assertEquals(1, bindings.unavailableCount)
        assertEquals(1, fixture.conversations.get("alice", "simple", fixture.conversation.id).messages.count { it.role == AiMessageRole.ASSISTANT })
    }

    @Test
    fun broadRequestIsSemanticallyPlannedAndStagedWithoutPhraseBasedConfirmation(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-1", "Loan"))),
            completed(text = "The confirmed plan now has one private draft edit."),
        )
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Design an ontology for a complete lending domain."),
        )

        assertEquals(AiConversationIntent.SEMANTIC_REQUEST, result.intent)
        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status)
        assertNull(result.plan)
        assertNotNull(result.draftId)
        assertEquals(1, fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId)).items.size)
    }

    @Test
    fun providerResolvesMaterialPropertyMeaningFromOntologyContext(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createObjectPropertyCall("call-1", "manages account"))),
            completed(text = "I prepared the object property."),
        )
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create property manages account."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        assertEquals(AiConversationIntent.SEMANTIC_REQUEST, result.intent)
        assertTrue(provider.requests.first().userInput.contains("Create property manages account."))
    }

    @Test
    fun followUpExplanationAndUndoPreserveConversationAndCurrentDraft(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-1", "Receivable Account"))),
            completed(text = "The class is in the private draft."),
            completed(text = "It remains private until explicit review submission.", responseId = "response-explain"),
            completed(calls = listOf(simpleCall("call-undo", "entio_draft_undo", """{"explanation":"Undo the class edit."}"""))),
            completed(text = "I undid the latest private draft revision."),
        )
        val fixture = fixture(provider)
        val first = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Receivable Account."),
        )

        val explanation = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Why is this change still private?"),
        )
        assertEquals(first.draftId, explanation.draftId)
        assertTrue(provider.requests[2].userInput.contains("The class is in the private draft."))

        val undone = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Undo the last proposed item."),
        )
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(undone.draftId))
        assertTrue(draft.items.isEmpty())
        assertEquals(listOf("add", "undo"), draft.revisions.map(AiDraftRevision::action))
        assertTrue(fixture.service.getConversation("alice", "simple", fixture.conversation.id).providerResponseIds.contains("response-explain"))
    }

    @Test
    fun draftRevisionPreservesConversationAndUpdatesTheExistingPrivateItem(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-add", "Receivable Account"))),
            completed(text = "The class is in the private draft."),
            completed(calls = listOf(updateClassCall("call-update", "draft-item-1", "Trade Receivable"))),
            completed(text = "I revised the existing private draft item."),
        )
        val fixture = fixture(provider)
        val initial = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Receivable Account."),
        )

        val revised = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Revise the proposed class label to Trade Receivable."),
        )

        assertEquals(initial.draftId, revised.draftId)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(revised.draftId))
        assertEquals(listOf("add", "update"), draft.revisions.map(AiDraftRevision::action))
        assertEquals("Trade Receivable", (draft.items.single().operation as AiTypedDraftOperation).request.label)
        assertTrue(provider.requests.last().userInput.contains("Receivable Account"))
    }

    @Test
    fun unknownDuplicateReplayedAndUnauthorizedCallsFailWithoutDuplicateMutation(): Unit = runBlocking {
        val unknown = fixture(FakeToolProvider(completed(calls = listOf(simpleCall("unknown-1", "shell_exec", "{}")))))
        val unknownResult = unknown.service.send(unknown.scope, unknown.conversation.id, AiConversationTurnRequest("Create class A."))
        assertEquals(AiRunStatus.FAILED, unknownResult.run.status)

        val duplicateProvider = FakeToolProvider(
            completed(calls = listOf(createClassCall("same", "A"), createClassCall("same", "B"))),
        )
        val duplicate = fixture(duplicateProvider)
        val duplicateResult = duplicate.service.send(duplicate.scope, duplicate.conversation.id, AiConversationTurnRequest("Create class A."))
        assertEquals(AiRunStatus.FAILED, duplicateResult.run.status)
        assertTrue(duplicate.drafts.list("alice", "simple", duplicate.conversation.id).single().items.isEmpty())

        val replayProvider = FakeToolProvider(
            completed(calls = listOf(createClassCall("replay", "A"))),
            completed(text = "Created A."),
            completed(calls = listOf(createClassCall("replay", "B"))),
        )
        val replay = fixture(replayProvider)
        replay.service.send(replay.scope, replay.conversation.id, AiConversationTurnRequest("Create class A."))
        val replayed = replay.service.send(replay.scope, replay.conversation.id, AiConversationTurnRequest("Create class B."))
        assertEquals(AiRunStatus.FAILED, replayed.run.status)
        assertEquals(1, replay.drafts.list("alice", "simple", replay.conversation.id).single().items.size)

        val unauthorizedProvider = FakeToolProvider(completed(calls = listOf(createClassCall("unauthorized", "A"))))
        val unauthorized = fixture(unauthorizedProvider, includePreparePermission = false)
        val unauthorizedResult = unauthorized.service.send(
            unauthorized.scope,
            unauthorized.conversation.id,
            AiConversationTurnRequest("Create class A."),
        )
        assertEquals(AiRunStatus.FAILED, unauthorizedResult.run.status)
    }

    @Test
    fun requestToolTokenAndContextLimitsStopSafelyAndPreserveDraft(): Unit = runBlocking {
        val requestLimited = fixture(FakeToolProvider(completed(calls = listOf(createClassCall("call-1", "A")))))
        val requestResult = requestLimited.service.send(
            requestLimited.scope,
            requestLimited.conversation.id,
            AiConversationTurnRequest("Create class A.", policy = AiRunPolicy(maxProviderRequestsPerTurn = 1)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, requestResult.run.status)
        assertEquals("provider-requests", requestResult.limits.single().kind)
        assertEquals(1, requestLimited.drafts.list("alice", "simple", requestLimited.conversation.id).single().items.size)

        val toolLimited = fixture(
            FakeToolProvider(
                completed(calls = listOf(createClassCall("call-1", "A"))),
                completed(calls = listOf(createClassCall("call-2", "B"))),
            ),
        )
        val toolResult = toolLimited.service.send(
            toolLimited.scope,
            toolLimited.conversation.id,
            AiConversationTurnRequest("Create class A.", policy = AiRunPolicy(maxCapabilityCallsPerTurn = 1)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, toolResult.run.status)
        assertEquals("capability-calls", toolResult.limits.single().kind)
        assertEquals(1, toolLimited.drafts.list("alice", "simple", toolLimited.conversation.id).single().items.size)

        val tokenLimited = fixture(FakeToolProvider(completed(text = "Too expensive", usage = OpenAiUsage(10, 1, 11))))
        val tokenResult = tokenLimited.service.send(
            tokenLimited.scope,
            tokenLimited.conversation.id,
            AiConversationTurnRequest("Explain Customer.", policy = AiRunPolicy(maxInputTokens = 5)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, tokenResult.run.status)
        assertEquals("input-tokens", tokenResult.limits.single().kind)

        val outputLimited = fixture(FakeToolProvider(completed(text = "Too verbose", usage = OpenAiUsage(1, 10, 11))))
        val outputResult = outputLimited.service.send(
            outputLimited.scope,
            outputLimited.conversation.id,
            AiConversationTurnRequest("Explain Customer.", policy = AiRunPolicy(maxOutputTokens = 5)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, outputResult.run.status)
        assertEquals("output-tokens", outputResult.limits.single().kind)

        val editLimited = fixture(
            FakeToolProvider(
                completed(calls = listOf(createClassCall("edit-1", "A"), createClassCall("edit-2", "B"))),
            ),
        )
        val editResult = editLimited.service.send(
            editLimited.scope,
            editLimited.conversation.id,
            AiConversationTurnRequest("Create class A and create class B.", policy = AiRunPolicy(maxDraftEditsPerRun = 1)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, editResult.run.status)
        assertEquals("draft-edits", editResult.limits.single().kind)
        assertEquals(1, editLimited.drafts.list("alice", "simple", editLimited.conversation.id).single().items.size)

        val elapsedClock = MutableClock(now)
        val elapsedProvider = FakeToolProvider(
            completed(calls = listOf(createClassCall("elapsed-1", "Elapsed Boundary"))),
            onRespond = { elapsedClock.advanceMillis(10) },
        )
        val elapsedLimited = fixture(elapsedProvider, clock = elapsedClock)
        val elapsedResult = elapsedLimited.service.send(
            elapsedLimited.scope,
            elapsedLimited.conversation.id,
            AiConversationTurnRequest("Create class Elapsed Boundary.", policy = AiRunPolicy(maxElapsedMillis = 5)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, elapsedResult.run.status)
        assertEquals("elapsed-millis", elapsedResult.limits.single().kind)

        val contextProvider = FakeToolProvider(completed(text = "First answer."))
        val contextLimited = fixture(contextProvider)
        contextLimited.service.send(contextLimited.scope, contextLimited.conversation.id, AiConversationTurnRequest("Explain Customer."))
        val contextResult = contextLimited.service.send(
            contextLimited.scope,
            contextLimited.conversation.id,
            AiConversationTurnRequest("Explain Invoice.", policy = AiRunPolicy(maxConversationMessagesInContext = 2)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, contextResult.run.status)
        assertEquals("context-messages", contextResult.limits.single().kind)
    }

    @Test
    fun defaultRunDoesNotStopOnAccumulatedProviderTokenUsage(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(
                calls = listOf(createClassCall("long-edit", "Long-running model")),
                usage = OpenAiUsage(75_000, 1, 75_001),
            ),
            completed(
                text = "The long-running model edit is staged for review.",
                usage = OpenAiUsage(75_000, 1, 75_001),
            ),
        )
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Long-running model."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        assertTrue(result.limits.isEmpty())
        assertEquals(1, fixture.drafts.list("alice", "simple", fixture.conversation.id).single().items.size)
    }

    @Test
    fun defaultRunDoesNotStopAtAnArbitraryProviderRequestCount(): Unit = runBlocking {
        val responses = buildList {
            add(completed(calls = listOf(createClassCall("long-class", "Long-running model"))))
            repeat(12) { index ->
                add(completed(calls = listOf(createObjectPropertyCall("long-property-$index", "long property $index"))))
            }
            add(completed(text = "The long-running model workflow is staged for review."))
        }
        val provider = FakeToolProvider(*responses.toTypedArray())
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Long-running model."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        assertTrue(result.limits.isEmpty())
        assertEquals(14, provider.requests.size)
        assertEquals(13, fixture.drafts.list("alice", "simple", fixture.conversation.id).single().items.size)
    }

    @Test
    fun defaultRunDoesNotStopAfterAnArbitraryElapsedDuration(): Unit = runBlocking {
        val elapsedClock = MutableClock(now)
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("long-class", "Long-running model"))),
            completed(text = "The long-running model edit is staged for review."),
            onRespond = { elapsedClock.advanceMillis(121_000) },
        )
        val fixture = fixture(provider, clock = elapsedClock)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Long-running model."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status, result.answer)
        assertTrue(result.limits.isEmpty())
        assertEquals(1, fixture.drafts.list("alice", "simple", fixture.conversation.id).single().items.size)
    }

    @Test
    fun cancellationPropagatesToProviderAndLeavesAuthoritativeCancelledRun(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val provider = FakeToolProvider(blockStarted = started, blockGate = gate)
        val fixture = fixture(provider)
        val request = async {
            fixture.service.send(fixture.scope, fixture.conversation.id, AiConversationTurnRequest("Explain Customer."))
        }
        started.await()
        val run = fixture.runs.list("alice", "simple").single()

        fixture.service.cancel("alice", "simple", run.id)
        request.join()

        assertEquals(AiRunStatus.CANCELLED, fixture.runs.get("alice", "simple", run.id).status)
        assertTrue(provider.cancelled)
        assertTrue(fixture.service.events("alice", "simple", run.id).any { it.type == AiRunEventType.CANCELLED })
    }

    @Test
    fun concurrentTurnIsRejectedBeforeItCanAppendOrMutateThePrivateDraft(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val provider = FakeToolProvider(blockStarted = started, blockGate = gate)
        val fixture = fixture(provider)
        val first = async {
            fixture.service.send(fixture.scope, fixture.conversation.id, AiConversationTurnRequest("Explain Customer."))
        }
        started.await()

        val failure = assertFailsWith<AiConversationFailure> {
            fixture.service.send(fixture.scope, fixture.conversation.id, AiConversationTurnRequest("Create class Unsafe Concurrent Edit."))
        }

        assertEquals("active-run-in-progress", failure.code)
        assertEquals(1, fixture.conversations.get("alice", "simple", fixture.conversation.id).messages.size)
        assertTrue(fixture.drafts.list("alice", "simple", fixture.conversation.id).isEmpty())
        fixture.service.cancel("alice", "simple", fixture.runs.list("alice", "simple").single().id)
        first.join()
    }

    @Test
    fun semanticStartReturnsImmediatelyAndPublishesSafeProgressUntilCancelled(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val provider = FakeToolProvider(
            completed(text = "The request is complete."),
            blockStarted = started,
            blockGate = gate,
        )
        val fixture = fixture(provider)

        val turn = fixture.service.start(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Turn the lending concepts into a coherent ontology model."),
        )

        assertEquals(AiConversationIntent.SEMANTIC_REQUEST, turn.intent)
        assertEquals(AiRunStatus.QUEUED, turn.run.status)
        assertTrue(turn.answer.contains("working", ignoreCase = true))
        started.await()

        val events = fixture.service.events("alice", "simple", turn.run.id)
        assertTrue(events.any { it.type == AiRunEventType.STATUS_CHANGED && it.message.contains("interpreting", ignoreCase = true) })
        assertTrue(events.any { it.type == AiRunEventType.STATUS_CHANGED && it.message.contains("ontology context", ignoreCase = true) })
        assertTrue(events.none { it.message.contains("chain of thought", ignoreCase = true) })

        fixture.service.cancel("alice", "simple", turn.run.id)
        withTimeout(2_000) {
            while (!fixture.runs.get("alice", "simple", turn.run.id).status.terminal) delay(10)
        }
        gate.complete(Unit)
        assertEquals(AiRunStatus.CANCELLED, fixture.runs.get("alice", "simple", turn.run.id).status)
        assertTrue(fixture.service.events("alice", "simple", turn.run.id).any { it.type == AiRunEventType.CANCELLED })
    }

    @Test
    fun providerAuthorityClaimsAndSensitiveFailuresNeverRenderAsSuccessOrLeakSecrets(): Unit = runBlocking {
        val authorityClaim = fixture(FakeToolProvider(completed(text = "I successfully applied the ontology change.")))

        val blocked = authorityClaim.service.send(
            authorityClaim.scope,
            authorityClaim.conversation.id,
            AiConversationTurnRequest("Explain the current proposal."),
        )

        assertEquals(AiRunStatus.FAILED, blocked.run.status)
        assertTrue(blocked.answer.contains("withheld"))
        assertFalse(blocked.answer.contains("successfully applied"))

        val leakedFailure = fixture(
            FakeToolProvider(
                OpenAiResponsesResult.Failed(
                    OpenAiProviderFailure(
                        OpenAiFailureCode.PROVIDER_ERROR,
                        "Authorization: Bearer server-only-secret",
                        false,
                    ),
                ),
            ),
        )
        val failed = leakedFailure.service.send(
            leakedFailure.scope,
            leakedFailure.conversation.id,
            AiConversationTurnRequest("Explain Customer."),
        )
        val serializedState = buildString {
            append(failed.answer)
            append(leakedFailure.service.events("alice", "simple", failed.run.id))
            append(leakedFailure.audits.list("alice", "simple"))
        }

        assertEquals(AiRunStatus.FAILED, failed.run.status)
        assertFalse(serializedState.contains("server-only-secret"))
        assertFalse(serializedState.contains("Bearer"))
        assertTrue(serializedState.contains("[REDACTED]"))
    }

    private fun fixture(
        provider: FakeToolProvider,
        modelBindings: AiRunModelBindingResolver = FixedAiRunModelBindingResolver(provider, "gpt-5.2"),
        includePreparePermission: Boolean = true,
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
        stripDefinitionsFromCopiedOntology: Boolean = false,
    ): Fixture {
        val source = listOf(Path.of("examples/simple-ontology"), Path.of("../examples/simple-ontology"))
            .first(Files::isDirectory)
            .toAbsolutePath()
            .normalize()
        val parent = Files.createTempDirectory("entio-ai-conversation")
        val project = parent.resolve("simple-ontology")
        Files.walk(source).use { paths ->
            paths.forEach { current ->
                val target = project.resolve(source.relativize(current).toString())
                if (current.isRegularFile()) {
                    Files.createDirectories(target.parent)
                    Files.copy(current, target, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.createDirectories(target)
                }
            }
        }
        if (stripDefinitionsFromCopiedOntology) {
            val ontology = project.resolve("ontology/simple.ttl")
            val withoutDefinitions = Files.readString(ontology).replace(
                Regex(
                    ";\\s*<http://www\\.w3\\.org/2004/02/skos/core#definition>\\s*\"[^\"]*\"\\s*\\.",
                    setOf(RegexOption.MULTILINE),
                ),
                " .",
            )
            Files.writeString(ontology, withoutDefinitions)
        }
        val projects = InMemoryProjectRegistry(setOf(parent))
        projects.register("simple", "Simple", project)
        val staging = StagingWorkflowService(projects)
        val drafts = InMemoryAiDraftStore()
        val baseline = AiProjectBaselineService(projects).current("simple", "simple")
        val conversations = InMemoryAiConversationStore()
        val runs = InMemoryAiRunStore()
        val audits = InMemoryAiAuditStore()
        val ids = mutableMapOf<String, Int>()
        val workspace = AiPrivateDraftWorkspace(
            drafts,
            AiTypedEditCapabilityAdapter(staging),
            clock,
            itemIdFactory = { "draft-item-${ids.merge("draft-item", 1, Int::plus)}" },
        )
        val credentialStore = InMemoryAiCredentialStore()
        val credentials = AiCredentialService(credentialStore, provider)
        credentials.save("alice", AiCredentialRequest(provider.providerId, "test-key"))
        val localReads = AiLocalReadCapabilityService(ReadOnlyProjectAdapter(projects), staging)
        val service = AiConversationService(
            conversations = conversations,
            runs = runs,
            audits = audits,
            draftStore = drafts,
            draftWorkspace = workspace,
            registry = AiCapabilityRegistry(),
            dispatcher = DefaultAiCapabilityDispatcher(drafts = workspace),
            provider = provider,
            credentials = credentials,
            contextPackages = AiContextPackageBuilder(localReads),
            modelBindings = modelBindings,
            clock = clock,
            idFactory = { prefix -> "$prefix-${ids.merge(prefix, 1, Int::plus)}" },
        )
        val conversation = service.createConversation("alice", "simple")
        val permissions = buildSet {
            add(WebPermission.USE_AI.name)
            if (includePreparePermission) add(WebPermission.PREPARE_EDIT.name)
        }
        val scope = AiCapabilityScope(
            userId = "alice",
            projectId = "simple",
            conversationId = conversation.id,
            allowedSourceIds = listOf("simple", "shapes"),
            baselineFingerprint = baseline,
            role = "CONTRIBUTOR",
            permissions = permissions,
            availableFeatures = setOf(AiCapabilityFeatures.PRIVATE_DRAFT),
            createdAt = now,
        )
        return Fixture(service, conversations, runs, audits, drafts, staging, conversation, scope)
    }

    private fun completed(
        text: String = "",
        calls: List<OpenAiFunctionCall> = emptyList(),
        responseId: String? = null,
        usage: OpenAiUsage = OpenAiUsage(1, 1, 2),
    ): OpenAiResponsesResult = OpenAiResponsesResult.Completed(
        OpenAiCompletedResponse(responseId, text, calls, usage, emptyList()),
    )

    private fun createClassCall(id: String, label: String): OpenAiFunctionCall = simpleCall(
        id,
        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
        """{"sourceId":"simple","editType":"create-class","rationale":"Add the reviewed concept.","label":"$label"}""",
    )

    private fun createObjectPropertyCall(id: String, label: String): OpenAiFunctionCall = simpleCall(
        id,
        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
        """{"sourceId":"simple","editType":"create-object-property","rationale":"Add the reviewed relationship.","label":"$label"}""",
    )

    private fun addDefinitionCall(id: String, sourceId: String, label: String, definition: String): OpenAiFunctionCall = simpleCall(
        id,
        AiTypedEditCapabilityAdapter.ADD_DEFINITION_CAPABILITY,
        """{"sourceId":"$sourceId","targetLabel":"$label","value":"$definition","rationale":"Document the reviewed concept."}""",
    )

    private fun updateClassCall(id: String, itemId: String, label: String): OpenAiFunctionCall = simpleCall(
        id,
        AiTypedEditCapabilityAdapter.UPDATE_ONTOLOGY_CAPABILITY,
        """{"itemId":"$itemId","sourceId":"simple","editType":"create-class","rationale":"Revise the proposed concept.","label":"$label"}""",
    )

    private fun simpleCall(id: String, name: String, arguments: String): OpenAiFunctionCall = OpenAiFunctionCall(id, name, arguments)

    private data class Fixture(
        val service: AiConversationService,
        val conversations: InMemoryAiConversationStore,
        val runs: InMemoryAiRunStore,
        val audits: InMemoryAiAuditStore,
        val drafts: InMemoryAiDraftStore,
        val staging: StagingWorkflowService,
        val conversation: AiConversation,
        val scope: AiCapabilityScope,
    )

    private class FakeToolProvider(
        vararg initialResponses: OpenAiResponsesResult,
        private val blockStarted: CompletableDeferred<Unit>? = null,
        private val blockGate: CompletableDeferred<Unit>? = null,
        private val onRespond: (() -> Unit)? = null,
    ) : AiToolLoopProvider, AiProviderClient {
        override val providerId: String = "openai"
        override val promptVersion: String = "phase-7-test-v1"
        val requests: MutableList<OpenAiResponsesRequest> = mutableListOf()
        val selectedModelIds: MutableList<String> = mutableListOf()
        var cancelled: Boolean = false
        private val responses: ArrayDeque<OpenAiResponsesResult> = ArrayDeque(initialResponses.toList())

        override suspend fun test(apiKey: String): AiProviderTestResult = AiProviderTestResult.Passed("Accepted")

        override suspend fun respond(
            apiKey: String,
            request: OpenAiResponsesRequest,
            onEvent: suspend (OpenAiProviderEvent) -> Unit,
        ): OpenAiResponsesResult {
            requests += request
            blockStarted?.complete(Unit)
            try {
                blockGate?.await()
            } catch (cancelledFailure: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw cancelledFailure
            }
            onRespond?.invoke()
            return if (responses.isEmpty()) {
                OpenAiResponsesResult.Failed(OpenAiProviderFailure(OpenAiFailureCode.PROVIDER_ERROR, "No fake response.", false))
            } else {
                responses.removeFirst()
            }
        }

        override suspend fun respond(
            apiKey: String,
            selectedModelId: String,
            request: OpenAiResponsesRequest,
            onEvent: suspend (OpenAiProviderEvent) -> Unit,
        ): OpenAiResponsesResult {
            selectedModelIds += selectedModelId
            return respond(apiKey, request, onEvent)
        }
    }

    private class MutableBindingResolver(var modelId: String) : AiRunModelBindingResolver {
        var resolveCount: Int = 0
        var unavailableCount: Int = 0

        override fun resolve(userId: String): AiRunModelBinding {
            resolveCount += 1
            return AiRunModelBinding("openai", modelId, "catalog-test", 7, "prompt-test", "request-test")
        }

        override suspend fun markUnavailableAndRefresh(userId: String, binding: AiRunModelBinding) {
            unavailableCount += 1
        }
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceMillis(milliseconds: Long): Unit {
            current = current.plusMillis(milliseconds)
        }
    }
}
