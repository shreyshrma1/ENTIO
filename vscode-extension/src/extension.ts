import { randomBytes } from "node:crypto";
import * as vscode from "vscode";
import { EntioEngineClient } from "./engineCli";
import { detectEntioProject } from "./projectDetector";
import { renderWorkbench } from "./webview";
import { createWorkbenchModel } from "./workbenchModel";

export function activate(context: vscode.ExtensionContext): void {
  const command = vscode.commands.registerCommand(
    "entio.openOntologyWorkbench",
    async () => {
      const panel = vscode.window.createWebviewPanel(
        "entioOntologyWorkbench",
        "Entio Ontology Workbench",
        vscode.ViewColumn.One,
        { enableScripts: true, retainContextWhenHidden: true },
      );
      const nonce = randomBytes(16).toString("hex");
      panel.webview.html = renderWorkbench(panel.webview, nonce);

      const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
      const project = detectEntioProject(workspaceRoot);
      if (!project) {
        await panel.webview.postMessage({
          type: "error",
          message: "No Entio project found in the active workspace.",
        });
        return;
      }

      const cliCommand = vscode.workspace
        .getConfiguration("entio")
        .get<string>("cliCommand", "entio");
      const engine = new EntioEngineClient(cliCommand);
      const refresh = async (): Promise<void> => {
        try {
          const response = await engine.run(
            ["project-summary", project.rootPath],
            project.rootPath,
          );
          const model = createWorkbenchModel(response);
          if (!model) {
            throw new Error("Entio CLI returned an invalid project summary.");
          }
          await panel.webview.postMessage({ type: "project-summary", payload: model });
        } catch (error) {
          await panel.webview.postMessage({
            type: "error",
            message: error instanceof Error ? error.message : "Entio CLI invocation failed.",
          });
        }
      };
      const messageSubscription = panel.webview.onDidReceiveMessage(async (message: { type?: string }) => {
        if (message.type !== "refresh") {
          return;
        }

        await refresh();
      });
      const watcher = vscode.workspace.createFileSystemWatcher(
        new vscode.RelativePattern(project.rootPath, "**/*.ttl"),
      );
      const watcherSubscriptions = [
        watcher.onDidChange(refresh),
        watcher.onDidCreate(refresh),
        watcher.onDidDelete(refresh),
      ];
      panel.onDidDispose(() => {
        messageSubscription.dispose();
        watcherSubscriptions.forEach((subscription) => subscription.dispose());
        watcher.dispose();
      });
      await refresh();
    },
  );

  context.subscriptions.push(command);
}

export function deactivate(): void {}
