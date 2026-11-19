import type { Drain, Pss } from "./parsers";
import type { CatalogItem } from "./rules";

export interface Candidate extends CatalogItem {
  disabled: boolean;
  lastUsed: Date | null | undefined;
  ramKb: number | null;
}

interface DebloatInput {
  enabled: string[];
  disabled: string[];
  catalog: CatalogItem[];
  isProtected: (pkg: string) => boolean;
  usage: Map<string, Date | null>;
  pss: Pss[];
}

export function debloatCandidates(i: DebloatInput): Candidate[] {
  const enabled = new Set(i.enabled);
  const disabled = new Set(i.disabled);
  const ram = new Map(i.pss.map((p) => [p.pkg, p.kb]));
  return i.catalog
    .filter((c) => !i.isProtected(c.pkg) && (enabled.has(c.pkg) || disabled.has(c.pkg)))
    .map((c) => ({
      ...c,
      disabled: disabled.has(c.pkg),
      lastUsed: i.usage.get(c.pkg),
      ramKb: ram.get(c.pkg) ?? null,
    }))
    .sort((a, b) => Number(a.disabled) - Number(b.disabled) || (a.risk === b.risk ? (b.ramKb ?? 0) - (a.ramKb ?? 0) : a.risk === "safe" ? -1 : 1));
}

export interface Drainer { uid: number; mah: number; packages: string[] }

// Top drainers that map to at least one installed package.
export function topDrainers(stats: Drain[], uidMap: Map<number, string[]>, limit = 8): Drainer[] {
  return stats
    .filter((d) => uidMap.has(d.uid))
    .slice(0, limit)
    .map((d) => ({ uid: d.uid, mah: d.mah, packages: uidMap.get(d.uid) ?? [] }));
}
