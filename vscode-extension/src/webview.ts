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
    #status { margin: 16px 0; white-space: pre-wrap; }
  </style>
</head>
<body>
  <h1>Entio Ontology Workbench</h1>
  <button id="refresh" type="button">Refresh</button>
  <div id="status" role="status">Loading project summary...</div>
  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    const status = document.getElementById("status");
    document.getElementById("refresh").addEventListener("click", () => vscode.postMessage({ type: "refresh" }));
    window.addEventListener("message", (event) => {
      const message = event.data;
      if (message.type === "error") {
        status.textContent = message.message;
        return;
      }
      if (message.type === "project-summary") {
        const project = message.payload.project;
        const sources = message.payload.ontologySources || [];
        const symbols = message.payload.symbols || [];
        status.textContent = project
          ? project.name + "\n" + sources.length + " ontology source(s), " + symbols.length + " symbol(s)"
          : "No project summary returned.";
      }
    });
    vscode.postMessage({ type: "refresh" });
  </script>
</body>
</html>`;
}
