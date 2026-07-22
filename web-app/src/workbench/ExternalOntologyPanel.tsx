import { useState } from "react";
import { useFiboActions, useFiboDetails, useFiboModuleElements, useFiboModules, useFiboSearch, useProjectSummary } from "../web/queries";
import type { WebFiboElement } from "../web/projectApi";

export default function ExternalOntologyPanel({ projectId, sourceId }: { projectId: string; sourceId: string }) {
  const modules = useFiboModules(projectId);
  const projectSummary = useProjectSummary(projectId);
  const [selectedModule, setSelectedModule] = useState<string | null>(null);
  const elements = useFiboModuleElements(projectId, selectedModule);
  const [searchInput, setSearchInput] = useState("");
  const [searchText, setSearchText] = useState("");
  const search = useFiboSearch(projectId, searchText);
  const [selectedIri, setSelectedIri] = useState<string | null>(null);
  const details = useFiboDetails(projectId, selectedIri);
  const stage = useFiboActions(projectId);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [targetOntologyIri, setTargetOntologyIri] = useState("https://example.com/entio/simple#");
  const [selectedDependencies, setSelectedDependencies] = useState<string[]>([]);

  function selectElement(element: WebFiboElement) {
    setSelectedIri(element.iri);
    setSelectedDependencies([]);
    setDetailsOpen(true);
  }

  function submitSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // A search replaces the current module browse view. Clear the module
    // selection so the returned search results are actually shown.
    setSelectedModule(null);
    setSearchText(searchInput.trim());
  }

  function toggleDependency(iri: string) {
    setSelectedDependencies((current) => current.includes(iri) ? current.filter((value) => value !== iri) : [...current, iri]);
  }

  function stageProposal() {
    if (!details.data) return;
    const intentType = intentForElement(details.data.element);
    stage.mutate({
      intentType,
      sourceId,
      targetOntologyIri,
      externalIri: details.data.element.iri,
      selectedDependencyIris: selectedDependencies,
      idempotencyKey: `fibo-${details.data.element.iri}-${Date.now()}`,
    });
  }

  const moduleItems = modules.data?.page.items ?? [];
  const elementItems = elements.data?.page.items ?? [];
  const searchItems = search.data?.page.items ?? [];

  return (
    <section className="external-ontology-panel" aria-labelledby="fibo-heading">
      <div className="section-heading"><h2 id="fibo-heading">External ontology browser</h2></div>
      <p className="muted">Pinned read-only catalog. Select a module or search for a reusable concept.</p>
      <form className="external-search" onSubmit={submitSearch} role="search">
        <label htmlFor="fibo-search">Search FIBO</label>
        <div className="search-row"><input id="fibo-search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="agreement" /><button className="button primary" type="submit">Search</button></div>
      </form>
      <div className="external-browser-grid">
        <div>
          <h3>Curated foundations</h3>
          {modules.isPending ? <p role="status">Loading modules...</p> : null}
          {modules.isError ? <p role="alert">Could not load FIBO modules.</p> : null}
          <div className="external-scroll-list">
            {moduleItems.map((module) => <button type="button" className={selectedModule === module.ontologyIri ? "selected-list-item" : "list-item"} key={module.ontologyIri} onClick={() => setSelectedModule(module.ontologyIri)}><strong>{module.label}</strong><small>{module.domain} · {module.elementCount} catalog elements</small></button>)}
          </div>
          {modules.data?.page.nextOffset !== null ? <p className="muted">More curated modules are available through the paged API.</p> : null}
        </div>
        <div>
          <h3>{selectedModule ? "Module elements" : "Search results"}</h3>
          <div className="external-scroll-list">
            {(selectedModule ? elementItems : searchItems).map((element) => <button type="button" className={selectedIri === element.iri ? "selected-list-item" : "list-item"} key={element.iri} onClick={() => selectElement(element)}><strong>{element.label}</strong><small>{element.kind} · {element.maturity}</small></button>)}
          </div>
          {!selectedModule && !searchText ? <p className="muted">Search or select a module to browse its elements.</p> : null}
          {selectedModule && !elementItems.length && !elements.isPending ? <p className="muted">No elements found in this module.</p> : null}
        </div>
      </div>
      {detailsOpen && selectedIri ? <div className="external-details-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setDetailsOpen(false)}>
        <section className="external-details-modal" role="dialog" aria-modal="true" aria-labelledby="external-details-heading">
          <header className="external-details-modal-header">
            <div>
              <span className="eyebrow">FIBO</span>
              <h3 id="external-details-heading">{details.data?.element.label ?? "External details"}</h3>
            </div>
            <button className="icon-button" type="button" aria-label="Close external details" onClick={() => setDetailsOpen(false)}>×</button>
          </header>
          <div className="external-details-modal-body">
            {details.isPending ? <p role="status">Loading external details...</p> : null}
            {details.isError ? <p role="alert">Could not load external details.</p> : null}
            {details.data ? <ExternalDetails
              details={details.data}
              projectDisplayName={projectSummary.data?.project.displayName}
              targetOntologyIri={targetOntologyIri}
              onTargetOntologyIriChange={setTargetOntologyIri}
              selectedDependencies={selectedDependencies}
              onToggleDependency={toggleDependency}
              onStage={stageProposal}
              staging={stage}
            /> : null}
          </div>
        </section>
      </div> : null}
    </section>
  );
}

function ExternalDetails({ details, projectDisplayName, targetOntologyIri, onTargetOntologyIriChange, selectedDependencies, onToggleDependency, onStage, staging }: any) {
  const element = details.element as WebFiboElement;
  const visibleDependencies = details.dependencies
    .filter((dependency: any) => dependency.visibility === "UserVisible")
    .sort((left: any, right: any) => Number(right.category === "SourceOntology") - Number(left.category === "SourceOntology"));
  return (
    <div className="external-details">
      <p><strong>IRI:</strong> <code>{element.iri}</code></p>
      {element.definitions.map((definition) => <p key={definition}><strong>Definition:</strong> {definition}</p>)}
      {element.alternateLabels.length ? <p><strong>Alternate labels:</strong> {element.alternateLabels.join(", ")}</p> : null}
      <h4>Dependencies</h4>
      <p className="muted external-dependency-help">To import this {elementKindLabel(element.kind)}, you must also import the required module dependency marked with a star (★). An ontology module is the file that defines the concept and is imported with <code>owl:imports</code>; a semantic parent is a related superclass that provides context but is not imported separately.</p>
      {visibleDependencies.length ? <ul className="dependency-list">{visibleDependencies.map((dependency: any) => <li key={`${dependency.category}-${dependency.externalIri}`}><label><input type="checkbox" checked={selectedDependencies.includes(dependency.externalIri ?? "")} disabled={!dependency.externalIri || dependency.selection === "AlreadyAvailable"} onChange={() => dependency.externalIri && onToggleDependency(dependency.externalIri)} /><span><strong>{dependency.label ?? dependency.externalIri ?? dependency.category}</strong><small>{dependencyKindLabel(dependency.category)} · {dependency.selection}</small></span>{dependency.category === "SourceOntology" ? <span className="dependency-import-marker" title="Required ontology import" aria-label="Required ontology import">★</span> : <span className="dependency-import-marker-placeholder" aria-hidden="true" />}</label></li>)}</ul> : <p className="muted">No user-visible dependencies.</p>}
      <div className="external-proposal-form">
        <label htmlFor="fibo-target-ontology">Target ontology</label>
        <select id="fibo-target-ontology" value={targetOntologyIri} onChange={(event) => onTargetOntologyIriChange(event.target.value)}>
          <option value={targetOntologyIri}>{projectDisplayName ?? "Current ontology"}</option>
        </select>
        <p className="muted">The selected FIBO {elementKindLabel(element.kind)} will be copied into the local ontology with its original IRI and can be edited after approval.</p>
        <button className="button primary" type="button" onClick={onStage} disabled={staging.isPending}>Stage external proposal</button>
        {staging.isError ? <p role="alert">Could not stage external proposal. {staging.error.message}</p> : null}
        {staging.isSuccess ? <p role="status">External proposal added to shared staged changes.</p> : null}
      </div>
    </div>
  );
}

function intentForElement(element: WebFiboElement): "reuse-class" | "reuse-object-property" | "reuse-datatype-property" {
  const kind = element.kind.replace(/[\s_-]/g, "").toLowerCase();
  if (kind === "objectproperty") return "reuse-object-property";
  if (kind === "datatypeproperty") return "reuse-datatype-property";
  return "reuse-class";
}

function dependencyKindLabel(category: string): string {
  switch (category) {
    case "SourceOntology": return "Ontology module · adds owl:imports";
    case "SemanticParent": return "Semantic parent · context only";
    case "PropertyDomain": return "Property domain · context only";
    case "PropertyRange": return "Property range · context only";
    default: return category.replace(/([a-z])([A-Z])/g, "$1 $2");
  }
}

function elementKindLabel(kind: string): string {
  const normalized = kind.replace(/[\s_-]/g, "").toLowerCase();
  if (normalized === "objectproperty") return "object property";
  if (normalized === "datatypeproperty") return "datatype property";
  if (normalized === "annotationproperty") return "annotation property";
  return "class";
}
