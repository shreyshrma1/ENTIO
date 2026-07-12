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
      <p id="edit-form-placeholder" hidden>Additional edit forms are provided by the workbench edit modes.</p>
      <button id="preview-submit" type="submit">Preview change</button>
    </form>
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
    const proposalForm = document.getElementById("proposal-form");
    const targetSource = document.getElementById("target-source");
    const editKind = document.getElementById("edit-kind");
    const classFields = document.getElementById("class-fields");
    const editFormPlaceholder = document.getElementById("edit-form-placeholder");
    const previewSubmit = document.getElementById("preview-submit");
    const classIri = document.getElementById("class-iri");
    const classLabel = document.getElementById("class-label");
    const previewStatus = document.getElementById("preview-status");
    const previewImpact = document.getElementById("preview-impact");
    const previewDiff = document.getElementById("preview-diff");
    const previewValidation = document.getElementById("preview-validation");
    const approve = document.getElementById("approve");
    const reject = document.getElementById("reject");
    const openSource = document.getElementById("open-source");
    const approvalState = document.getElementById("approval-state");
    let currentRequest;
    let currentPreview;
    let changedSource;
    document.getElementById("refresh").addEventListener("click", () => vscode.postMessage({ type: "refresh" }));

    function updateEditFormMode() {
      const createClass = editKind.value === "create-class";
      classFields.hidden = !createClass;
      editFormPlaceholder.hidden = createClass;
      previewSubmit.disabled = !createClass;
      classIri.required = createClass;
    }
    editKind.addEventListener("change", updateEditFormMode);
    updateEditFormMode();

    proposalForm.addEventListener("submit", (event) => {
      event.preventDefault();
      if (editKind.value !== "create-class") return;
      vscode.postMessage({
        type: "proposal-preview",
        payload: {
          targetSourceId: targetSource.value,
          editKind: editKind.value,
          classIri: classIri.value,
          label: classLabel.value,
        },
      });
      currentRequest = {
        targetSourceId: targetSource.value,
        editKind: "create-class",
        classIri: classIri.value,
        label: classLabel.value,
      };
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
