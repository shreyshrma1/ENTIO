package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReasoningWorkerProtocolTest {
    @Test
    fun roundTripsVersionedRequestAndResponse(): Unit {
        val request = ReasoningWorkerRequest(
            ontologySourcePaths = listOf("/tmp/ontology one.ttl"),
            importSourcePaths = listOf("/tmp/import.ttl"),
            graphFingerprint = "graph-1",
            importClosureFingerprint = "imports-1",
            reasonerConfigurationFingerprint = "config-1",
        )
        val decodedRequest = ReasoningWorkerProtocol.decodeRequest(
            ReasoningWorkerProtocol.encodeRequest(request),
        )
        val response = ReasoningWorkerResponse(
            status = ReasoningWorkerResponseStatus.Completed,
            output = ReasoningWorkerNormalizedOutput(
                graphFingerprint = request.graphFingerprint,
                importClosureFingerprint = request.importClosureFingerprint,
                facts = listOf(
                    ReasoningWorkerFact(
                        subject = "https://example.com/Customer",
                        predicate = "http://www.w3.org/2000/01/rdf-schema#subClassOf",
                        objectValue = "https://example.com/Party",
                        origin = FactOrigin.Inferred,
                    ),
                ),
            ),
        )
        val decodedResponse = ReasoningWorkerProtocol.decodeResponse(
            ReasoningWorkerProtocol.encodeResponse(response),
        )

        val requestValue = assertIs<EntioResult.Success<ReasoningWorkerRequest>>(decodedRequest).value
        val responseValue = assertIs<EntioResult.Success<ReasoningWorkerResponse>>(decodedResponse).value
        assertEquals(request, requestValue)
        assertEquals(response, responseValue)
    }

    @Test
    fun rejectsProtocolMismatchAndMalformedOutput(): Unit {
        val mismatch = ReasoningWorkerProtocol.decodeRequest(
            "protocolVersion=99\nmessageType=request",
        )
        val malformed = ReasoningWorkerProtocol.decodeResponse(
            "protocolVersion=1\nmessageType=response\nstatus=Completed",
        )

        assertIs<EntioResult.Failure>(mismatch)
        assertEquals("reasoning-worker-protocol-invalid", mismatch.issues.single().code)
        assertIs<EntioResult.Failure>(malformed)
        assertEquals("reasoning-worker-protocol-invalid", malformed.issues.single().code)
    }

    @Test
    fun representsStructuredWorkerFailure(): Unit {
        val response = ReasoningWorkerResponse(
            status = ReasoningWorkerResponseStatus.TimedOut,
            failure = ReasoningWorkerFailure(
                kind = ReasoningWorkerFailureKind.Timeout,
                message = "Reasoning worker timed out.",
            ),
        )
        val decoded = ReasoningWorkerProtocol.decodeResponse(
            ReasoningWorkerProtocol.encodeResponse(response),
        )

        val value = assertIs<EntioResult.Success<ReasoningWorkerResponse>>(decoded).value
        assertEquals(ReasoningWorkerResponseStatus.TimedOut, value.status)
        assertEquals(ReasoningWorkerFailureKind.Timeout, value.failure?.kind)
        assertEquals("Reasoning worker timed out.", value.failure?.message)
    }
}
