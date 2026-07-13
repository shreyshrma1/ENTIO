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
    [hidden] { display: none !important; }
    input, select { color: var(--vscode-input-foreground); background: var(--vscode-input-background); border: 1px solid var(--vscode-input-border); padding: 6px; }
    #edit-form, #preview { margin-top: 20px; }
    #preview-status { white-space: pre-wrap; }
    #preview-diff, #preview-validation, #preview-impact { margin: 8px 0; }
    #approval-state { margin-left: 8px; }
    #open-source { margin-left: 8px; }
    @media (max-width: 640px) { main { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <h1>Entio Ontology Workbench</h1>
  <button id="refresh" type="button">Refresh</button>
  <div id="status" role="status">Loading project summary...</div>
  <section id="entity-selector" aria-labelledby="entity-selector-heading">
    <h2 id="entity-selector-heading">Find an existing entity</h2>
    <form id="entity-selector-form">
      <label>Label <input id="entity-selector-label" type="text"></label>
      <label>Kind
        <select id="entity-selector-kind">
          <option value="">Any kind</option>
          <option value="Class">Class</option>
          <option value="Property">Property</option>
          <option value="Individual">Individual</option>
        </select>
      </label>
      <button id="resolve-entity" type="submit">Resolve entity</button>
    </form>
    <div id="entity-resolution" aria-live="polite">No entity selected.</div>
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
  <section id="edit-form" aria-labelledby="edit-heading">
    <h2 id="edit-heading">Preview Ontology Edit</h2>
    <form id="proposal-form">
      <label>Target source <select id="target-source" required></select></label>
      <label>Edit type <select id="edit-kind">${editKindOptions}</select></label>
      <div id="class-fields">
        <label>Class IRI <input id="class-iri" type="url" required></label>
        <label>Label <input id="class-label" type="text"></label>
      </div>
      <div id="property-fields" hidden>
        <label>Property IRI <input id="property-iri" type="url"></label>
        <label id="property-label-field">Label <input id="property-label" type="text"></label>
        <label id="property-domain-field">Domain IRI <input id="property-domain-iri" type="url"></label>
        <label id="property-range-field">Range IRI <input id="property-range-iri" type="url"></label>
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
        <label>Individual IRI <input id="individual-iri" type="url"></label>
        <label>Type IRI <input id="individual-type-iri" type="url"></label>
        <label>Label <input id="individual-label" type="text"></label>
      </div>
      <div id="assertion-fields" hidden>
        <label>Subject IRI <input id="assertion-subject-iri" type="url"></label>
        <label>Property IRI <input id="assertion-property-iri" type="url"></label>
        <label id="assertion-object-field">Object IRI <input id="assertion-object-iri" type="url"></label>
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
        <label>Class IRI <input id="hierarchy-class-iri" type="url"></label>
        <label>Superclass IRI <input id="hierarchy-superclass-iri" type="url"></label>
      </div>
      <div id="label-fields" hidden>
        <label>Entity IRI <input id="label-entity-iri" type="url"></label>
        <label>Label <input id="label-value" type="text"></label>
        <label>Language tag <input id="label-language" type="text"></label>
        <label><input id="label-replace" type="checkbox"> Replace existing labels</label>
      </div>
      <p id="edit-form-placeholder" hidden>Additional edit forms are provided by the workbench edit modes.</p>
      <button id="generate-iri" type="button">Generate IRI from label</button>
      <span id="generated-iri-status" aria-live="polite"></span>
      <button id="preview-submit" type="submit">Preview change</button>
    </form>
  </section>
  <section id="deletion-review" aria-labelledby="deletion-heading">
    <h2 id="deletion-heading">Review deletion dependencies</h2>
    <form id="deletion-form">
      <label>Entity label <input id="deletion-label" type="text"></label>
      <label>Entity IRI <input id="deletion-iri" type="url"></label>
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
    const entitySelectorForm = document.getElementById("entity-selector-form");
    const entitySelectorLabel = document.getElementById("entity-selector-label");
    const entitySelectorKind = document.getElementById("entity-selector-kind");
    const entityResolution = document.getElementById("entity-resolution");
    const proposalForm = document.getElementById("proposal-form");
    const targetSource = document.getElementById("target-source");
    const editKind = document.getElementById("edit-kind");
    const classFields = document.getElementById("class-fields");
    const editFormPlaceholder = document.getElementById("edit-form-placeholder");
    const previewSubmit = document.getElementById("preview-submit");
    const generateIri = document.getElementById("generate-iri");
    const generatedIriStatus = document.getElementById("generated-iri-status");
    const classIri = document.getElementById("class-iri");
    const classLabel = document.getElementById("class-label");
    const propertyFields = document.getElementById("property-fields");
    const propertyIri = document.getElementById("property-iri");
    const propertyLabelField = document.getElementById("property-label-field");
    const propertyLabel = document.getElementById("property-label");
    const propertyDomainField = document.getElementById("property-domain-field");
    const propertyDomainIri = document.getElementById("property-domain-iri");
    const propertyRangeField = document.getElementById("property-range-field");
    const propertyRangeIri = document.getElementById("property-range-iri");
    const propertyDatatypeField = document.getElementById("property-datatype-field");
    const propertyDatatype = document.getElementById("property-datatype");
    const propertyReplace = document.getElementById("property-replace");
    const individualFields = document.getElementById("individual-fields");
    const individualIri = document.getElementById("individual-iri");
    const individualTypeIri = document.getElementById("individual-type-iri");
    const individualLabel = document.getElementById("individual-label");
    const assertionFields = document.getElementById("assertion-fields");
    const assertionSubjectIri = document.getElementById("assertion-subject-iri");
    const assertionPropertyIri = document.getElementById("assertion-property-iri");
    const assertionObjectField = document.getElementById("assertion-object-field");
    const assertionObjectIri = document.getElementById("assertion-object-iri");
    const assertionValueField = document.getElementById("assertion-value-field");
    const assertionValue = document.getElementById("assertion-value");
    const assertionDatatypeField = document.getElementById("assertion-datatype-field");
    const assertionDatatype = document.getElementById("assertion-datatype");
    const assertionLanguageField = document.getElementById("assertion-language-field");
    const assertionLanguage = document.getElementById("assertion-language");
    const hierarchyFields = document.getElementById("hierarchy-fields");
    const hierarchyClassIri = document.getElementById("hierarchy-class-iri");
    const hierarchySuperclassIri = document.getElementById("hierarchy-superclass-iri");
    const labelFields = document.getElementById("label-fields");
    const labelEntityIri = document.getElementById("label-entity-iri");
    const labelValue = document.getElementById("label-value");
    const labelLanguage = document.getElementById("label-language");
    const labelReplace = document.getElementById("label-replace");
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
    let currentRequest;
    let currentPreview;
    let changedSource;
    document.getElementById("refresh").addEventListener("click", () => vscode.postMessage({ type: "refresh" }));

    entitySelectorForm.addEventListener("submit", (event) => {
      event.preventDefault();
      if (entitySelectorLabel.value === "") {
        entityResolution.textContent = "Enter a label to resolve an entity.";
        return;
      }
      vscode.postMessage({
        type: "resolve-entity",
        payload: {
          label: entitySelectorLabel.value,
          kind: entitySelectorKind.value || undefined,
          sourceId: targetSource.value || undefined,
        },
      });
      entityResolution.textContent = "Resolving entity...";
    });

    generateIri.addEventListener("click", () => {
      const kind = entityKindForEdit();
      const label = labelForEdit();
      if (!kind || !label) {
        generatedIriStatus.textContent = "Choose a supported new-entity edit and provide a label first.";
        return;
      }
      vscode.postMessage({ type: "generate-iri", payload: { label, kind } });
      generatedIriStatus.textContent = "Generating deterministic IRI...";
    });

    deletionForm.addEventListener("submit", (event) => {
      event.preventDefault();
      if (deletionLabel.value === "" && deletionIri.value === "") {
        deletionDependencies.textContent = "Enter an entity label or IRI to inspect deletion dependencies.";
        return;
      }
      vscode.postMessage({
        type: "deletion-dependencies",
        payload: {
          label: deletionLabel.value || undefined,
          iri: deletionIri.value || undefined,
          kind: deletionKind.value || undefined,
          sourceId: targetSource.value || undefined,
        },
      });
      deletionDependencies.textContent = "Inspecting deletion dependencies...";
    });

    function entityKindForEdit() {
      if (editKind.value === "create-class") return "Class";
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) return "Property";
      if (editKind.value === "create-individual") return "Individual";
      return undefined;
    }

    function labelForEdit() {
      if (editKind.value === "create-class") return classLabel.value;
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) return propertyLabel.value;
      if (editKind.value === "create-individual") return individualLabel.value;
      return undefined;
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
      const formMode = createClass || propertyMode || individualMode || assertionMode || hierarchyMode || labelMode;
      classFields.hidden = !createClass;
      propertyFields.hidden = !propertyMode;
      individualFields.hidden = !individualMode;
      assertionFields.hidden = !assertionMode;
      hierarchyFields.hidden = !hierarchyMode;
      labelFields.hidden = !labelMode;
      editFormPlaceholder.hidden = formMode;
      previewSubmit.disabled = !formMode;
      classIri.required = createClass;
      propertyIri.required = propertyMode;
      propertyLabelField.hidden = !createProperty;
      propertyDomainField.hidden = !domainMode;
      propertyRangeField.hidden = !rangeMode;
      propertyDatatypeField.hidden = !datatypeMode;
      propertyReplace.parentElement.hidden = editKind.value !== "set-property-domain" && editKind.value !== "set-property-range";
      propertyDomainIri.required = editKind.value === "set-property-domain";
      propertyRangeIri.required = editKind.value === "set-property-range" && propertyDatatype.value === "";
      individualIri.required = individualMode;
      individualTypeIri.required = editKind.value === "assign-individual-type";
      assertionSubjectIri.required = assertionMode;
      assertionPropertyIri.required = assertionMode;
      assertionObjectField.hidden = !assertionMode || datatypeAssertion;
      assertionValueField.hidden = !datatypeAssertion;
      assertionDatatypeField.hidden = !datatypeAssertion;
      assertionLanguageField.hidden = !datatypeAssertion;
      assertionObjectIri.required = editKind.value === "add-object-property-assertion";
      assertionValue.required = datatypeAssertion;
      hierarchyClassIri.required = hierarchyMode;
      hierarchySuperclassIri.required = hierarchyMode;
      labelEntityIri.required = labelMode;
      labelValue.required = labelMode;
    }
    editKind.addEventListener("change", updateEditFormMode);
    propertyDatatype.addEventListener("change", updateEditFormMode);
    assertionDatatype.addEventListener("change", updateEditFormMode);
    updateEditFormMode();

    proposalForm.addEventListener("submit", (event) => {
      event.preventDefault();
      const propertyMode = !propertyFields.hidden;
      if (editKind.value === "set-property-range" && propertyRangeIri.value === "" && propertyDatatype.value === "") {
        previewStatus.textContent = "A range IRI or datatype is required.";
        return;
      }
      const individualMode = !individualFields.hidden;
      const assertionMode = !assertionFields.hidden;
      const hierarchyMode = !hierarchyFields.hidden;
      const labelMode = !labelFields.hidden;
      if (editKind.value === "add-datatype-property-assertion" && assertionValue.value === "") {
        previewStatus.textContent = "A literal value is required.";
        return;
      }
      if (editKind.value !== "create-class" && !propertyMode && !individualMode && !assertionMode && !hierarchyMode && !labelMode) return;
      const payload = editKind.value === "create-class"
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            classIri: classIri.value,
            label: classLabel.value,
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
      vscode.postMessage({
        type: "proposal-preview",
        payload,
      });
      currentRequest = payload;
      previewStatus.textContent = "Requesting proposal preview...";
    });

    approve.addEventListener("click", () => {
      if (currentPreview && currentPreview.canApprove && currentRequest) {
        vscode.postMessage({ type: "proposal-action", action: "apply", payload: currentRequest });
      }
    });

    reject.addEventListener("click", () => {
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
        ? "Preview is valid and ready to apply."
        : preview.approvalDisabledReason;
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

    function renderEntityResolution(result) {
      entityResolution.replaceChildren();
      if (result.status === "resolved" && result.candidate) {
        entityResolution.textContent = result.candidate.label || result.candidate.iri;
        const details = document.createElement("div");
        details.textContent = result.candidate.kind + " · " + result.candidate.sourceId + " · " + result.candidate.iri;
        entityResolution.append(details);
        return;
      }
      if (result.status === "ambiguous") {
        const heading = document.createElement("strong");
        heading.textContent = "Ambiguous entity; choose a kind or source filter.";
        const list = document.createElement("ul");
        result.candidates.forEach((candidate) => {
          const item = document.createElement("li");
          item.textContent = (candidate.label || candidate.iri) + " · " + candidate.kind + " · " + candidate.sourceId;
          list.append(item);
        });
        entityResolution.append(heading, list);
        return;
      }
      entityResolution.textContent = result.message || "Entity was not found.";
    }

    function renderGeneratedIri(result) {
      generatedIriStatus.textContent = result.iri + " · " + result.collision;
      if (editKind.value === "create-class") classIri.value = result.iri;
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) propertyIri.value = result.iri;
      if (editKind.value === "create-individual") individualIri.value = result.iri;
    }

    function renderDeletionReview(result) {
      deletionDependencies.replaceChildren();
      const status = document.createElement("strong");
      status.textContent = result.status + (result.safe ? " · safe to review" : " · explicit dependencies required");
      deletionDependencies.append(status);
      const list = document.createElement("ul");
      result.directStatements.concat(result.dependentStatements).forEach((statement) => {
        const item = document.createElement("li");
        item.textContent = statement.kind + " · " + statement.subject + " · " + statement.predicate + " · " + statement.object;
        list.append(item);
      });
      if (list.childElementCount > 0) deletionDependencies.append(list);
      if (!result.safe) {
        const blocker = document.createElement("p");
        blocker.textContent = "Deletion remains blocked until dependent statements are explicitly selected.";
        deletionDependencies.append(blocker);
      }
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
    }

    function renderDetails(symbol) {
      details.replaceChildren();
      const heading = document.createElement("strong");
      heading.textContent = symbol.label || symbol.iri;
      const metadata = document.createElement("div");
      metadata.textContent = symbol.kind + " · " + symbol.sourceId;
      const iri = document.createElement("div");
      iri.textContent = symbol.iri;
      details.append(heading, metadata, iri);
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
          const predicate = relationship.predicateLabel || relationship.predicate;
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
        if (value.datatype) formatted += "^^" + value.datatype;
        return formatted;
      }
      return relationship.valueLabel
        ? relationship.valueLabel + " (" + value.value + ")"
        : value.value;
    }

    function renderModel(model) {
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
          button.textContent = symbol.label || symbol.iri;
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
      if (message.type === "proposal-preview") {
        renderPreview(message.payload);
      }
      if (message.type === "entity-resolution") {
        renderEntityResolution(message.payload);
      }
      if (message.type === "entity-resolution-error") {
        entityResolution.textContent = message.message;
      }
      if (message.type === "generated-iri") {
        renderGeneratedIri(message.payload);
      }
      if (message.type === "generated-iri-error") {
        generatedIriStatus.textContent = message.message;
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
    vscode.postMessage({ type: "refresh" });
  </script>
</body>
</html>`;
}
