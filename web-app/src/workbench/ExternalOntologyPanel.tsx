import { useMemo, useState } from "react";
import {
  useDomainFoundation,
  useDomainOntology,
  useDomainRecommendation,
  useDomainSearch,
  useFiboDetails,
  useFiboSearch,
  useProjectSearch,
} from "../web/queries";
import type { WebDomainFoundationMember, WebDomainRecommendation, WebSemanticSearchHit } from "../web/projectApi";

export default function ExternalOntologyPanel({ projectId }: { projectId: string; sourceId: string }) {
  const domain = useDomainOntology(projectId);
  const active = domain.status.data?.status.availability === "Active";
  const descriptor = domain.catalog.data?.domainOntologies[0];
  const foundations = useDomainFoundation(projectId, active);
  const [confirmationChecked, setConfirmationChecked] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [searchInput, setSearchInput] = useState("");
  const [submittedSearchText, setSubmittedSearchText] = useState("");
  const domainSearch = useDomainSearch(projectId, submittedSearchText);
  const projectSearch = useProjectSearch(projectId, submittedSearchText);
  const [selectedRecommendationId, setSelectedRecommendationId] = useState<string | null>(null);
  const recommendation = useDomainRecommendation(projectId, selectedRecommendationId);
  const [partialAcknowledged, setPartialAcknowledged] = useState(false);
  const [inactiveSearchText, setInactiveSearchText] = useState("");
  const [inactiveSelectedIri, setInactiveSelectedIri] = useState<string | null>(null);
  const inactiveSearch = useFiboSearch(projectId, inactiveSearchText);
  const inactiveDetails = useFiboDetails(projectId, inactiveSelectedIri);

  const selectedMembers = useMemo(() => foundations.foundation.data?.groups
    .flatMap((group) => group.members)
    .filter((member) => selectedIds.has(member.elementId)) ?? [], [foundations.foundation.data, selectedIds]);

  function toggleMember(member: WebDomainFoundationMember) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(member.elementId)) next.delete(member.elementId); else next.add(member.elementId);
      return next;
    });
  }

  function toggleGroup(members: WebDomainFoundationMember[]) {
    setSelectedIds((current) => {
      const next = new Set(current);
      const allSelected = members.every((member) => next.has(member.elementId));
      members.forEach((member) => allSelected ? next.delete(member.elementId) : next.add(member.elementId));
      return next;
    });
  }

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    const value = searchInput.trim();
    if (!value) return;
    setSelectedRecommendationId(null);
    setSubmittedSearchText(value);
  }

  const preview = domain.previewActivation.data;
  const deactivation = domain.previewDeactivation.data;
  const retrieval = descriptor?.retrievalAvailability ?? "Unavailable";

  return <section className="external-ontology-panel domain-ontology-panel" aria-labelledby="domain-heading">
    <div className="section-heading">
      <div><span className="overline">Optional domain ontology</span><h2 id="domain-heading">Domain ontology</h2></div>
      <SourceBadge family="FIBO" />
    </div>

    {domain.catalog.isPending || domain.status.isPending ? <p role="status">Loading domain settings...</p> : null}
    {domain.catalog.isError || domain.status.isError ? <p role="alert">Domain settings are unavailable. Retry from this page.</p> : null}

    {descriptor && domain.status.data ? <>
      <div className="domain-setting-card">
        <label htmlFor="domain-selection">Domain ontology</label>
        <select id="domain-selection" value={active ? descriptor.sourceId : "none"} disabled={active || domain.activate.isPending || !descriptor.selectable} onChange={(event) => { if (event.target.value === descriptor.sourceId) domain.previewActivation.mutate(); }}>
          <option value="none">None</option>
          <option value={descriptor.sourceId}>FIBO</option>
        </select>
        <dl className="domain-release-details">
          <div><dt>Release</dt><dd>{descriptor.release}</dd></div>
          <div><dt>Retrieval</dt><dd><RetrievalStatus availability={retrieval} /></dd></div>
          <div><dt>Project status</dt><dd>{domain.status.data.status.availability}</dd></div>
        </dl>
        {!active && !preview ? <button className="button primary" type="button" onClick={() => domain.previewActivation.mutate()} disabled={!descriptor.selectable || domain.previewActivation.isPending}>Select FIBO</button> : null}
        {domain.previewActivation.isError ? <p role="alert">Activation preview failed. No project file was changed.</p> : null}
      </div>

      {!active && preview ? <section className="domain-confirmation" aria-labelledby="domain-activation-heading">
        <h3 id="domain-activation-heading">Confirm FIBO activation</h3>
        <p>This prepares the exact project-owned profile and an empty managed reuse source. It does not import or change ontology statements.</p>
        <dl>
          <div><dt>Profile file</dt><dd><code>{preview.preview.profilePath}</code></dd></div>
          <div><dt>Managed source</dt><dd><code>{preview.preview.managedSourcePath}</code></dd></div>
          <div><dt>Ontology statements changed</dt><dd>{preview.preview.changesProjectOntology ? "Yes" : "No"}</dd></div>
        </dl>
        <details><summary>Exact profile content</summary><pre>{preview.preview.serializedProfile}</pre></details>
        <label className="confirmation-check"><input type="checkbox" checked={confirmationChecked} onChange={(event) => setConfirmationChecked(event.target.checked)} /> I understand these exact files will be created.</label>
        <div className="button-row"><button className="button primary" type="button" disabled={!confirmationChecked || domain.activate.isPending} onClick={() => domain.activate.mutate(preview.activationToken)}>Activate FIBO</button><button className="button" type="button" onClick={() => domain.previewActivation.reset()}>Cancel</button></div>
        {domain.activate.isError ? <p role="alert">Activation failed and the prior project state was restored.</p> : null}
      </section> : null}

      {active ? <>
        <section className="domain-foundation" aria-labelledby="foundation-heading">
          <div className="section-heading compact"><div><span className="overline">Starting point</span><h3 id="foundation-heading">Foundational concepts</h3></div><button className="button" type="button" disabled={foundations.plan.isPending} onClick={() => foundations.plan.mutate({ selectAll: true })}>Plan all</button></div>
          <p>Select whole groups or individual classes and properties. Entio calculates dependencies and stable review batches on the server.</p>
          {foundations.foundation.isPending ? <p role="status">Loading foundational concepts...</p> : null}
          {foundations.foundation.isError ? <p role="alert">Foundational concepts could not be loaded.</p> : null}
          <div className="domain-foundation-groups">{foundations.foundation.data?.groups.map((group) => {
            const allSelected = group.members.every((member) => selectedIds.has(member.elementId));
            return <section key={group.groupId} className="domain-foundation-group"><header><label><input type="checkbox" checked={allSelected} onChange={() => toggleGroup(group.members)} /> <strong>{group.label}</strong></label><span>{group.members.length}</span></header><ul>{group.members.map((member) => <li key={member.elementId}><label><input type="checkbox" checked={selectedIds.has(member.elementId)} onChange={() => toggleMember(member)} /> <span>{member.label}<small>{member.kind} · <SourceBadge family={member.sourceFamily} /></small></span></label></li>)}</ul></section>;
          })}</div>
          <div className="button-row"><button className="button primary" type="button" disabled={!selectedMembers.length || foundations.plan.isPending} onClick={() => foundations.plan.mutate({ elementIds: selectedMembers.map((member) => member.elementId) })}>Plan selected ({selectedMembers.length})</button></div>
          {foundations.plan.isError ? <p role="alert">The selected foundation plan could not be prepared. No selection was dropped.</p> : null}
          {foundations.plan.data ? <FoundationPlan response={foundations.plan.data} /> : null}
        </section>

        <section className="domain-search" aria-labelledby="domain-search-heading">
          <div className="section-heading compact"><div><span className="overline">Full corpus</span><h3 id="domain-search-heading">Search local and domain concepts</h3></div></div>
          <form role="search" onSubmit={submitSearch}><label htmlFor="domain-search-input">Search concepts</label><div className="search-row"><input id="domain-search-input" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="agreement" /><button className="button primary" type="submit" disabled={domainSearch.search.isFetching}>Search project and FIBO</button></div></form>
          {submittedSearchText && domainSearch.search.isPending ? <p role="status">Searching the complete verified domain index...</p> : null}
          {submittedSearchText && domainSearch.search.isError ? <p role="alert">Domain search failed. Existing project work is unchanged.</p> : null}
          {submittedSearchText ? <UnifiedProjectResults query={projectSearch} /> : null}
          {domainSearch.search.data ? <><RetrievalStatus availability={domainSearch.search.data.result.availability} />{domainSearch.search.data.result.noConfidentMatch ? <p>No confident domain match. Continue locally or broaden the wording.</p> : null}<div className="external-scroll-list">{domainSearch.search.data.result.recommendations.map((result) => <RecommendationButton key={result.recommendationId} result={result} selected={selectedRecommendationId === result.recommendationId} onSelect={() => { setSelectedRecommendationId(result.recommendationId); setPartialAcknowledged(false); }} />)}</div>{!domainSearch.search.data.result.recommendations.length ? <p>No domain matches found.</p> : null}</> : null}
          {selectedRecommendationId ? <section className="domain-recommendation-detail" aria-live="polite">
            {recommendation.isPending ? <p role="status">Loading source and project meaning...</p> : null}
            {recommendation.isError ? <p role="alert">This result is stale or unavailable. Search again to refresh it.</p> : null}
            {recommendation.data ? <RecommendationDetail detail={recommendation.data} retrievalAvailability={domainSearch.search.data?.result.availability ?? retrieval} acknowledged={partialAcknowledged} onAcknowledged={setPartialAcknowledged} onStage={() => domainSearch.stage.mutate({ recommendationId: selectedRecommendationId, acknowledged: partialAcknowledged })} staging={domainSearch.stage} /> : null}
          </section> : null}
        </section>

        {!deactivation ? <button className="button danger" type="button" onClick={() => domain.previewDeactivation.mutate()}>Deactivate FIBO</button> : <section className="domain-confirmation"><h3>Confirm deactivation</h3>{deactivation.preview.eligible ? <><p>The empty managed source and profile can be removed. Ontology statements will not change.</p><button className="button danger" type="button" disabled={!deactivation.deactivationToken} onClick={() => deactivation.deactivationToken && domain.deactivate.mutate(deactivation.deactivationToken)}>Deactivate</button></> : <><p>FIBO cannot be deactivated while project work depends on it.</p><ul>{deactivation.preview.blockers.map((blocker) => <li key={blocker}>{humanize(blocker)}</li>)}</ul></>}<button className="button" type="button" onClick={() => domain.previewDeactivation.reset()}>Cancel</button></section>}
      </> : <InactiveBrowser searchText={inactiveSearchText} setSearchText={setInactiveSearchText} search={inactiveSearch} selectedIri={inactiveSelectedIri} setSelectedIri={setInactiveSelectedIri} details={inactiveDetails} />}
    </> : null}
  </section>;
}

function InactiveBrowser({ searchText, setSearchText, search, selectedIri, setSelectedIri, details }: any) {
  const [input, setInput] = useState("");
  return <section className="domain-inactive-browser" aria-labelledby="inactive-browser-heading"><h3 id="inactive-browser-heading">Browse FIBO before activation</h3><p className="muted">Browsing is read-only. Contextual recommendations and reuse actions become available only after activation.</p><form role="search" onSubmit={(event) => { event.preventDefault(); setSearchText(input.trim()); }}><label htmlFor="inactive-domain-search">Search catalog</label><div className="search-row"><input id="inactive-domain-search" value={input} onChange={(event) => setInput(event.target.value)} /><button className="button" type="submit">Browse</button></div></form>{search.isPending ? <p role="status">Loading catalog results...</p> : null}{search.isError ? <p role="alert">Catalog browsing is unavailable.</p> : null}<div className="external-scroll-list">{search.data?.page.items.map((item: any) => <button type="button" className={selectedIri === item.iri ? "selected-list-item" : "list-item"} key={item.iri} onClick={() => setSelectedIri(item.iri)}><strong>{item.label}</strong><small>{item.kind} · Available, not applied</small></button>)}</div>{searchText && !search.isPending && !search.data?.page.items.length ? <p>No catalog matches.</p> : null}{details.data ? <div className="domain-source-preview"><SourceBadge family={details.data.element.domain === "Commons" ? "OMG_COMMONS" : "FIBO"} /><h4>{details.data.element.label}</h4><code>{details.data.element.iri}</code>{details.data.element.definitions.map((definition: string) => <p key={definition}>{definition}</p>)}<p className="muted">Available in the catalog · not applied to this project</p></div> : null}</section>;
}

function FoundationPlan({ response }: any) {
  return <section className="foundation-plan" aria-labelledby="foundation-plan-heading"><h4 id="foundation-plan-heading">Review plan</h4><p>{response.plan.explicitSelectionCount} selected · {response.plan.dependencyCount} dependencies · {response.plan.batches.length} review batches</p><p role="status">0 of {response.plan.batches.length} batches applied</p><ol>{response.plan.batches.map((batch: any) => <li key={batch.batchNumber}><strong>Batch {batch.batchNumber}</strong> · {batch.explicitSelectionCount} selected<ul>{batch.items.map((item: any) => <li key={`${item.iri}-${item.role}`}>{item.label} <small>{humanize(item.role)}</small></li>)}</ul></li>)}</ol></section>;
}

function RecommendationButton({ result, selected, onSelect }: { result: WebDomainRecommendation; selected: boolean; onSelect: () => void }) {
  const status = result.warnings.includes("CustomizedFromSource") ? "Customized project reuse" : result.reasons.some((reason) => reason.type === "AlreadyReused") ? "Project reuse" : "Available, not applied";
  return <button type="button" className={selected ? "selected-list-item" : "list-item"} onClick={onSelect}><strong>{result.preferredLabel}</strong><small><SourceBadge family={result.sourceFamily} /> · {result.kind} · {status}</small></button>;
}

function UnifiedProjectResults({ query }: { query: ReturnType<typeof useProjectSearch> }) {
  if (query.isPending) return <p role="status">Searching project concepts...</p>;
  if (query.isError) return <p role="alert">Project search is unavailable. Domain results remain separate.</p>;
  const results = query.data?.page.items ?? [];
  if (!results.length) return <p className="muted">No local or imported matches.</p>;
  return <section aria-labelledby="project-search-results-heading"><h4 id="project-search-results-heading">Project results</h4><div className="external-scroll-list">{results.map((result) => <ProjectResult key={`${result.sourceId}:${result.iri}`} result={result} />)}</div></section>;
}

function ProjectResult({ result }: { result: WebSemanticSearchHit }) {
  const family = result.sourceId === "fibo-reuse" ? "PROJECT_REUSE" : result.locality.toLowerCase() === "imported" ? "IMPORTED" : "LOCAL";
  const status = family === "PROJECT_REUSE" ? "Applied project reuse" : family === "IMPORTED" ? "Imported into project" : "Local project concept";
  return <div className="list-item domain-project-result"><strong>{result.label}</strong><small><SourceBadge family={family} /> · {result.kind} · {status}</small></div>;
}

function RecommendationDetail({ detail, retrievalAvailability, acknowledged, onAcknowledged, onStage, staging }: any) {
  const snapshot = detail.difference.sourceSnapshot;
  const partial = snapshot.classification === "PartialMaterialization";
  const canStage = retrievalAvailability !== "Unavailable" && detail.recommendation.permittedActions.includes("Reuse");
  return <div><div className="section-heading compact"><div><SourceBadge family={detail.recommendation.sourceFamily} /><h4>{detail.recommendation.preferredLabel}</h4></div><span>{humanize(detail.difference.classification)}</span></div><p><strong>Canonical IRI:</strong> <code>{detail.recommendation.iri}</code></p><div className="domain-meaning-grid"><section><h5>FIBO source meaning</h5><p>{snapshot.statements.length} supported statements from <code>{snapshot.sourcePath}</code>.</p>{snapshot.omittedSourceAxioms.length ? <ul>{snapshot.omittedSourceAxioms.map((axiom: string) => <li key={axiom}>{axiom}</li>)}</ul> : <p>No omitted source axioms.</p>}</section><section><h5>Project meaning</h5><p>{detail.difference.projectStatements.length ? `${detail.difference.projectStatements.length} current project statements.` : "Not currently applied to this project."}</p><p>{detail.difference.addedProjectStatements.length} added · {detail.difference.removedSourceStatements.length} removed from source meaning</p></section></div>{partial ? <label className="confirmation-check"><input type="checkbox" checked={acknowledged} onChange={(event) => onAcknowledged(event.target.checked)} /> I reviewed every omitted source axiom and understand this is a partial materialization.</label> : null}{!canStage ? <p className="muted">Reuse cannot be staged while verified domain retrieval is unavailable.</p> : null}<button className="button primary" type="button" disabled={!canStage || staging.isPending || (partial && !acknowledged)} onClick={onStage}>Stage reuse for review</button>{staging.isSuccess ? <p role="status">Reuse added to the shared review queue.</p> : null}{staging.isError ? <p role="alert">Reuse could not be staged. Refresh the search and review its dependencies.</p> : null}</div>;
}

function SourceBadge({ family }: { family: string }) {
  const label = family === "OMG_COMMONS" ? "OMG Commons" : family === "FIBO" ? "FIBO" : family === "PROJECT_REUSE" ? "Project reuse" : family === "IMPORTED" ? "Imported" : "Local";
  return <span className={`source-badge source-badge-${family.toLowerCase().replace("_", "-")}`}>{label}</span>;
}

function RetrievalStatus({ availability }: { availability: string }) {
  const label = availability === "Full" ? "Full hybrid retrieval" : availability === "LexicalStructural" ? "Lexical and structural retrieval" : "Domain retrieval unavailable";
  return <span className={`retrieval-status retrieval-status-${availability.toLowerCase()}`}>{label}</span>;
}

function humanize(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2").replaceAll("_", " ");
}
