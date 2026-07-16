import type { Webview } from "vscode";
import { EDIT_KINDS } from "./proposalPreview";

export function renderWorkbench(webview: Webview, nonce: string): string {
  const editKindOptions = EDIT_KINDS
    .map((editKind) => `<option value="${editKind}">${editKind}</option>`)
    .join("");
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Entio Ontology Workbench</title>
  <style>
    body { color: var(--vscode-foreground); background: var(--vscode-editor-background); font-family: var(--vscode-font-family); padding: 16px; }
    button { color: var(--vscode-button-foreground); background: var(--vscode-button-background); border: 0; padding: 6px 12px; }
    button:hover { background: var(--vscode-button-hoverBackground); }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    main { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(240px, 2fr); gap: 20px; margin-top: 16px; }
    section { border-top: 1px solid var(--vscode-panel-border); padding-top: 12px; }
    ul { list-style: none; padding: 0; }
    li { margin: 4px 0; }
    .symbol-button { width: 100%; text-align: left; background: transparent; color: var(--vscode-foreground); }
    .symbol-button:hover, .symbol-button:focus { background: var(--vscode-list-hoverBackground); }
    #status { margin: 12px 0; white-space: pre-wrap; }
    #details { min-height: 100px; }
    form { display: grid; gap: 8px; max-width: 520px; }
    label { display: grid; gap: 4px; }
    .deletion-dependency { display: flex; align-items: flex-start; gap: 8px; }
    .deletion-dependency input { width: auto; margin: 2px 0 0; }
    [hidden] { display: none !important; }
    input, select { color: var(--vscode-input-foreground); background: var(--vscode-input-background); border: 1px solid var(--vscode-input-border); padding: 6px; }
    #edit-form, #preview { margin-top: 20px; }
    #preview-status { white-space: pre-wrap; }
    #preview-diff, #preview-validation, #preview-impact { margin: 8px 0; }
    #external-workbench { margin-top: 20px; }
    #external-results, #external-details, #external-dependencies, #external-proposal { margin: 8px 0; }
    #external-results { max-height: 520px; overflow-y: auto; }
    .external-pagination { display: flex; align-items: center; gap: 8px; margin: 12px 0; }
    .external-card { border: 1px solid var(--vscode-panel-border); padding: 8px; margin: 6px 0; }
    .external-card button { margin-right: 6px; }
    #approval-state { margin-left: 8px; }
    #open-source { margin-left: 8px; }
    @media (max-width: 640px) { main { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <h1>Entio Ontology Workbench</h1>
  <button id="refresh" type="button">Refresh</button>
  <div id="status" role="status">Loading project summary...</div>
  <section id="entity-search" aria-labelledby="entity-search-heading">
    <h2 id="entity-search-heading">Find an existing entity</h2>
    <p id="semantic-search-context">Search results are for inspection. Edit fields are verified when staged.</p>
    <form id="semantic-search-form">
      <label>Label or IRI <input id="semantic-search-query" type="search"></label>
      <label>Kind
        <select id="semantic-search-kind">
          <option value="">Any kind</option>
          <option value="Class">Class</option>
          <option value="ObjectProperty">Object property</option>
          <option value="DatatypeProperty">Datatype property</option>
          <option value="AnnotationProperty">Annotation property</option>
          <option value="Individual">Individual</option>
        </select>
      </label>
      <label>Source <select id="semantic-search-source"><option value="">All sources</option></select></label>
      <button id="semantic-search-submit" type="submit">Search</button>
    </form>
    <div id="semantic-search-results" aria-live="polite">No entity search requested.</div>
  </section>
  <main>
    <section aria-labelledby="sources-heading">
      <h2 id="sources-heading">Ontology Sources</h2>
      <ul id="sources"></ul>
    </section>
    <section aria-labelledby="symbols-heading">
      <h2 id="symbols-heading">Symbols</h2>
      <div id="symbol-groups"></div>
      <div id="details" aria-live="polite">Select a symbol to inspect its details.</div>
    </section>
  </main>
  <section id="phase4-workbench" aria-labelledby="phase4-heading">
    <h2 id="phase4-heading">Reasoning and SHACL</h2>
    <div class="phase4-actions">
      <button id="phase4-refresh" type="button">Refresh reasoning and validation</button>
    </div>
    <div id="reasoning-status" aria-live="polite">Reasoning has not been refreshed.</div>
    <div id="reasoning-results"></div>
    <div id="shacl-status" aria-live="polite">SHACL validation has not been run.</div>
    <div id="shacl-results"></div>
    <div id="shacl-shapes"></div>
  </section>
  <section id="external-workbench" aria-labelledby="external-heading">
    <h2 id="external-heading">External ontology catalog</h2>
    <button id="external-refresh" type="button">Refresh external catalog</button>
    <div id="external-status" aria-live="polite">External catalog has not been loaded.</div>
    <div id="external-manifest" class="external-card">Select FIBO to inspect the pinned release.</div>
    <form id="external-search-form">
      <label>Search external concepts <input id="external-search-query" type="search"></label>
      <label>Kind
        <select id="external-search-kind">
          <option value="">Any kind</option>
          <option value="Class">Class</option>
          <option value="ObjectProperty">Object property</option>
          <option value="DatatypeProperty">Datatype property</option>
        </select>
      </label>
      <label>Domain <input id="external-search-domain" type="text"></label>
      <label><input id="external-search-curated" type="checkbox"> Curated Foundations only</label>
      <button id="external-search-submit" type="submit">Search external catalog</button>
    </form>
    <div id="external-results" aria-live="polite">No external search requested.</div>
    <div id="external-details" aria-live="polite">Select an external element to inspect its details.</div>
    <div id="external-dependencies" aria-live="polite">No external dependencies inspected.</div>
    <div id="external-proposal" aria-live="polite">No external proposal prepared.</div>
  </section>
  <section id="edit-form" aria-labelledby="edit-heading">
    <h2 id="edit-heading">Preview Ontology Edit</h2>
    <form id="proposal-form">
      <label>Target source <select id="target-source" required></select></label>
      <label>Edit type <select id="edit-kind">${editKindOptions}</select></label>
      <div id="class-fields">
        <label>Class label <input id="class-label" type="text" required></label>
        <input id="class-iri" type="hidden">
      </div>
      <div id="property-fields" hidden>
        <div id="property-label-field" class="entity-picker">
          <label>Property label <input id="property-label" type="text"></label>
        </div>
        <input id="property-iri" type="hidden">
        <div id="property-domain-field" class="entity-picker">
          <label>Domain label <input id="property-domain-label" type="text"></label>
          <input id="property-domain-iri" type="hidden">
        </div>
        <div id="property-range-field" class="entity-picker">
          <label>Range label <input id="property-range-label" type="text"></label>
          <input id="property-range-iri" type="hidden">
        </div>
        <label id="property-datatype-field">Datatype
          <select id="property-datatype">
            <option value="">Select a datatype</option>
            <option value="http://www.w3.org/2001/XMLSchema#string">xsd:string</option>
            <option value="http://www.w3.org/2001/XMLSchema#boolean">xsd:boolean</option>
            <option value="http://www.w3.org/2001/XMLSchema#integer">xsd:integer</option>
            <option value="http://www.w3.org/2001/XMLSchema#decimal">xsd:decimal</option>
            <option value="http://www.w3.org/2001/XMLSchema#date">xsd:date</option>
            <option value="http://www.w3.org/2001/XMLSchema#dateTime">xsd:dateTime</option>
          </select>
        </label>
        <label><input id="property-replace" type="checkbox"> Replace existing domain or range</label>
      </div>
      <div id="individual-fields" hidden>
        <div id="individual-label-field" class="entity-picker">
          <label>Individual label <input id="individual-label" type="text"></label>
        </div>
        <input id="individual-iri" type="hidden">
        <div class="entity-picker">
          <label>Type label <input id="individual-type-label" type="text"></label>
          <input id="individual-type-iri" type="hidden">
        </div>
      </div>
      <div id="assertion-fields" hidden>
        <div class="entity-picker">
          <label>Subject label <input id="assertion-subject-label" type="text"></label>
          <input id="assertion-subject-iri" type="hidden">
        </div>
        <div class="entity-picker">
          <label>Property label <input id="assertion-property-label" type="text"></label>
          <input id="assertion-property-iri" type="hidden">
        </div>
        <div id="assertion-object-field" class="entity-picker">
          <label>Object label <input id="assertion-object-label" type="text"></label>
          <input id="assertion-object-iri" type="hidden">
        </div>
        <label id="assertion-value-field">Value <input id="assertion-value" type="text"></label>
        <label id="assertion-datatype-field">Datatype
          <select id="assertion-datatype">
            <option value="">Select a datatype</option>
            <option value="http://www.w3.org/2001/XMLSchema#string">xsd:string</option>
            <option value="http://www.w3.org/2001/XMLSchema#boolean">xsd:boolean</option>
            <option value="http://www.w3.org/2001/XMLSchema#integer">xsd:integer</option>
            <option value="http://www.w3.org/2001/XMLSchema#decimal">xsd:decimal</option>
            <option value="http://www.w3.org/2001/XMLSchema#date">xsd:date</option>
            <option value="http://www.w3.org/2001/XMLSchema#dateTime">xsd:dateTime</option>
          </select>
        </label>
        <label id="assertion-language-field">Language tag <input id="assertion-language" type="text"></label>
      </div>
      <div id="hierarchy-fields" hidden>
        <div class="entity-picker">
          <label>Class label <input id="hierarchy-class-label" type="text"></label>
          <input id="hierarchy-class-iri" type="hidden">
        </div>
        <div class="entity-picker">
          <label>Superclass label <input id="hierarchy-superclass-label" type="text"></label>
          <input id="hierarchy-superclass-iri" type="hidden">
        </div>
      </div>
      <div id="label-fields" hidden>
        <div class="entity-picker">
          <label>Entity label <input id="label-entity-label" type="text"></label>
          <input id="label-entity-iri" type="hidden">
        </div>
        <label>Label <input id="label-value" type="text"></label>
        <label>Language tag <input id="label-language" type="text"></label>
        <label><input id="label-replace" type="checkbox"> Replace existing labels</label>
      </div>
      <div id="semantic-fields" hidden>
        <div id="semantic-property-field" class="entity-picker">
          <label id="semantic-property-picker-label">Annotation property label <input id="semantic-property-label" type="text"></label>
          <input id="semantic-property-iri" type="hidden">
        </div>
        <div id="semantic-target-field" class="entity-picker">
          <label>Target label <input id="semantic-target-label" type="text"></label>
          <input id="semantic-target-iri" type="hidden">
        </div>
        <label id="semantic-label-field">Label <input id="semantic-label" type="text"></label>
        <label id="semantic-definition-field">Definition <input id="semantic-definition" type="text"></label>
        <label id="semantic-value-field">Value <input id="semantic-value" type="text"></label>
        <label id="semantic-existing-field">Existing value <input id="semantic-existing" type="text"></label>
        <label id="semantic-replacement-field">Replacement value <input id="semantic-replacement" type="text"></label>
      </div>
      <p id="edit-form-placeholder" hidden>Additional edit forms are provided by the workbench edit modes.</p>
      <button id="preview-submit" type="button">Stage change</button>
      <div id="edit-status" aria-live="polite">No edit staged.</div>
    </form>
  </section>
  <section id="deletion-review" aria-labelledby="deletion-heading">
    <h2 id="deletion-heading">Review deletion dependencies</h2>
    <form id="deletion-form">
      <label>Entity label <input id="deletion-label" type="text"></label>
      <input id="deletion-iri" type="hidden">
      <label>Kind
        <select id="deletion-kind">
          <option value="">Any kind</option>
          <option value="Class">Class</option>
          <option value="Property">Property</option>
          <option value="Individual">Individual</option>
        </select>
      </label>
      <button id="inspect-deletion" type="submit">Inspect dependencies</button>
    </form>
    <div id="deletion-dependencies" aria-live="polite">No deletion review requested.</div>
    <button id="preview-deletion" type="button" disabled>Stage deletion</button>
  </section>
  <section id="staged-changes" aria-labelledby="staged-heading">
    <h2 id="staged-heading">Staged changes</h2>
    <div id="staged-status" aria-live="polite">No changes staged.</div>
    <ul id="staged-list"></ul>
    <button id="cancel-staged-edit" type="button" hidden>Cancel staged edit</button>
    <button id="preview-all-changes" type="button" disabled>Preview all changes</button>
  </section>
  <section id="preview" aria-labelledby="preview-heading">
    <h2 id="preview-heading">Proposal Preview</h2>
    <div id="preview-status">No proposal preview requested.</div>
    <div id="preview-impact"></div>
    <div id="preview-diff"></div>
    <div id="preview-validation"></div>
    <button id="approve" type="button" disabled>Approve and apply</button>
    <button id="reject" type="button" disabled>Reject</button>
    <button id="open-source" type="button" disabled>Open changed source</button>
    <span id="approval-state">Preview a proposal to review approval.</span>
  </section>
  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    const status = document.getElementById("status");
    const sources = document.getElementById("sources");
    const symbolGroups = document.getElementById("symbol-groups");
    const details = document.getElementById("details");
    const phase4Refresh = document.getElementById("phase4-refresh");
    const reasoningStatus = document.getElementById("reasoning-status");
    const reasoningResults = document.getElementById("reasoning-results");
    const shaclStatus = document.getElementById("shacl-status");
    const shaclResults = document.getElementById("shacl-results");
    const shaclShapes = document.getElementById("shacl-shapes");
    const semanticSearchForm = document.getElementById("semantic-search-form");
    const semanticSearchQuery = document.getElementById("semantic-search-query");
    const semanticSearchKind = document.getElementById("semantic-search-kind");
    const semanticSearchSource = document.getElementById("semantic-search-source");
    const semanticSearchResults = document.getElementById("semantic-search-results");
    const externalRefresh = document.getElementById("external-refresh");
    const externalStatus = document.getElementById("external-status");
    const externalManifest = document.getElementById("external-manifest");
    const externalSearchForm = document.getElementById("external-search-form");
    const externalSearchQuery = document.getElementById("external-search-query");
    const externalSearchKind = document.getElementById("external-search-kind");
    const externalSearchDomain = document.getElementById("external-search-domain");
    const externalSearchCurated = document.getElementById("external-search-curated");
    const externalResults = document.getElementById("external-results");
    const externalDetails = document.getElementById("external-details");
    const externalDependencies = document.getElementById("external-dependencies");
    const externalProposal = document.getElementById("external-proposal");
    const proposalForm = document.getElementById("proposal-form");
    const targetSource = document.getElementById("target-source");
    const editKind = document.getElementById("edit-kind");
    const classFields = document.getElementById("class-fields");
    const editFormPlaceholder = document.getElementById("edit-form-placeholder");
    const previewSubmit = document.getElementById("preview-submit");
    const editStatus = document.getElementById("edit-status");
    const classIri = document.getElementById("class-iri");
    const classLabel = document.getElementById("class-label");
    const propertyFields = document.getElementById("property-fields");
    const propertyIri = document.getElementById("property-iri");
    const propertyLabelField = document.getElementById("property-label-field");
    const propertyLabel = document.getElementById("property-label");
    const propertyDomainField = document.getElementById("property-domain-field");
    const propertyDomainIri = document.getElementById("property-domain-iri");
    const propertyDomainLabel = document.getElementById("property-domain-label");
    const propertyRangeField = document.getElementById("property-range-field");
    const propertyRangeIri = document.getElementById("property-range-iri");
    const propertyRangeLabel = document.getElementById("property-range-label");
    const propertyDatatypeField = document.getElementById("property-datatype-field");
    const propertyDatatype = document.getElementById("property-datatype");
    const propertyReplace = document.getElementById("property-replace");
    const individualFields = document.getElementById("individual-fields");
    const individualIri = document.getElementById("individual-iri");
    const individualLabelField = document.getElementById("individual-label-field");
    const individualTypeIri = document.getElementById("individual-type-iri");
    const individualTypeLabel = document.getElementById("individual-type-label");
    const individualLabel = document.getElementById("individual-label");
    const assertionFields = document.getElementById("assertion-fields");
    const assertionSubjectIri = document.getElementById("assertion-subject-iri");
    const assertionSubjectLabel = document.getElementById("assertion-subject-label");
    const assertionPropertyIri = document.getElementById("assertion-property-iri");
    const assertionPropertyLabel = document.getElementById("assertion-property-label");
    const assertionObjectField = document.getElementById("assertion-object-field");
    const assertionObjectIri = document.getElementById("assertion-object-iri");
    const assertionObjectLabel = document.getElementById("assertion-object-label");
    const assertionValueField = document.getElementById("assertion-value-field");
    const assertionValue = document.getElementById("assertion-value");
    const assertionDatatypeField = document.getElementById("assertion-datatype-field");
    const assertionDatatype = document.getElementById("assertion-datatype");
    const assertionLanguageField = document.getElementById("assertion-language-field");
    const assertionLanguage = document.getElementById("assertion-language");
    const hierarchyFields = document.getElementById("hierarchy-fields");
    const hierarchyClassIri = document.getElementById("hierarchy-class-iri");
    const hierarchyClassLabel = document.getElementById("hierarchy-class-label");
    const hierarchySuperclassIri = document.getElementById("hierarchy-superclass-iri");
    const hierarchySuperclassLabel = document.getElementById("hierarchy-superclass-label");
    const labelFields = document.getElementById("label-fields");
    const labelEntityIri = document.getElementById("label-entity-iri");
    const labelEntityLabel = document.getElementById("label-entity-label");
    const labelValue = document.getElementById("label-value");
    const labelLanguage = document.getElementById("label-language");
    const labelReplace = document.getElementById("label-replace");
    const semanticFields = document.getElementById("semantic-fields");
    const semanticPropertyField = document.getElementById("semantic-property-field");
    const semanticPropertyPickerLabel = document.getElementById("semantic-property-picker-label");
    const semanticPropertyIri = document.getElementById("semantic-property-iri");
    const semanticPropertyLabel = document.getElementById("semantic-property-label");
    const semanticTargetField = document.getElementById("semantic-target-field");
    const semanticTargetIri = document.getElementById("semantic-target-iri");
    const semanticTargetLabel = document.getElementById("semantic-target-label");
    const semanticLabelField = document.getElementById("semantic-label-field");
    const semanticLabel = document.getElementById("semantic-label");
    const semanticDefinitionField = document.getElementById("semantic-definition-field");
    const semanticDefinition = document.getElementById("semantic-definition");
    const semanticValueField = document.getElementById("semantic-value-field");
    const semanticValue = document.getElementById("semantic-value");
    const semanticExistingField = document.getElementById("semantic-existing-field");
    const semanticExisting = document.getElementById("semantic-existing");
    const semanticReplacementField = document.getElementById("semantic-replacement-field");
    const semanticReplacement = document.getElementById("semantic-replacement");
    const previewStatus = document.getElementById("preview-status");
    const previewImpact = document.getElementById("preview-impact");
    const previewDiff = document.getElementById("preview-diff");
    const previewValidation = document.getElementById("preview-validation");
    const approve = document.getElementById("approve");
    const reject = document.getElementById("reject");
    const openSource = document.getElementById("open-source");
    const approvalState = document.getElementById("approval-state");
    const deletionForm = document.getElementById("deletion-form");
    const deletionLabel = document.getElementById("deletion-label");
    const deletionIri = document.getElementById("deletion-iri");
    const deletionKind = document.getElementById("deletion-kind");
    const deletionDependencies = document.getElementById("deletion-dependencies");
    const previewDeletion = document.getElementById("preview-deletion");
    const stagedStatus = document.getElementById("staged-status");
    const stagedList = document.getElementById("staged-list");
    const cancelStagedEdit = document.getElementById("cancel-staged-edit");
    const previewAllChanges = document.getElementById("preview-all-changes");
    let currentRequest;
    let currentPreview;
    let changedSource;
    let stagedChanges = [];
    let stagedSequence = 1;
    let editingStagedEntry;
    let currentCombinedPreview;
    let currentDeletionReview;
    let selectedDeletionKeys = new Set();
    let currentModel;
    let selectedSemanticIri;
    let selectedExternal;
    let selectedExternalDependencies = new Set();
    let externalTargetOntologyIri = "";
    let externalBrowseRequest;
    let externalBrowsePage = 0;
    let externalSearchRequest;
    let externalSearchPage = 0;
    let pendingIriGenerationKey;
    let pendingPreviewAfterIri = false;
    let pendingEntityResolution = false;
    document.getElementById("refresh").addEventListener("click", () => vscode.postMessage({ type: "refresh" }));
    phase4Refresh.addEventListener("click", () => {
      reasoningStatus.textContent = "Refreshing reasoning and SHACL validation...";
      vscode.postMessage({ type: "phase4-refresh" });
    });

    [
      [classLabel, classIri],
      [propertyLabel, propertyIri],
      [propertyDomainLabel, propertyDomainIri],
      [propertyRangeLabel, propertyRangeIri],
      [individualLabel, individualIri],
      [individualTypeLabel, individualTypeIri],
      [assertionSubjectLabel, assertionSubjectIri],
      [assertionPropertyLabel, assertionPropertyIri],
      [assertionObjectLabel, assertionObjectIri],
      [semanticLabel, semanticPropertyIri],
      [semanticPropertyLabel, semanticPropertyIri],
      [semanticTargetLabel, semanticTargetIri],
      [hierarchyClassLabel, hierarchyClassIri],
      [hierarchySuperclassLabel, hierarchySuperclassIri],
      [labelEntityLabel, labelEntityIri],
    ].forEach(([labelInput, iriInput]) => {
      labelInput.addEventListener("input", () => {
        iriInput.value = "";
        pendingEntityResolution = false;
        pendingIriGenerationKey = undefined;
        pendingPreviewAfterIri = false;
      });
    });

    semanticSearchForm.addEventListener("submit", (event) => {
      event.preventDefault();
      const query = semanticSearchQuery.value.trim();
      if (query === "") {
        semanticSearchResults.textContent = "Enter text to search semantic descriptions.";
        return;
      }
      vscode.postMessage({
        type: "semantic-search",
        payload: {
          query,
          kind: semanticSearchKind.value || undefined,
          sourceId: semanticSearchSource.value || undefined,
        },
      });
      semanticSearchResults.textContent = "Searching semantic descriptions...";
    });

    externalRefresh.addEventListener("click", () => {
      externalStatus.textContent = "Loading the approved read-only FIBO package...";
      vscode.postMessage({ type: "external-refresh" });
    });

    externalSearchForm.addEventListener("submit", (event) => {
      event.preventDefault();
      const query = externalSearchQuery.value.trim();
      if (query === "") {
        externalResults.textContent = "Enter text to search the external catalog.";
        return;
      }
      externalResults.textContent = "Searching the pinned external catalog...";
      vscode.postMessage({
        type: "external-search",
        payload: {
          query,
          kind: externalSearchKind.value || undefined,
          domain: externalSearchDomain.value.trim() || undefined,
          curatedOnly: externalSearchCurated.checked,
          page: 0,
          pageSize: 25,
        },
      });
      externalSearchRequest = {
        query,
        kind: externalSearchKind.value || undefined,
        domain: externalSearchDomain.value.trim() || undefined,
        curatedOnly: externalSearchCurated.checked,
        pageSize: 25,
      };
      externalSearchPage = 0;
    });

    function requestIriGeneration() {
      const kind = entityKindForEdit();
      const label = labelForEdit();
      if (!kind || !label) return false;
      pendingIriGenerationKey = editKind.value + "|" + label.trim();
      vscode.postMessage({ type: "generate-iri", payload: { label, kind } });
      editStatus.textContent = "Generating deterministic IRI...";
      return true;
    }

    function requestEntityResolutions(entries) {
      pendingEntityResolution = true;
      editStatus.textContent = "Verifying entity labels...";
      vscode.postMessage({
        type: "resolve-edit-entities",
        payload: {
          sourceId: targetSource.value,
          entries,
        },
      });
      return true;
    }

    deletionForm.addEventListener("submit", (event) => {
      event.preventDefault();
      if (deletionLabel.value === "" && deletionIri.value === "") {
        deletionDependencies.textContent = "Enter an entity label or IRI to inspect deletion dependencies.";
        return;
      }
      requestDeletionReview({
        label: deletionLabel.value || undefined,
        iri: deletionIri.value || undefined,
        kind: deletionKind.value || undefined,
        sourceId: targetSource.value || undefined,
      });
    });

    previewDeletion.addEventListener("click", () => {
      if (!currentDeletionReview || !canPreviewDeletion()) return;
      const target = currentDeletionReview.target;
      const request = {
        targetSourceId: target?.sourceId || targetSource.value,
        editKind: "delete-entity",
        entityIri: target?.iri || deletionIri.value,
        selectedDependencyKeys: Array.from(selectedDeletionKeys),
      };
      currentRequest = request;
      vscode.postMessage({ type: "deletion-preview", payload: request });
      previewStatus.textContent = "Requesting deletion proposal preview...";
      previewDeletion.disabled = true;
    });

    cancelStagedEdit.addEventListener("click", () => {
      if (editingStagedEntry) {
        stagedChanges = stagedChanges.concat(editingStagedEntry).sort((first, second) => first.order - second.order);
        editingStagedEntry = undefined;
        currentRequest = undefined;
        clearEditForm();
        renderStagedList();
      }
    });

    previewAllChanges.addEventListener("click", () => {
      if (stagedChanges.length === 0) return;
      vscode.postMessage({
        type: "combined-preview",
        payload: stagedChanges.map((entry) => entry.request),
      });
      previewStatus.textContent = "Requesting combined proposal preview...";
    });

    function entityKindForEdit() {
      if (editKind.value === "create-class") return "Class";
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) return "Property";
      if (editKind.value === "create-annotation-property") return "Property";
      if (editKind.value === "create-individual") return "Individual";
      return undefined;
    }

    function labelForEdit() {
      if (editKind.value === "create-class") return classLabel.value;
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) return propertyLabel.value;
      if (editKind.value === "create-annotation-property") return semanticLabel.value;
      if (editKind.value === "create-individual") return individualLabel.value;
      return undefined;
    }

    function clearEditForm() {
      const source = targetSource.value;
      pendingIriGenerationKey = undefined;
      pendingPreviewAfterIri = false;
      pendingEntityResolution = false;
      proposalForm.reset();
      targetSource.value = source;
      updateEditFormMode();
      editStatus.textContent = "No edit staged.";
    }

    function requestDeletionReview(selector) {
      if (selector.sourceId) targetSource.value = selector.sourceId;
      deletionLabel.value = selector.label || "";
      deletionIri.value = selector.iri || "";
      deletionKind.value = selector.kind || "";
      currentDeletionReview = undefined;
      selectedDeletionKeys = new Set();
      previewDeletion.disabled = true;
      vscode.postMessage({
        type: "deletion-dependencies",
        payload: selector,
      });
      deletionDependencies.textContent = "Inspecting deletion dependencies...";
    }

    function canPreviewDeletion() {
      if (!currentDeletionReview ||
          !["Safe", "RequiresExplicitDependencies"].includes(currentDeletionReview.status) ||
          currentDeletionReview.invalidSelectedDependencyKeys.length > 0) return false;
      return currentDeletionReview.dependentStatements.every((statement) =>
        typeof statement.dependencyKey === "string" && selectedDeletionKeys.has(statement.dependencyKey));
    }

    function updateDeletionPreviewState() {
      previewDeletion.disabled = !canPreviewDeletion();
    }

    function labelForIri(iri) {
      if (!iri) return "";
      const symbol = currentModel?.symbolGroups.flatMap((group) => group.symbols).find((candidate) => candidate.iri === iri);
      return symbol ? preferredSymbolLabel(symbol) : readableLocalName(iri);
    }

    function preferredSymbolLabel(symbol) {
      return symbol.label || readableLocalName(symbol.iri);
    }

    function readableLocalName(iri) {
      const separator = Math.max(iri.lastIndexOf("#"), iri.lastIndexOf("/"));
      const localName = separator >= 0 && separator < iri.length - 1 ? iri.slice(separator + 1) : iri;
      return localName.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]/g, " ");
    }

    function setEntityPicker(labelInput, iriInput, iri, label) {
      iriInput.value = iri || "";
      labelInput.value = iri ? (label || labelForIri(iri)) : "";
      labelInput.dataset.resolvedLabel = labelInput.value;
    }

    function restoreEditForm(request) {
      editKind.value = request.editKind;
      updateEditFormMode();
      targetSource.value = request.targetSourceId;
      classIri.value = request.classIri || "";
      classLabel.value = request.label || "";
      propertyIri.value = request.propertyIri || "";
      propertyLabel.value = request.label || labelForIri(request.propertyIri);
      setEntityPicker(propertyDomainLabel, propertyDomainIri, request.domainIri);
      setEntityPicker(propertyRangeLabel, propertyRangeIri, request.rangeIri);
      propertyDatatype.value = request.datatype || "";
      propertyReplace.checked = request.replaceExisting === true;
      individualIri.value = request.individualIri || "";
      setEntityPicker(individualTypeLabel, individualTypeIri, request.typeIri);
      individualLabel.value = request.label || labelForIri(request.individualIri);
      setEntityPicker(assertionSubjectLabel, assertionSubjectIri, request.subjectIri);
      setEntityPicker(assertionPropertyLabel, assertionPropertyIri, request.propertyIri);
      setEntityPicker(assertionObjectLabel, assertionObjectIri, request.objectIri);
      assertionValue.value = request.value || "";
      assertionDatatype.value = request.datatype || "";
      assertionLanguage.value = request.language || "";
      setEntityPicker(hierarchyClassLabel, hierarchyClassIri, request.classIri);
      setEntityPicker(hierarchySuperclassLabel, hierarchySuperclassIri, request.superclassIri);
      setEntityPicker(labelEntityLabel, labelEntityIri, request.entityIri);
      labelValue.value = request.label || "";
      labelLanguage.value = request.language || "";
      labelReplace.checked = request.replaceExisting === true;
      setEntityPicker(semanticPropertyLabel, semanticPropertyIri, request.propertyIri);
      setEntityPicker(semanticTargetLabel, semanticTargetIri, request.targetIri);
      semanticLabel.value = request.label || "";
      semanticDefinition.value = request.definition || "";
      semanticValue.value = request.value || "";
      semanticExisting.value = request.existing || "";
      semanticReplacement.value = request.replacement || "";
    }

    function stagePreview(preview) {
      if (!currentRequest || !preview.canApprove) return;
      const entry = {
        id: editingStagedEntry ? editingStagedEntry.id : "staged-" + stagedSequence++,
        order: editingStagedEntry ? editingStagedEntry.order : stagedChanges.length,
        request: currentRequest,
        preview,
        summary: preview.targetSourceId + " · " + currentRequest.editKind + " · " + (currentRequest.label || currentRequest.classIri || currentRequest.propertyIri || currentRequest.individualIri || currentRequest.entityIri || "edit"),
      };
      stagedChanges = editingStagedEntry
        ? stagedChanges.concat(entry).sort((first, second) => first.order - second.order)
        : stagedChanges.concat(entry);
      stagedChanges = stagedChanges.map((value, index) => ({ ...value, order: index }));
      editingStagedEntry = undefined;
      currentRequest = undefined;
      currentPreview = undefined;
      clearEditForm();
      renderStagedList();
      editStatus.textContent = "Change staged successfully.";
      previewStatus.textContent = "No combined proposal preview requested.";
      previewImpact.textContent = "The source file has not changed.";
      previewDiff.replaceChildren();
      previewValidation.replaceChildren();
      approve.disabled = true;
      reject.disabled = true;
      openSource.disabled = true;
      approvalState.textContent = "Review the staged list before the combined preview.";
    }

    function renderStagedList() {
      stagedList.replaceChildren();
      stagedStatus.textContent = stagedChanges.length === 0
        ? "No changes staged."
        : stagedChanges.length + " change(s) staged for combined review.";
      cancelStagedEdit.hidden = !editingStagedEntry;
      previewAllChanges.disabled = stagedChanges.length === 0 || Boolean(editingStagedEntry);
      stagedChanges.forEach((entry) => {
        const item = document.createElement("li");
        const summary = document.createElement("span");
        summary.textContent = entry.summary + " · " + entry.preview.validationStatus;
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = "Edit";
        edit.addEventListener("click", () => {
          editingStagedEntry = entry;
          stagedChanges = stagedChanges.filter((value) => value.id !== entry.id);
          restoreEditForm(entry.request);
          currentRequest = entry.request;
          stagedStatus.textContent = "Editing " + entry.summary + ". Re-preview to return it to the staged list.";
          renderStagedList();
        });
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "Remove";
        remove.addEventListener("click", () => {
          stagedChanges = stagedChanges.filter((value) => value.id !== entry.id).map((value, index) => ({ ...value, order: index }));
          renderStagedList();
        });
        item.append(summary, edit, remove);
        stagedList.append(item);
      });
    }

    function updateEditFormMode() {
      const createClass = editKind.value === "create-class";
      const propertyMode = [
        "create-object-property",
        "create-datatype-property",
        "set-property-domain",
        "set-property-range",
      ].includes(editKind.value);
      const createProperty = editKind.value === "create-object-property" || editKind.value === "create-datatype-property";
      const datatypeMode = editKind.value === "create-datatype-property" || editKind.value === "set-property-range";
      const domainMode = createProperty || editKind.value === "set-property-domain";
      const rangeMode = createProperty || editKind.value === "set-property-range";
      const individualMode = editKind.value === "create-individual" || editKind.value === "assign-individual-type";
      const assertionMode = editKind.value === "add-object-property-assertion" || editKind.value === "add-datatype-property-assertion";
      const datatypeAssertion = editKind.value === "add-datatype-property-assertion";
      const hierarchyMode = editKind.value === "add-superclass" || editKind.value === "remove-superclass";
      const labelMode = editKind.value === "set-entity-label";
      const semanticMode = [
        "create-annotation-property", "add-definition", "replace-definition", "remove-definition",
        "add-alternate-label", "replace-alternate-label", "remove-alternate-label",
        "add-annotation", "remove-annotation",
      ].includes(editKind.value);
      const createAnnotationProperty = editKind.value === "create-annotation-property";
      const definitionMode = ["add-definition", "replace-definition", "remove-definition"].includes(editKind.value);
      const alternateLabelMode = ["add-alternate-label", "replace-alternate-label", "remove-alternate-label"].includes(editKind.value);
      const annotationMode = ["add-annotation", "remove-annotation"].includes(editKind.value);
      const replaceSemanticValue = ["replace-definition", "replace-alternate-label"].includes(editKind.value);
      const formMode = createClass || propertyMode || individualMode || assertionMode || hierarchyMode || labelMode || semanticMode;
      classFields.hidden = !createClass;
      propertyFields.hidden = !propertyMode;
      individualFields.hidden = !individualMode;
      assertionFields.hidden = !assertionMode;
      hierarchyFields.hidden = !hierarchyMode;
      labelFields.hidden = !labelMode;
      semanticFields.hidden = !semanticMode;
      editFormPlaceholder.hidden = formMode;
      previewSubmit.disabled = !formMode;
      classIri.required = false;
      classLabel.required = createClass;
      propertyIri.required = false;
      propertyLabel.required = createProperty;
      propertyLabelField.hidden = !propertyMode;
      propertyDomainField.hidden = !domainMode;
      propertyRangeField.hidden = !rangeMode;
      propertyDatatypeField.hidden = !datatypeMode;
      propertyReplace.parentElement.hidden = editKind.value !== "set-property-domain" && editKind.value !== "set-property-range";
      propertyDomainIri.required = false;
      propertyRangeIri.required = false;
      individualIri.required = false;
      individualLabel.required = editKind.value === "create-individual";
      individualLabelField.hidden = !individualMode;
      individualTypeIri.required = false;
      assertionSubjectIri.required = false;
      assertionPropertyIri.required = false;
      assertionObjectField.hidden = !assertionMode || datatypeAssertion;
      assertionValueField.hidden = !datatypeAssertion;
      assertionDatatypeField.hidden = !datatypeAssertion;
      assertionLanguageField.hidden = !datatypeAssertion;
      assertionObjectIri.required = false;
      assertionValue.required = datatypeAssertion;
      hierarchyClassIri.required = false;
      hierarchySuperclassIri.required = false;
      labelEntityIri.required = false;
      labelValue.required = labelMode;
      semanticPropertyField.hidden = !createAnnotationProperty && !annotationMode;
      semanticPropertyPickerLabel.hidden = createAnnotationProperty;
      semanticTargetField.hidden = !definitionMode && !alternateLabelMode && !annotationMode;
      semanticLabelField.hidden = !createAnnotationProperty;
      semanticDefinitionField.hidden = !createAnnotationProperty;
      semanticValueField.hidden = !definitionMode && !alternateLabelMode && !annotationMode;
      semanticExistingField.hidden = !replaceSemanticValue;
      semanticReplacementField.hidden = !replaceSemanticValue;
      semanticPropertyIri.required = false;
      semanticTargetIri.required = false;
      semanticLabel.required = false;
      semanticDefinition.required = false;
      semanticValue.required = (definitionMode || alternateLabelMode || annotationMode) &&
        !replaceSemanticValue;
      semanticExisting.required = replaceSemanticValue;
      semanticReplacement.required = replaceSemanticValue;
    }
    editKind.addEventListener("change", () => {
      updateEditFormMode();
      pendingIriGenerationKey = undefined;
      pendingPreviewAfterIri = false;
      pendingEntityResolution = false;
    });
    propertyDatatype.addEventListener("change", updateEditFormMode);
    assertionDatatype.addEventListener("change", updateEditFormMode);
    updateEditFormMode();

    function collectEntityResolutions(editState) {
      const {
        propertyMode,
        createProperty,
        hierarchyMode,
        labelMode,
        annotationMode,
        definitionMode,
        alternateLabelMode,
      } = editState;
      const entries = [];
      const missing = [];
      const add = (field, labelInput, iriInput, kind, description, required = true) => {
        if (iriInput.value) return;
        const label = labelInput.value.trim();
        if (!label) {
          if (required) missing.push(description);
          return;
        }
        entries.push({ field, label, kind, description });
      };
      const propertyExisting = propertyMode && !createProperty;
      if (propertyExisting) add("property-iri", propertyLabel, propertyIri, "Property", "property");
      if (editKind.value === "set-property-domain") add("property-domain-iri", propertyDomainLabel, propertyDomainIri, "Class", "domain");
      if (editKind.value === "set-property-range" || createProperty) {
        add("property-range-iri", propertyRangeLabel, propertyRangeIri, "Class", "range", editKind.value === "set-property-range" && !propertyDatatype.value);
      }
      if (createProperty) {
        add("property-domain-iri", propertyDomainLabel, propertyDomainIri, "Class", "domain", false);
      }
      if (editKind.value === "create-individual") add("individual-type-iri", individualTypeLabel, individualTypeIri, "Class", "individual type", false);
      if (editKind.value === "assign-individual-type") {
        add("individual-iri", individualLabel, individualIri, "Individual", "individual");
        add("individual-type-iri", individualTypeLabel, individualTypeIri, "Class", "individual type");
      }
      if (editKind.value === "add-object-property-assertion") {
        add("assertion-subject-iri", assertionSubjectLabel, assertionSubjectIri, undefined, "assertion subject");
        add("assertion-property-iri", assertionPropertyLabel, assertionPropertyIri, "Property", "assertion property");
        add("assertion-object-iri", assertionObjectLabel, assertionObjectIri, undefined, "assertion object");
      }
      if (editKind.value === "add-datatype-property-assertion") {
        add("assertion-subject-iri", assertionSubjectLabel, assertionSubjectIri, undefined, "assertion subject");
        add("assertion-property-iri", assertionPropertyLabel, assertionPropertyIri, "Property", "assertion property");
      }
      if (hierarchyMode) {
        add("hierarchy-class-iri", hierarchyClassLabel, hierarchyClassIri, "Class", "class");
        add("hierarchy-superclass-iri", hierarchySuperclassLabel, hierarchySuperclassIri, "Class", "superclass");
      }
      if (labelMode) add("label-entity-iri", labelEntityLabel, labelEntityIri, undefined, "entity");
      if (annotationMode) {
        add("semantic-property-iri", semanticPropertyLabel, semanticPropertyIri, "AnnotationProperty", "annotation property");
        add("semantic-target-iri", semanticTargetLabel, semanticTargetIri, undefined, "annotation target");
      } else if (definitionMode || alternateLabelMode) {
        add("semantic-target-iri", semanticTargetLabel, semanticTargetIri, undefined, "target entity");
      }
      return { entries, missing };
    }

    function submitProposalPreview() {
      const propertyMode = !propertyFields.hidden;
      if (editKind.value === "set-property-range" && propertyRangeIri.value === "" && propertyDatatype.value === "") {
        editStatus.textContent = "A range IRI or datatype is required.";
        return;
      }
      const individualMode = !individualFields.hidden;
      const assertionMode = !assertionFields.hidden;
      const hierarchyMode = !hierarchyFields.hidden;
      const labelMode = !labelFields.hidden;
      const semanticMode = !semanticFields.hidden;
      const createProperty = editKind.value === "create-object-property" || editKind.value === "create-datatype-property";
      const createAnnotationProperty = editKind.value === "create-annotation-property";
      const definitionMode = ["add-definition", "replace-definition", "remove-definition"].includes(editKind.value);
      const alternateLabelMode = ["add-alternate-label", "replace-alternate-label", "remove-alternate-label"].includes(editKind.value);
      const annotationMode = ["add-annotation", "remove-annotation"].includes(editKind.value);
      if (editKind.value === "add-datatype-property-assertion" && assertionValue.value === "") {
        editStatus.textContent = "A literal value is required.";
        return;
      }
      if (editKind.value !== "create-class" && !propertyMode && !individualMode && !assertionMode && !hierarchyMode && !labelMode && !semanticMode) return;
      if (editKind.value === "create-class" && !classIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      if (createProperty && !propertyIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      if (editKind.value === "set-property-range" && propertyRangeIri.value === "" && propertyDatatype.value === "") return;
      if (editKind.value === "create-individual" && !individualIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      if (createAnnotationProperty && !semanticPropertyIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      const entityResolutions = collectEntityResolutions({
        propertyMode,
        createProperty,
        hierarchyMode,
        labelMode,
        annotationMode,
        definitionMode,
        alternateLabelMode,
      });
      if (entityResolutions.missing.length > 0) {
        editStatus.textContent = "Enter a " + entityResolutions.missing[0] + " label before staging.";
        return;
      }
      if (entityResolutions.entries.length > 0) {
        if (!pendingEntityResolution) requestEntityResolutions(entityResolutions.entries);
        return;
      }
      const payload = editKind.value === "create-class"
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            classIri: classIri.value,
            label: classLabel.value,
          }
        : semanticMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            propertyIri: semanticPropertyIri.value,
            targetIri: semanticTargetIri.value,
            label: semanticLabel.value,
            definition: semanticDefinition.value,
            value: semanticValue.value,
            existing: semanticExisting.value,
            replacement: semanticReplacement.value,
          }
        : propertyMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            propertyIri: propertyIri.value,
            label: propertyLabel.value,
            domainIri: propertyDomainIri.value,
            rangeIri: propertyRangeIri.value,
            datatype: propertyDatatype.value,
            replaceExisting: propertyReplace.checked,
          }
        : individualMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            individualIri: individualIri.value,
            typeIri: individualTypeIri.value,
            label: individualLabel.value,
          }
        : hierarchyMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            classIri: hierarchyClassIri.value,
            superclassIri: hierarchySuperclassIri.value,
          }
        : labelMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            entityIri: labelEntityIri.value,
            label: labelValue.value,
            language: labelLanguage.value,
            replaceExisting: labelReplace.checked,
          }
        : {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            subjectIri: assertionSubjectIri.value,
            propertyIri: assertionPropertyIri.value,
            objectIri: assertionObjectIri.value,
            value: assertionValue.value,
            datatype: assertionDatatype.value,
            language: assertionLanguage.value,
          };
      currentRequest = payload;
      vscode.postMessage({
        type: semanticMode ? "semantic-preview" : "proposal-preview",
        payload,
      });
      editStatus.textContent = "Preparing staged change...";
    }

    previewSubmit.addEventListener("click", () => {
      try {
        submitProposalPreview();
      } catch (error) {
        renderPreviewError(error instanceof Error ? error.message : "The proposal preview could not be started.");
      }
    });

    approve.addEventListener("click", () => {
      if (currentCombinedPreview && currentCombinedPreview.canApprove) {
        vscode.postMessage({ type: "combined-action", action: "apply" });
        return;
      }
      if (currentPreview && currentPreview.canApprove && currentRequest) {
        vscode.postMessage({ type: "proposal-action", action: "apply", payload: currentRequest });
      }
    });

    reject.addEventListener("click", () => {
      if (currentCombinedPreview) {
        vscode.postMessage({ type: "combined-action", action: "reject" });
        return;
      }
      if (currentRequest) {
        vscode.postMessage({ type: "proposal-action", action: "reject", payload: currentRequest });
      }
    });

    openSource.addEventListener("click", () => {
      if (changedSource) {
        vscode.postMessage({ type: "open-source", path: changedSource });
      }
    });

    function renderList(container, items, emptyText) {
      container.replaceChildren();
      if (items.length === 0) {
        container.textContent = emptyText;
        return;
      }
      const list = document.createElement("ul");
      items.forEach((item) => {
        const entry = document.createElement("li");
        entry.textContent = item;
        list.append(entry);
      });
      container.append(list);
    }

    function renderPreview(preview) {
      currentPreview = preview;
      previewStatus.textContent = preview.status + " · " + preview.targetSourceId + " · " + preview.previewTripleCount + " graph triple(s)";
      previewImpact.textContent = "Affected files: " + (preview.affectedPaths.join(", ") || "none");
      renderList(previewDiff, preview.diffEntries.map((entry) => entry.description), "No semantic changes.");
      renderList(previewValidation, preview.validationIssues, preview.validationStatus);
      approve.disabled = !preview.canApprove;
      reject.disabled = false;
      openSource.disabled = true;
      changedSource = undefined;
      approvalState.textContent = preview.canApprove
        ? "Preview is valid; adding it to the staged list."
        : preview.approvalDisabledReason;
      stagePreview(preview);
    }

    function renderPhase4State(state) {
      if (state.reasoning) renderReasoning(state.reasoning);
      else reasoningStatus.textContent = "Reasoning result unavailable.";
      if (state.validation) renderShaclValidation(state.validation);
      else shaclStatus.textContent = "SHACL validation result unavailable.";
      if (state.shapes) renderShaclShapes(state.shapes);
      else shaclShapes.textContent = "SHACL shape descriptors unavailable.";
    }

    function renderReasoning(result) {
      reasoningStatus.textContent = "Reasoning: " + result.status + " · consistency: " + result.consistency +
        " · imports: " + (result.importClosureComplete ? "complete" : "incomplete");
      reasoningResults.replaceChildren();
      const facts = [
        ...result.classRelationships.map((fact) => ({ ...fact, title: "Class relationship" })),
        ...result.individualTypes.map((fact) => ({ ...fact, title: "Individual type" })),
        ...result.propertyRelationships.map((fact) => ({ ...fact, title: "Property relationship" })),
      ];
      appendPhase4List(reasoningResults, "Asserted and inferred facts", facts.map((fact) =>
        fact.title + " · " + (fact.origin === "inferred" ? "Inferred" : "Asserted") + " · " +
        displayIri(fact.subject) + (fact.predicate ? " · " + displayIri(fact.predicate) : "") + " · " + displayIri(fact.object)),
        "No derived facts returned.");
      appendPhase4List(reasoningResults, "Unsatisfiable classes", result.unsatisfiableClasses.map(displayIri), "None");
      appendPhase4List(reasoningResults, "OWL limitations", result.unsupportedFeatures.map((feature) =>
        feature.feature + " · " + feature.support + (feature.message ? " · " + feature.message : "")), "None");
      appendPhase4List(reasoningResults, "Warnings and errors", result.warnings.concat(result.errors), "None");
    }

    function renderShaclValidation(result) {
      shaclStatus.textContent = "SHACL: " + result.status + " · mode: " + result.mode +
        " · " + result.results.length + " result(s)";
      shaclResults.replaceChildren();
      appendPhase4List(shaclResults, "Validation findings", result.results.map((finding) =>
        finding.severity + " · " + finding.message + " · focus " + displayIri(finding.focusNode) +
        (finding.path ? " · path " + displayIri(finding.path) : "")), "No validation findings.");
      appendPhase4List(shaclResults, "Validation warnings and errors", result.warnings.concat(result.errors), "None");
    }

    function renderShaclShapes(result) {
      shaclShapes.replaceChildren();
      appendPhase4List(shaclShapes, "Supported SHACL shapes", result.shapes.map((shape) =>
        (shape.label || displayIri(shape.iri)) + " · targets: " + (shape.targets.map(displayIri).join(", ") || "none") +
        " · paths: " + (shape.propertyShapes.map(displayIri).join(", ") || "none") +
        " · constraints: " + (shape.constraints.join(", ") || "none")), "No supported shapes found.");
    }

    function renderProposalImpact(result) {
      previewImpact.replaceChildren();
      const heading = document.createElement("strong");
      heading.textContent = "Proposal impact: " + result.status + " · " + result.explicitDiffCount + " explicit change(s)";
      previewImpact.append(heading);
      appendPhase4List(previewImpact, "Reasoning impact", result.addedInferences.concat(result.removedInferences), "No reasoning changes.");
      appendPhase4List(previewImpact, "New or worsened SHACL findings", result.newShaclResults.concat(result.worsenedShaclResults), "None");
      appendPhase4List(previewImpact, "Unchanged or resolved SHACL findings", result.unchangedShaclResults.concat(result.resolvedShaclResults), "None");
      appendPhase4List(previewImpact, "Blocking messages", result.blockingMessages, "None");
    }

    function appendPhase4List(container, title, values, emptyText) {
      const heading = document.createElement("h4");
      heading.textContent = title;
      const list = document.createElement("ul");
      if (values.length === 0) {
        const empty = document.createElement("li");
        empty.textContent = emptyText;
        list.append(empty);
      } else {
        values.forEach((value) => {
          const item = document.createElement("li");
          item.textContent = value;
          list.append(item);
        });
      }
      container.append(heading, list);
    }

    function renderDeletionPreview(combined) {
      const deletionRequest = currentRequest;
      const preview = {
        proposalId: combined.proposalId || "deletion-preview",
        status: combined.status,
        targetSourceId: deletionRequest?.targetSourceId || targetSource.value,
        affectedPaths: combined.affectedPaths || [],
        previewTripleCount: combined.previewTripleCount || 0,
        diffEntries: combined.diffEntries || [],
        validationStatus: combined.validationStatus || "not run",
        validationOk: combined.validationOk,
        validationIssues: combined.validationIssues || [],
        semanticEquivalenceStatus: combined.semanticEquivalenceStatus,
        semanticEquivalenceReason: combined.semanticEquivalenceReason,
        canApprove: combined.canApprove,
        approvalDisabledReason: combined.canApprove
          ? undefined
          : "Approval is disabled because the deletion proposal is not ready.",
      };
      renderPreview(preview);
      if (combined.canApprove) {
        currentDeletionReview = undefined;
        selectedDeletionKeys = new Set();
        previewDeletion.disabled = true;
        deletionDependencies.textContent = "Deletion staged successfully. Review it in the staged changes list.";
      } else {
        updateDeletionPreviewState();
      }
    }

    function renderCombinedPreview(preview) {
      currentCombinedPreview = preview;
      currentPreview = undefined;
      previewStatus.textContent = preview.status + " · combined proposal · " + (preview.previewTripleCount || 0) + " graph triple(s)";
      previewImpact.textContent = "Affected files: " + (preview.affectedPaths.join(", ") || "none");
      renderList(previewDiff, preview.diffEntries.map((entry) => entry.description), "No semantic changes.");
      renderList(previewValidation, preview.validationIssues, preview.validationStatus || "not run");
      approve.disabled = !preview.canApprove;
      reject.disabled = false;
      openSource.disabled = true;
      approvalState.textContent = preview.canApprove
        ? "Combined preview is valid and ready for approval."
        : "Approval is disabled because the combined preview is not ready.";
    }

    function renderCombinedActionResult(result) {
      currentCombinedPreview = undefined;
      approve.disabled = true;
      reject.disabled = true;
      openSource.disabled = true;
      changedSource = result.changedFiles && result.changedFiles[0];
      if (result.ok && result.action === "apply") {
        stagedChanges = [];
        editingStagedEntry = undefined;
        renderStagedList();
        previewStatus.textContent = "Combined proposal applied successfully.";
        approvalState.textContent = "The project was refreshed after atomic application.";
        openSource.disabled = !changedSource;
        return;
      }
      if (result.ok && result.action === "reject") {
        currentRequest = undefined;
        currentPreview = undefined;
        changedSource = undefined;
        renderStagedList();
        previewStatus.textContent = "Combined proposal rejected; source files were not changed.";
        previewImpact.textContent = "No source files were changed.";
        previewDiff.replaceChildren();
        previewValidation.replaceChildren();
        approvalState.textContent = "The changes remain staged for correction or another review.";
        return;
      }
      previewStatus.textContent = result.reason || "Combined proposal action failed.";
      approvalState.textContent = result.rollbackStatus
        ? "Combined apply failed; rollback status: " + result.rollbackStatus
        : "The staged list remains available for correction.";
    }

    function renderPreviewError(message) {
      previewStatus.textContent = message;
      previewImpact.replaceChildren();
      previewDiff.replaceChildren();
      previewValidation.replaceChildren();
      approve.disabled = true;
      reject.disabled = true;
      openSource.disabled = true;
      currentPreview = undefined;
      approvalState.textContent = "Approval is disabled because preview failed.";
    }

    function renderGeneratedIri(result) {
      const currentKey = editKind.value + "|" + (labelForEdit() || "").trim();
      if (pendingIriGenerationKey !== currentKey) {
        pendingPreviewAfterIri = false;
        editStatus.textContent = "The label changed while the IRI was being generated. Click Stage change again.";
        return;
      }
      pendingIriGenerationKey = undefined;
      editStatus.textContent = "Generated IRI: " + result.iri + " · " + result.collision;
      if (editKind.value === "create-class") classIri.value = result.iri;
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) propertyIri.value = result.iri;
      if (editKind.value === "create-individual") individualIri.value = result.iri;
      if (editKind.value === "create-annotation-property") semanticPropertyIri.value = result.iri;
      if (pendingPreviewAfterIri) {
        pendingPreviewAfterIri = false;
        try {
          submitProposalPreview();
        } catch (error) {
          renderPreviewError(error instanceof Error ? error.message : "The proposal preview could not be started.");
        }
      }
    }

    function renderEntityResolutions(result) {
      pendingEntityResolution = false;
      Object.entries(result.entities || {}).forEach(([field, iri]) => {
        const input = document.getElementById(field);
        if (input && typeof iri === "string") input.value = iri;
      });
      try {
        submitProposalPreview();
      } catch (error) {
        renderPreviewError(error instanceof Error ? error.message : "The staged change could not be prepared.");
      }
    }

    function renderDeletionReview(result) {
      currentDeletionReview = result;
      selectedDeletionKeys = new Set(
        result.dependentStatements
          .filter((statement) => statement.selectedForRemoval && statement.dependencyKey)
          .map((statement) => statement.dependencyKey),
      );
      deletionDependencies.replaceChildren();
      const status = document.createElement("strong");
      status.textContent = formatDeletionStatus(result.status, result.safe);
      deletionDependencies.append(status);
      const list = document.createElement("ul");
      result.directStatements.forEach((statement) => {
        const item = document.createElement("li");
        item.textContent = statement.kind + " · " + displayStatementValue(statement.subjectLabel, statement.subject) + " · " +
          displayStatementValue(statement.predicateLabel, statement.predicate) + " · " +
          displayStatementValue(statement.objectLabel, statement.object);
        list.append(item);
      });
      result.dependentStatements.forEach((statement) => {
        const item = document.createElement("li");
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.checked = statement.selectedForRemoval;
        checkbox.disabled = !statement.dependencyKey;
        checkbox.addEventListener("change", () => {
          if (!statement.dependencyKey) return;
          if (checkbox.checked) selectedDeletionKeys.add(statement.dependencyKey);
          else selectedDeletionKeys.delete(statement.dependencyKey);
          statement.selectedForRemoval = checkbox.checked;
          updateDeletionPreviewState();
        });
        const label = document.createElement("label");
        label.className = "deletion-dependency";
        label.append(
          checkbox,
          document.createTextNode(" " + statement.kind + " · " + displayStatementValue(statement.subjectLabel, statement.subject) + " · " +
            displayStatementValue(statement.predicateLabel, statement.predicate) + " · " +
            displayStatementValue(statement.objectLabel, statement.object)),
        );
        item.append(label);
        list.append(item);
      });
      if (list.childElementCount > 0) deletionDependencies.append(list);
      if (!result.safe && canPreviewDeletion()) {
        const ready = document.createElement("p");
        ready.textContent = "All dependent statements are selected. Stage deletion is ready.";
        deletionDependencies.append(ready);
      } else if (!result.safe) {
        const blocker = document.createElement("p");
        blocker.textContent = result.invalidSelectedDependencyKeys.length > 0
          ? "Deletion remains blocked because one or more selected dependencies are invalid."
          : "Deletion remains blocked until dependent statements are explicitly selected.";
        deletionDependencies.append(blocker);
      }
      updateDeletionPreviewState();
    }

    function formatDeletionStatus(status, safe) {
      const labels = {
        Safe: "Safe",
        RequiresExplicitDependencies: "Requires explicit dependencies",
        Invalid: "Invalid deletion target",
        InvalidDependencySelection: "Invalid dependency selection",
      };
      return (labels[status] || status) + (safe ? " · safe to review" : " · explicit dependencies required");
    }

    function displayStatementValue(label, rawValue) {
      if (label) return label;
      const value = rawValue.startsWith("Iri(value=") && rawValue.endsWith(")")
        ? rawValue.slice("Iri(value=".length, -1)
        : rawValue;
      const separator = Math.max(value.lastIndexOf("#"), value.lastIndexOf("/"));
      return separator >= 0 && separator < value.length - 1 ? value.slice(separator + 1) : value;
    }

    function renderActionResult(result) {
      approve.disabled = true;
      reject.disabled = true;
      currentPreview = undefined;
      currentRequest = undefined;
      changedSource = result.changedFiles && result.changedFiles[0];
      openSource.disabled = !changedSource;
      if (result.ok) {
        previewStatus.textContent = result.action === "reject"
          ? "Proposal rejected; source files were not changed."
          : "Proposal applied successfully.";
        approvalState.textContent = result.action === "reject"
          ? "No files changed."
          : "The project was refreshed after application.";
        return;
      }
      previewStatus.textContent = result.reason;
      approvalState.textContent = result.rollbackStatus
        ? "Apply failed; rollback status: " + result.rollbackStatus
        : "Proposal action failed.";
    }

    function populateSources(model) {
      targetSource.replaceChildren();
      model.ontologySources.forEach((source) => {
        const option = document.createElement("option");
        option.value = source.id;
        option.textContent = source.id + " · " + source.path;
        targetSource.append(option);
      });
      const selectedSource = semanticSearchSource.value;
      semanticSearchSource.replaceChildren();
      const allSources = document.createElement("option");
      allSources.value = "";
      allSources.textContent = "All sources";
      semanticSearchSource.append(allSources);
      model.ontologySources.forEach((source) => {
        const option = document.createElement("option");
        option.value = source.id;
        option.textContent = source.id + " · " + source.path;
        semanticSearchSource.append(option);
      });
      semanticSearchSource.value = model.ontologySources.some((source) => source.id === selectedSource)
        ? selectedSource
        : "";
    }

    function renderDetails(symbol) {
      selectedSemanticIri = symbol.iri;
      details.replaceChildren();
      const heading = document.createElement("strong");
      heading.textContent = preferredSymbolLabel(symbol);
      const metadata = document.createElement("div");
      metadata.textContent = symbol.kind + " · " + symbol.sourceId;
      const iri = document.createElement("div");
      iri.textContent = symbol.iri;
      const deleteAction = document.createElement("button");
      deleteAction.id = "delete-symbol";
      deleteAction.type = "button";
      deleteAction.textContent = "Delete";
      deleteAction.disabled = !["Class", "Property", "Individual"].includes(symbol.kind);
      deleteAction.addEventListener("click", () => requestDeletionReview({
        label: symbol.label || undefined,
        iri: symbol.iri,
        kind: symbol.kind,
        sourceId: symbol.sourceId,
      }));
      details.append(heading, metadata, iri, deleteAction);
      renderRelationshipSection(
        "Types",
        symbol.relationships.filter((relationship) => relationship.kind === "type" && relationship.direction === "outgoing"),
      );
      renderRelationshipSection(
        "Outgoing properties",
        symbol.relationships.filter((relationship) => relationship.kind === "property" && relationship.direction === "outgoing"),
      );
      renderRelationshipSection(
        "Incoming relationships",
        symbol.relationships.filter((relationship) => relationship.direction === "incoming"),
      );
      const loading = document.createElement("p");
      loading.id = "semantic-details-status";
      loading.textContent = "Loading semantic details...";
      details.append(loading);
      vscode.postMessage({ type: "semantic-describe", payload: { iri: symbol.iri } });
    }

    function renderRelationshipSection(title, relationships) {
      const sectionHeading = document.createElement("h4");
      sectionHeading.textContent = title;
      const list = document.createElement("ul");
      if (relationships.length === 0) {
        const empty = document.createElement("li");
        empty.textContent = "None";
        list.append(empty);
      } else {
        relationships.forEach((relationship) => {
          const item = document.createElement("li");
          const predicate = relationship.predicateLabel || displayIri(relationship.predicate);
          const value = formatRelationshipValue(relationship);
          item.textContent = relationship.direction === "incoming"
            ? value + " → " + predicate
            : predicate + " → " + value;
          list.append(item);
        });
      }
      details.append(sectionHeading, list);
    }

    function formatRelationshipValue(relationship) {
      const value = relationship.value;
      if (value.kind === "literal") {
        let formatted = '"' + value.value + '"';
        if (value.language) formatted += "@" + value.language;
        if (value.datatype) formatted += "^^" + displayIri(value.datatype);
        return formatted;
      }
      return relationship.valueLabel
        ? relationship.valueLabel
        : displayIri(value.value);
    }

    function renderSemanticDescriptor(descriptor) {
      if (selectedSemanticIri && descriptor.iri !== selectedSemanticIri) return;
      const status = document.getElementById("semantic-details-status");
      if (status) status.remove();
      const existing = document.getElementById("semantic-details");
      if (existing) existing.remove();
      const section = document.createElement("section");
      section.id = "semantic-details";
      const heading = document.createElement("h4");
      heading.textContent = "Semantic details";
      section.append(heading);
      appendSemanticField(section, "Preferred label", descriptor.preferredLabel?.value || "None");
      appendSemanticField(section, "Label source", descriptor.preferredLabelSource);
      appendSemanticCollection(section, "Alternate labels", descriptor.alternateLabels.map(formatLocalizedText));
      appendSemanticCollection(section, "Definitions", descriptor.definitions.map(formatLocalizedText));
      appendSemanticCollection(section, "Annotations", descriptor.annotations.map(formatAnnotation));
      appendDescriptorStructure(section, descriptor);
      const technical = document.createElement("details");
      const technicalSummary = document.createElement("summary");
      technicalSummary.textContent = "Technical details";
      technical.append(technicalSummary);
      appendSemanticField(technical, "IRI", descriptor.iri);
      appendSemanticField(technical, "Kind", descriptor.kind);
      appendSemanticField(technical, "Source", descriptor.sourceId + " · " + descriptor.sourceOntologyId);
      appendSemanticField(technical, "Locality", descriptor.locality);
      section.append(technical);
      details.append(section);
    }

    function appendDescriptorStructure(container, descriptor) {
      if (descriptor.kind === "Class") {
        appendSemanticCollection(container, "Direct superclasses", descriptor.directSuperclasses.map(displayEntity));
        appendSemanticCollection(container, "Direct subclasses", descriptor.directSubclasses.map(displayEntity));
        appendSemanticCollection(container, "Directly typed individuals", descriptor.directlyTypedIndividuals.map(displayEntity));
      }
      if (["ObjectProperty", "DatatypeProperty"].includes(descriptor.kind)) {
        appendSemanticCollection(container, "Domains", descriptor.domains.map(displayEntity));
        appendSemanticCollection(
          container,
          descriptor.kind === "DatatypeProperty" ? "Datatype ranges" : "Ranges",
          (descriptor.kind === "DatatypeProperty" ? descriptor.datatypeRanges : descriptor.ranges).map(displayEntity),
        );
        appendSemanticCollection(container, "Direct assertions", descriptor.directAssertions.map(formatAssertion));
      }
      if (descriptor.kind === "AnnotationProperty") {
        appendSemanticCollection(container, "Statements using property", descriptor.statementsUsingProperty.map(formatAnnotation));
      }
      if (descriptor.kind === "Individual") {
        appendSemanticCollection(container, "Asserted types", descriptor.assertedTypes.map(displayEntity));
        appendSemanticCollection(container, "Object property assertions", descriptor.objectPropertyAssertions.map(formatAssertion));
        appendSemanticCollection(container, "Datatype property assertions", descriptor.datatypePropertyAssertions.map(formatAssertion));
      }
    }

    function appendSemanticField(container, label, value) {
      const field = document.createElement("p");
      const name = document.createElement("strong");
      name.textContent = label + ": ";
      field.append(name, document.createTextNode(value));
      container.append(field);
    }

    function appendSemanticCollection(container, title, values) {
      const heading = document.createElement("h5");
      heading.textContent = title;
      const list = document.createElement("ul");
      if (values.length === 0) {
        const empty = document.createElement("li");
        empty.textContent = "None";
        list.append(empty);
      } else {
        values.forEach((value) => {
          const item = document.createElement("li");
          item.textContent = value;
          list.append(item);
        });
      }
      container.append(heading, list);
    }

    function formatLocalizedText(text) {
      let value = text.value;
      if (text.language) value += " @" + text.language;
      if (text.datatype) value += " ^^ " + displayIri(text.datatype);
      return value;
    }

    function formatAnnotation(annotation) {
      return displayEntity(annotation.subject) + " · " + displayEntity(annotation.property) + " · " + formatRdfTerm(annotation.value);
    }

    function formatAssertion(assertion) {
      const value = typeof assertion.value === "string" ? displayEntity(assertion.value) : formatRdfTerm(assertion.value);
      return displayEntity(assertion.subject) + " · " + displayEntity(assertion.property) + " · " + value;
    }

    function formatRdfTerm(term) {
      if (term.kind !== "literal") return displayEntity(term.value);
      let value = '"' + term.value + '"';
      if (term.language) value += " @" + term.language;
      if (term.datatype) value += " ^^ " + displayIri(term.datatype);
      return value;
    }

    function displayEntity(iri) {
      const symbol = currentModel?.symbolGroups.flatMap((group) => group.symbols).find((candidate) => candidate.iri === iri);
      return symbol ? preferredSymbolLabel(symbol) : readableLocalName(iri);
    }

    function renderSemanticSearch(result) {
      semanticSearchResults.replaceChildren();
      if (result.results.length === 0) {
        semanticSearchResults.textContent = "No semantic descriptions matched '" + result.query + "'.";
        return;
      }
      const heading = document.createElement("p");
      heading.textContent = result.ambiguous
        ? "Multiple semantic descriptions matched; select one to inspect details."
        : "Semantic search result:";
      semanticSearchResults.append(heading);
      const list = document.createElement("ul");
      result.results.forEach((match) => {
        const item = document.createElement("li");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "symbol-button";
        button.textContent = (match.descriptor.preferredLabel?.value || displayIri(match.descriptor.iri)) +
          " · " + match.descriptor.kind + " · " + formatSearchReason(match.reason);
        button.addEventListener("click", () => {
          selectedSemanticIri = match.descriptor.iri;
          renderSemanticDescriptor(match.descriptor);
          details.scrollIntoView({ block: "nearest" });
        });
        item.append(button);
        list.append(item);
      });
      semanticSearchResults.append(list);
    }

    function externalElementLabel(element) {
      const descriptor = element && element.descriptor;
      const semantic = descriptor && descriptor.semantic;
      const preferred = semantic && semantic.preferredLabel;
      return (preferred && preferred.value) || (semantic && semantic.iri) || "Unnamed external element";
    }

    function externalElementIri(element) {
      return element && element.descriptor && element.descriptor.semantic && element.descriptor.semantic.iri;
    }

    function externalElementKind(element) {
      return (element && element.kind) || "Unknown";
    }

    function requestExternalBrowse(page) {
      if (!externalBrowseRequest) return;
      externalBrowsePage = page;
      externalStatus.textContent = "Loading external catalog page " + (page + 1) + "...";
      vscode.postMessage({
        type: "external-browse",
        payload: { ...externalBrowseRequest, page },
      });
    }

    function renderExternalPagination(result) {
      if (result.page <= 0 && !result.hasNext) return;
      const pagination = document.createElement("nav");
      pagination.className = "external-pagination";
      pagination.setAttribute("aria-label", "External catalog pages");
      const previous = document.createElement("button");
      previous.type = "button";
      previous.textContent = "Previous page";
      previous.disabled = result.page <= 0;
      previous.addEventListener("click", () => requestExternalBrowse(result.page - 1));
      const pageStatus = document.createElement("span");
      pageStatus.textContent = "Page " + (result.page + 1) + " · " + result.items.length + " shown";
      const next = document.createElement("button");
      next.type = "button";
      next.textContent = "Next page";
      next.disabled = !result.hasNext;
      next.addEventListener("click", () => requestExternalBrowse(result.page + 1));
      pagination.append(previous, pageStatus, next);
      externalResults.append(pagination);
    }

    function renderExternalState(state) {
      externalBrowseRequest = { mode: "curated", pageSize: state.browse.pageSize };
      externalBrowsePage = state.browse.page;
      externalStatus.textContent = "FIBO " + state.manifest.release + " · " + state.manifest.elementCount +
        " catalog element(s) · " + state.manifest.moduleCount + " module(s) · read-only";
      externalManifest.textContent = "Source: " + state.manifest.sourceId + " · catalog: " + state.manifest.catalogSchema +
        " · availability: " + state.manifest.availability + " · commit: " + state.manifest.commitSha;
      externalResults.replaceChildren();
      const heading = document.createElement("h3");
      heading.textContent = "Curated Foundations · showing " + state.browse.items.length + " of " + state.browse.totalCount;
      externalResults.append(heading);
      const list = document.createElement("ul");
      state.browse.items.forEach((module) => {
        const item = document.createElement("li");
        item.className = "external-card";
        const label = document.createElement("strong");
        label.textContent = (module.label || module.ontologyIri) + " · " + (module.domain || "unknown domain");
        const browse = document.createElement("button");
        browse.type = "button";
        browse.textContent = "Browse module";
        browse.addEventListener("click", () => {
          externalBrowseRequest = { mode: "module", moduleIri: module.ontologyIri, pageSize: 25 };
          externalBrowsePage = 0;
          externalStatus.textContent = "Loading module contents...";
          requestExternalBrowse(0);
        });
        item.append(label, browse);
        list.append(item);
      });
      externalResults.append(list);
      renderExternalPagination(state.browse);
    }

    function renderExternalBrowse(result) {
      externalBrowsePage = result.page;
      externalResults.replaceChildren();
      const back = document.createElement("button");
      back.type = "button";
      back.textContent = "Back to curated modules";
      back.addEventListener("click", () => {
        externalBrowseRequest = { mode: "curated", pageSize: 25 };
        requestExternalBrowse(0);
      });
      externalResults.append(back);
      const heading = document.createElement("h3");
      heading.textContent = "Module contents · page " + (result.page + 1) + " · showing " + result.items.length + " of " + result.totalCount;
      externalResults.append(heading);
      if (result.items.length === 0) {
        externalResults.append(document.createTextNode("No catalog elements were found in this module."));
        renderExternalPagination(result);
        return;
      }
      const list = document.createElement("ul");
      result.items.forEach((element) => {
        const item = document.createElement("li");
        item.className = "external-card";
        const label = document.createElement("strong");
        label.textContent = externalElementLabel(element) + " · " + externalElementKind(element);
        const inspect = document.createElement("button");
        inspect.type = "button";
        inspect.textContent = "Inspect details";
        inspect.addEventListener("click", () => inspectExternal(element));
        item.append(label, inspect);
        list.append(item);
      });
      externalResults.append(list);
      renderExternalPagination(result);
    }

    function renderExternalSearch(result) {
      externalResults.replaceChildren();
      const heading = document.createElement("h3");
      heading.textContent = "External results · showing " + result.candidates.length + " of " + result.totalResultCount;
      externalResults.append(heading);
      if (result.candidates.length === 0) {
        externalResults.append(document.createTextNode("No external concepts matched '" + result.query + "'."));
        return;
      }
      const list = document.createElement("ul");
      result.candidates.forEach((candidate) => {
        const element = candidate.element;
        const item = document.createElement("li");
        item.className = "external-card";
        const label = document.createElement("strong");
        label.textContent = externalElementLabel(element) + " · " + externalElementKind(element);
        const score = document.createElement("span");
        score.textContent = " · score " + candidate.score + " · " + candidate.confidence;
        const inspect = document.createElement("button");
        inspect.type = "button";
        inspect.textContent = "Inspect details";
        inspect.addEventListener("click", () => inspectExternal(element));
        item.append(label, score, inspect);
        if (candidate.scoreBreakdown) {
          const breakdown = document.createElement("small");
          breakdown.textContent = "Signals: " + Object.entries(candidate.scoreBreakdown)
            .filter(([, value]) => value)
            .map(([key, value]) => key + " " + value)
            .join(", ");
          item.append(breakdown);
        }
        if (Array.isArray(candidate.reasons) && candidate.reasons.length > 0) {
          const reasons = document.createElement("small");
          reasons.textContent = "Matched by: " + candidate.reasons.map((reason) => reason.matchedField || reason.type).join(", ");
          item.append(reasons);
        }
        if (candidate.tieGroupId) {
          const tie = document.createElement("small");
          tie.textContent = " Tied candidate group: " + candidate.tieGroupId;
          item.append(tie);
        }
        list.append(item);
      });
      externalResults.append(list);
      externalSearchPage = result.page;
      if (result.hasNext && externalSearchRequest) {
        const next = document.createElement("button");
        next.type = "button";
        next.textContent = "Load more results";
        next.addEventListener("click", () => {
          const page = externalSearchPage + 1;
          externalResults.append(document.createTextNode(" Loading more results..."));
          vscode.postMessage({ type: "external-search", payload: { ...externalSearchRequest, page } });
        });
        externalResults.append(next);
      }
    }

    function inspectExternal(element) {
      const iri = externalElementIri(element);
      if (!iri) return;
      selectedExternal = element;
      externalDetails.textContent = "Loading external descriptor details...";
      externalDependencies.textContent = "Loading explicit dependencies...";
      vscode.postMessage({ type: "external-describe", payload: { iri, kind: externalElementKind(element) } });
      vscode.postMessage({ type: "external-dependencies", payload: { iri, kind: externalElementKind(element) } });
    }

    function renderExternalDescriptor(result) {
      const descriptor = result.descriptor;
      const semantic = descriptor.semantic || {};
      externalDetails.replaceChildren();
      const heading = document.createElement("h3");
      heading.textContent = externalElementLabel({ descriptor: descriptor }) + " · " + result.kind;
      externalDetails.append(heading);
      const metadata = descriptor || {};
      const fields = [
        ["Module", metadata.moduleIri],
        ["Domain", metadata.domain],
        ["Maturity", metadata.maturity],
        ["Status", metadata.catalogStatus],
        ["Locality", metadata.locality],
        ["External IRI", semantic.iri],
      ];
      fields.forEach(([label, value]) => {
        if (!value) return;
        const field = document.createElement("p");
        field.textContent = label + ": " + value;
        externalDetails.append(field);
      });
      const definitions = Array.isArray(semantic.definitions)
        ? semantic.definitions.map((definition) => definition && definition.value).filter(Boolean)
        : [];
      if (definitions.length > 0) {
        const definitionHeading = document.createElement("h4");
        definitionHeading.textContent = "Definition" + (definitions.length > 1 ? "s" : "");
        externalDetails.append(definitionHeading);
        definitions.forEach((definition) => {
          const definitionField = document.createElement("p");
          definitionField.textContent = definition;
          externalDetails.append(definitionField);
        });
      }
      const inspectButton = document.createElement("button");
      inspectButton.type = "button";
      inspectButton.textContent = "Inspect dependencies";
      inspectButton.addEventListener("click", () => inspectExternal({ kind: result.kind, descriptor }));
      const reuseButton = document.createElement("button");
      reuseButton.type = "button";
      reuseButton.textContent = "Prepare external reuse";
      reuseButton.disabled = !semantic.iri;
      reuseButton.addEventListener("click", () => prepareExternalProposal(result.kind === "Class" ? "reuse-class" : result.kind === "ObjectProperty" ? "reuse-object-property" : "reuse-datatype-property"));
      externalDetails.append(inspectButton, reuseButton);
      if (result.kind === "Class") {
        const localButton = document.createElement("button");
        localButton.type = "button";
        localButton.textContent = "Prepare local subclass";
        localButton.addEventListener("click", () => prepareExternalProposal("local-subclass"));
        externalDetails.append(localButton);
      }
    }

    function renderExternalDependencies(result) {
      selectedExternalDependencies = new Set();
      externalDependencies.replaceChildren();
      const heading = document.createElement("h3");
      heading.textContent = result.requiresExplicitApproval ? "Dependency review · approval required" : "Dependency review · ready";
      externalDependencies.append(heading);
      const list = document.createElement("ul");
      result.dependencies.forEach((dependency) => {
        const item = document.createElement("li");
        item.className = "external-card";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.disabled = dependency.selection !== "Missing";
        checkbox.addEventListener("change", () => {
          const key = [dependency.category, dependency.externalIri || "", dependency.sourceModule || ""].join("|");
          if (checkbox.checked) selectedExternalDependencies.add(key);
          else selectedExternalDependencies.delete(key);
        });
        const text = document.createElement("span");
        text.textContent = dependency.category + " · " + (dependency.externalIri || dependency.sourceModule || "package") +
          " · " + dependency.selection + " · " + dependency.reason;
        item.append(checkbox, text);
        list.append(item);
      });
      externalDependencies.append(list);
    }

    function prepareExternalProposal(intent) {
      const iri = externalElementIri(selectedExternal);
      if (!iri) {
        externalProposal.textContent = "Select an external element before preparing a proposal.";
        return;
      }
      const targetOntology = prompt("Target ontology IRI for this proposal", "https://example.com/entio/" + targetSource.value);
      if (!targetOntology) return;
      externalTargetOntologyIri = targetOntology;
      externalProposal.textContent = "Preparing an external proposal; local files remain unchanged...";
      vscode.postMessage({
        type: "external-proposal",
        payload: {
          targetSourceId: targetSource.value,
          targetOntologyIri: externalTargetOntologyIri,
          intent,
          externalIri: iri,
          kind: externalElementKind(selectedExternal),
          localClassIri: intent === "local-subclass" ? prompt("Local class IRI", "") : undefined,
          selectedDependencyKeys: Array.from(selectedExternalDependencies),
        },
      });
    }

    function formatSearchReason(reason) {
      const labels = {
        PreferredLabel: "preferred label",
        AlternateLabel: "alternate label",
        Iri: "IRI",
        Annotation: "annotation",
      };
      return labels[reason] || reason;
    }

    function displayIri(value) {
      const separator = Math.max(value.lastIndexOf("#"), value.lastIndexOf("/"));
      return separator >= 0 && separator < value.length - 1 ? value.slice(separator + 1) : value;
    }

    function renderModel(model) {
      currentModel = model;
      status.textContent = model.projectName + " · " + model.graphTripleCount + " graph triple(s)";
      populateSources(model);
      sources.replaceChildren();
      model.ontologySources.forEach((source) => {
        const item = document.createElement("li");
        item.textContent = source.id + " · " + source.path;
        sources.append(item);
      });
      symbolGroups.replaceChildren();
      model.symbolGroups.forEach((group) => {
        const heading = document.createElement("h3");
        heading.textContent = group.kind;
        const list = document.createElement("ul");
        group.symbols.forEach((symbol) => {
          const item = document.createElement("li");
          const button = document.createElement("button");
          button.className = "symbol-button";
          button.type = "button";
          button.textContent = preferredSymbolLabel(symbol);
          button.addEventListener("click", () => renderDetails(symbol));
          item.append(button);
          list.append(item);
        });
        symbolGroups.append(heading, list);
      });
      if (model.selectedSymbol) {
        renderDetails(model.selectedSymbol);
      }
    }

    window.addEventListener("message", (event) => {
      const message = event.data;
      if (message.type === "error") {
        status.textContent = message.message;
        return;
      }
      if (message.type === "project-summary") {
        renderModel(message.payload);
      }
      if (message.type === "phase4-state") {
        renderPhase4State(message.payload);
      }
      if (message.type === "phase4-error") {
        reasoningStatus.textContent = message.message;
        shaclStatus.textContent = "Phase 4 state unavailable.";
      }
      if (message.type === "semantic-descriptor") {
        renderSemanticDescriptor(message.payload);
      }
      if (message.type === "semantic-descriptor-error") {
        const semanticStatus = document.getElementById("semantic-details-status");
        if (semanticStatus) semanticStatus.textContent = message.message;
      }
      if (message.type === "semantic-search") {
        renderSemanticSearch(message.payload);
      }
      if (message.type === "semantic-search-error") {
        semanticSearchResults.textContent = message.message;
      }
      if (message.type === "external-state") {
        renderExternalState(message.payload);
      }
      if (message.type === "external-error") {
        externalStatus.textContent = message.message;
      }
      if (message.type === "external-browse") {
        externalStatus.textContent = "External catalog browse complete.";
        renderExternalBrowse(message.payload);
      }
      if (message.type === "external-browse-error") {
        externalStatus.textContent = message.message;
        externalResults.textContent = message.message;
      }
      if (message.type === "external-search") {
        externalStatus.textContent = "External catalog search complete.";
        renderExternalSearch(message.payload);
      }
      if (message.type === "external-search-error") {
        externalResults.textContent = message.message;
      }
      if (message.type === "external-describe") {
        renderExternalDescriptor(message.payload);
      }
      if (message.type === "external-describe-error") {
        externalDetails.textContent = message.message;
      }
      if (message.type === "external-dependencies") {
        renderExternalDependencies(message.payload);
      }
      if (message.type === "external-dependencies-error") {
        externalDependencies.textContent = message.message;
      }
      if (message.type === "external-proposal") {
        const proposal = message.payload;
        externalProposal.textContent = "External proposal prepared: " + proposal.proposalId + " · " + proposal.changeCount +
          " change(s) · " + proposal.dependencyStatus + ". No local files were changed.";
      }
      if (message.type === "external-proposal-error") {
        externalProposal.textContent = message.message;
      }
      if (message.type === "proposal-preview") {
        renderPreview(message.payload);
      }
      if (message.type === "combined-preview") {
        renderCombinedPreview(message.payload);
      }
      if (message.type === "proposal-impact") {
        renderProposalImpact(message.payload);
      }
      if (message.type === "deletion-preview") {
        renderDeletionPreview(message.payload);
      }
      if (message.type === "deletion-preview-error") {
        previewDeletion.disabled = false;
        previewStatus.textContent = message.message;
        approvalState.textContent = "Deletion preview failed; correct the dependency selection and try again.";
      }
      if (message.type === "combined-preview-error") {
        currentCombinedPreview = undefined;
        previewStatus.textContent = message.message;
        approve.disabled = true;
        reject.disabled = true;
      }
      if (message.type === "combined-action-result") {
        renderCombinedActionResult(message.payload);
      }
      if (message.type === "combined-action-error") {
        renderCombinedActionResult({ ok: false, action: message.action || "apply", reason: message.message, changedFiles: [] });
      }
      if (message.type === "generated-iri") {
        renderGeneratedIri(message.payload);
      }
      if (message.type === "generated-iri-error") {
        pendingPreviewAfterIri = false;
        pendingIriGenerationKey = undefined;
        editStatus.textContent = message.message;
      }
      if (message.type === "edit-entities-resolved") {
        renderEntityResolutions(message.payload);
      }
      if (message.type === "edit-entities-error") {
        pendingEntityResolution = false;
        editStatus.textContent = message.message;
      }
      if (message.type === "deletion-dependencies") {
        renderDeletionReview(message.payload);
      }
      if (message.type === "deletion-dependencies-error") {
        deletionDependencies.textContent = message.message;
      }
      if (message.type === "proposal-preview-error") {
        renderPreviewError(message.message);
      }
      if (message.type === "proposal-action-result") {
        renderActionResult(message.payload);
      }
      if (message.type === "proposal-action-error") {
        renderPreviewError(message.message);
      }
    });
    renderStagedList();
    vscode.postMessage({ type: "refresh" });
    vscode.postMessage({ type: "external-refresh" });
  </script>
</body>
</html>`;
}
