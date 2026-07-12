import { existsSync } from "node:fs";
import { resolve, join } from "node:path";

export interface EntioProjectLocation {
  readonly rootPath: string;
  readonly configPath: string;
}

export type FileExists = (path: string) => boolean;

export function detectEntioProject(
  workspaceRoot: string | undefined,
  fileExists: FileExists = existsSync,
): EntioProjectLocation | undefined {
  if (!workspaceRoot) {
    return undefined;
  }

  const rootPath = resolve(workspaceRoot);
  const configPath = join(rootPath, "entio.yaml");

  return fileExists(configPath)
    ? { rootPath, configPath }
    : undefined;
}
