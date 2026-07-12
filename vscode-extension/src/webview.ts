import type { Webview } from "vscode";

export function renderWorkbench(webview: Webview, nonce: string): string {
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
    main { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(240px, 2fr); gap: 20px; margin-top: 16px; }
    section { border-top: 1px solid var(--vscode-panel-border); padding-top: 12px; }
    ul { list-style: none; padding: 0; }
    li { margin: 4px 0; }
    .symbol-button { width: 100%; text-align: left; background: transparent; color: var(--vscode-foreground); }
    .symbol-button:hover, .symbol-button:focus { background: var(--vscode-list-hoverBackground); }
    #status { margin: 12px 0; white-space: pre-wrap; }
    #details { min-height: 100px; }
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
  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    const status = document.getElementById("status");
    const sources = document.getElementById("sources");
    const symbolGroups = document.getElementById("symbol-groups");
    const details = document.getElementById("details");
    document.getElementById("refresh").addEventListener("click", () => vscode.postMessage({ type: "refresh" }));

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
    });
    vscode.postMessage({ type: "refresh" });
  </script>
</body>
</html>`;
}
