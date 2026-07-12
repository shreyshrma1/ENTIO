import { spawn } from "node:child_process";

export interface EngineResponse {
  readonly ok: boolean;
  readonly [key: string]: unknown;
}

export type SpawnProcess = typeof spawn;

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
        if (exitCode !== 0) {
          rejectResponse(
            new Error(
              stderr.join("").trim() || `Entio CLI exited with code ${exitCode ?? "unknown"}.`,
            ),
          );
          return;
        }

        try {
          resolveResponse(JSON.parse(output) as EngineResponse);
        } catch {
          rejectResponse(new Error("Entio CLI returned invalid JSON."));
        }
      });
    });
  }
}
