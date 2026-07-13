import { spawn } from "node:child_process";

export interface EngineResponse {
  readonly ok: boolean;
  readonly [key: string]: unknown;
}

export type SpawnProcess = typeof spawn;

export function cliFailureMessage(
  stdout: string,
  stderr: string,
  exitCode: number | null,
): string {
  try {
    const response = JSON.parse(stdout.trim()) as {
      readonly error?: { readonly message?: unknown };
    };
    const message = response.error?.message;
    if (typeof message === "string" && message.length > 0) {
      return message;
    }
  } catch {
    // Fall back to stderr when the process did not return structured JSON.
  }

  return stderr.trim() || `Entio CLI exited with code ${exitCode ?? "unknown"}.`;
}

export class EntioEngineClient {
  public constructor(
    private readonly executable: string = "entio",
    private readonly spawnProcess: SpawnProcess = spawn,
  ) {}

  public run(
    args: readonly string[],
    workingDirectory?: string,
  ): Promise<EngineResponse> {
    return new Promise((resolveResponse, rejectResponse) => {
      const child = this.spawnProcess(
        this.executable,
        [...args],
        {
          cwd: workingDirectory,
          stdio: ["ignore", "pipe", "pipe"],
        },
      );
      const stdout: string[] = [];
      const stderr: string[] = [];

      child.stdout.on("data", (chunk: Buffer | string) => stdout.push(chunk.toString()));
      child.stderr.on("data", (chunk: Buffer | string) => stderr.push(chunk.toString()));
      child.once("error", (error: Error) => rejectResponse(error));
      child.once("close", (exitCode: number | null) => {
        const output = stdout.join("").trim();
        try {
          const response = JSON.parse(output) as EngineResponse;
          if (exitCode !== 0 && typeof response.ok !== "boolean") {
            throw new Error("The CLI response did not contain a result status.");
          }
          resolveResponse(response);
        } catch {
          rejectResponse(
            new Error(
              exitCode !== 0
                ? cliFailureMessage(output, stderr.join(""), exitCode)
                : "Entio CLI returned invalid JSON.",
            ),
          );
        }
      });
    });
  }
}
