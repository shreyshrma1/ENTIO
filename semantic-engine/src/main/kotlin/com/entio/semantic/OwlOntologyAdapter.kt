package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Path
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.io.FileDocumentSource
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.OWLOntologyCreationException
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration

public data class OwlOntologyDocument(
    public val sourceId: String,
    public val ontology: OWLOntology,
    public val importedSourceIds: List<String> = emptyList(),
)

public class OwlOntologyAdapter {
    public fun load(
        source: ResolvedOntologySource,
        importMappings: Map<String, Path> = emptyMap(),
    ): EntioResult<OwlOntologyDocument> {
        val manager = OWLManager.createOWLOntologyManager()
        importMappings.forEach { (ontologyIri, localPath) ->
            manager.addIRIMapper(
                LocalOntologyIriMapper(
                    ontologyIri = ontologyIri,
                    localPath = localPath,
                ),
            )
        }

        val configuration = OWLOntologyLoaderConfiguration()
            .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.THROW_EXCEPTION)

        return try {
            EntioResult.Success(
                OwlOntologyDocument(
                    sourceId = source.id,
                    ontology = manager.loadOntologyFromOntologyDocument(
                        FileDocumentSource(source.path.toFile()),
                        configuration,
                    ),
                    importedSourceIds = importMappings.keys.sorted(),
                ),
            )
        } catch (exception: OWLOntologyCreationException) {
            failure(source, exception)
        } catch (exception: RuntimeException) {
            failure(source, exception)
        }
    }

    private fun failure(
        source: ResolvedOntologySource,
        exception: Throwable,
    ): EntioResult.Failure = EntioResult.Failure(
        message = "Ontology source '${source.id}' could not be loaded by OWL API.",
        issues = listOf(
            ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "owl-ontology-load-failed",
                message = "Ontology source '${source.id}' could not be loaded by OWL API.",
                source = source.id,
            ),
        ),
        cause = exception,
    )

    private class LocalOntologyIriMapper(
        private val ontologyIri: String,
        private val localPath: Path,
    ) : OWLOntologyIRIMapper {
        override fun getDocumentIRI(ontologyIRI: IRI): IRI? =
            if (ontologyIRI.toString() == ontologyIri) IRI.create(localPath.toUri().toString()) else null
    }
}
