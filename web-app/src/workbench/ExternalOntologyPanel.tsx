import { useState } from "react";
import { useFiboActions, useFiboDetails, useFiboModuleElements, useFiboModules, useFiboSearch } from "../web/queries";
import type { WebFiboElement } from "../web/projectApi";

export default function ExternalOntologyPanel({ projectId, sourceId }: { projectId: string; sourceId: string }) {
  const modules = useFiboModules(projectId);
  const [selectedModule, setSelectedModule] = useState<string | null>(null);
  const elements = useFiboModuleElements(projectId, selectedModule);
  const [searchInput, setSearchInput] = useState("");
  const [searchText, setSearchText] = useState("");
  const search = useFiboSearch(projectId, searchText);
  const [selectedIri, setSelectedIri] = useState<string | null>(null);
  const details = useFiboDetails(projectId, selectedIri);
  const stage = useFiboActions(projectId);
  const [targetOntologyIri, setTargetOntologyIri] = useState("https://example.com/entio/simple#");
  const [localClassIri, setLocalClassIri] = useState("");
  const [intentType, setIntentType] = useState<"reuse-class" | "reuse-object-property" | "reuse-datatype-property" | "create-local-subclass">("reuse-class");
  const [selectedDependencies, setSelectedDependencies] = useState<string[]>([]);

  function selectElement(element: WebFiboElement) {
    setSelectedIri(element.iri);
    setSelectedDependencies([]);
  }

  function submitSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearchText(searchInput.trim());
  }

  function toggleDependency(iri: string) {
    setSelectedDependencies((current) => current.includes(iri) ? current.filter((value) => value !== iri) : [...current, iri]);
  }

  function stageProposal() {
    if (!details.data) return;
    stage.mutate({
      intentType,
      sourceId,
      targetOntologyIri,
      externalIri: details.data.element.iri,
      localClassIri: intentType === "create-local-subclass" ? localClassIri : undefined,
      selectedDependencyIris: selectedDependencies,
      idempotencyKey: `fibo-${details.data.element.iri}-${Date.now()}`,
    });
  }

  const moduleItems = modules.data?.page.items ?? [];
  const elementItems = elements.data?.page.items ?? [];
  const searchItems = search.data?.page.items ?? [];

  return (
    <section className="external-ontology-panel" aria-labelledby="fibo-heading">
      <div className="section-heading"><h2 id="fibo-heading">External ontology browser</h2><span>FIBO</span></div>
      <p className="muted">Pinned, read-only catalog. External IRIs are preserved and reusable items enter shared staging.</p>
      <form className="external-search" onSubmit={submitSearch} role="search">
        <label htmlFor="fibo-search">Search FIBO</label>
        <div className="search-row"><input id="fibo-search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="agreement" /><button type="submit">Search</button></div>
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
      {details.isPending ? <p role="status">Loading external details...</p> : null}
      {details.isError ? <p role="alert">Could not load external details.</p> : null}
      {details.data ? <ExternalDetails
        details={details.data}
        intentType={intentType}
        onIntentTypeChange={setIntentType}
        targetOntologyIri={targetOntologyIri}
        onTargetOntologyIriChange={setTargetOntologyIri}
        localClassIri={localClassIri}
        onLocalClassIriChange={setLocalClassIri}
        selectedDependencies={selectedDependencies}
        onToggleDependency={toggleDependency}
        onStage={stageProposal}
        staging={stage}
      /> : null}
    </section>
  );
}

function ExternalDetails({ details, intentType, onIntentTypeChange, targetOntologyIri, onTargetOntologyIriChange, localClassIri, onLocalClassIriChange, selectedDependencies, onToggleDependency, onStage, staging }: any) {
  const element = details.element as WebFiboElement;
  const visibleDependencies = details.dependencies.filter((dependency: any) => dependency.visibility === "UserVisible" && dependency.requirement === "Required");
  return (
    <div className="external-details">
      <h3>{element.label}</h3>
      <p><strong>IRI:</strong> <code>{element.iri}</code></p>
      {element.definitions.map((definition) => <p key={definition}>{definition}</p>)}
      {element.alternateLabels.length ? <p><strong>Alternate labels:</strong> {element.alternateLabels.join(", ")}</p> : null}
      <h4>Dependencies</h4>
      {visibleDependencies.length ? <ul className="dependency-list">{visibleDependencies.map((dependency: any) => <li key={`${dependency.category}-${dependency.externalIri}`}><label><input type="checkbox" checked={selectedDependencies.includes(dependency.externalIri ?? "")} disabled={!dependency.externalIri || dependency.selection === "AlreadyAvailable"} onChange={() => dependency.externalIri && onToggleDependency(dependency.externalIri)} /> <strong>{dependency.label ?? dependency.externalIri ?? dependency.category}</strong> · {dependency.selection}</label></li>)}</ul> : <p className="muted">No required user-visible dependencies.</p>}
      <div className="external-proposal-form">
        <h4>Stage reuse proposal</h4>
        <label htmlFor="fibo-intent">Intent</label>
        <select id="fibo-intent" value={intentType} onChange={(event) => onIntentTypeChange(event.target.value)}><option value="reuse-class">Reuse class</option><option value="reuse-object-property">Reuse object property</option><option value="reuse-datatype-property">Reuse datatype property</option><option value="create-local-subclass">Create local subclass</option></select>
        <label htmlFor="fibo-target-ontology">Target ontology IRI</label>
        <input id="fibo-target-ontology" value={targetOntologyIri} onChange={(event) => onTargetOntologyIriChange(event.target.value)} />
        {intentType === "create-local-subclass" ? <><label htmlFor="fibo-local-class">Local class IRI</label><input id="fibo-local-class" value={localClassIri} onChange={(event) => onLocalClassIriChange(event.target.value)} placeholder="https://example.com/entio/simple#LocalClass" /></> : null}
        <button type="button" onClick={onStage} disabled={staging.isPending || (intentType === "create-local-subclass" && !localClassIri.trim())}>Stage external proposal</button>
        {staging.isError ? <p role="alert">Could not stage external proposal. {staging.error.message}</p> : null}
        {staging.isSuccess ? <p role="status">External proposal added to shared staged changes.</p> : null}
      </div>
    </div>
  );
}
