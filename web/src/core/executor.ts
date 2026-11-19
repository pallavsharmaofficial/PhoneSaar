import { hasSecurityException } from "./parsers";
import { oemHint, rules as defaultRules, type Rules } from "./rules";
import { SafetyGate, SafetyError } from "./safety";
import type { UndoLog } from "./undo";

export interface ShellResult { stdout: string; stderr: string; exitCode: number }
export type Shell = (cmd: string) => Promise<ShellResult>;

export type Status = "ok" | "blocked" | "refused" | "failed";

export interface ActionResult {
  label: string;
  cmd: string;
  status: Status;
  output: string;
  message: string;
  hint?: string;
}

// Runs every command through the SafetyGate and reports what the device returned.
export class Executor {
  manufacturer = "";
  serial = "";

  constructor(
    private shell: Shell,
    private gate: SafetyGate,
    private log: UndoLog,
    private rules: Rules = defaultRules,
  ) {}

  async read(cmd: string): Promise<string> {
    this.gate.assert(cmd);
    const r = await this.shell(cmd);
    return r.stdout;
  }

  async run(label: string, cmd: string, undoCmd?: string): Promise<ActionResult> {
    const refusal = this.gate.check(cmd) ?? (undoCmd ? this.gate.check(undoCmd) : null);
    if (refusal) return { label, cmd, status: "refused", output: "", message: refusal };
    let r: ShellResult;
    try {
      r = await this.shell(cmd);
    } catch (e) {
      return { label, cmd, status: "failed", output: "", message: `Could not talk to the device: ${(e as Error).message}` };
    }
    const output = [r.stdout, r.stderr].filter((s) => s.trim()).join("\n").trim();
    if (hasSecurityException(output)) {
      const who = this.manufacturer || "your phone's maker";
      return { label, cmd, status: "blocked", output, message: `Blocked by ${who}`, hint: oemHint(this.manufacturer, this.rules) };
    }
    if (r.exitCode !== 0 || /\bException\b|^Error:/m.test(output)) {
      return { label, cmd, status: "failed", output, message: "The device reported a problem." };
    }
    if (undoCmd) this.log.add(this.serial, label, cmd, undoCmd);
    return { label, cmd, status: "ok", output, message: "Done" };
  }
}

export { SafetyError };
