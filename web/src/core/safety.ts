import { rules as defaultRules, type Rules } from "./rules";

export class SafetyError extends Error {}

const PKG_RE = /^[A-Za-z0-9._]+$/;

// Pulls the package out of a disable or background-restrict command, else null.
export function targetPackage(cmd: string): string | null {
  const c = cmd.trim().replace(/\s+/g, " ");
  const pm = c.match(/\bpm (?:disable-user|disable-until-used|disable|hide|suspend)\b((?: --\S+(?: \S+)?)*) (\S+)$/);
  if (pm) return pm[2];
  const ops = c.match(/\bappops set (?:--user \d+ )?(\S+) (?:RUN_ANY_IN_BACKGROUND|RUN_IN_BACKGROUND) (?:ignore|deny)\b/);
  if (ops) return ops[1];
  return null;
}

export class SafetyGate {
  private forbidden: RegExp[];
  private protectedRe: RegExp[];

  constructor(r: Rules = defaultRules) {
    this.forbidden = r.forbidden.map((p) => new RegExp(p, "i"));
    this.protectedRe = r.protected.map((p) => new RegExp(p));
  }

  isProtected(pkg: string): boolean {
    return this.protectedRe.some((re) => re.test(pkg));
  }

  // Returns the refusal reason, or null when the command may run.
  check(cmd: string): string | null {
    if (this.forbidden.some((re) => re.test(cmd))) return "This command could delete data, so PhoneSaar will never run it.";
    for (const part of cmd.split(/[;&|\n]+|\$\(|`/)) {
      const risky = /\bpm\s+(disable|hide|suspend)|\bappops\s+set\b.*RUN_(ANY_IN_)?BACKGROUND/.test(part);
      const pkg = targetPackage(part);
      if (risky && pkg === null && !/\b(allow|default)\b/.test(part)) return "PhoneSaar could not tell which app that command changes, so it will not run it.";
      if (pkg === null) continue;
      if (!PKG_RE.test(pkg)) return "That is not a valid app name.";
      if (this.isProtected(pkg)) return `${pkg} is protected. Turning it off could log you out or break the phone.`;
    }
    return null;
  }

  assert(cmd: string): void {
    const why = this.check(cmd);
    if (why) throw new SafetyError(why);
  }
}

export function isValidPackage(pkg: string): boolean {
  return PKG_RE.test(pkg);
}
