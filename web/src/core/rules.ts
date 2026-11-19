import raw from "../../../rules/rules.json";

export interface CatalogItem {
  pkg: string;
  vendor: string;
  risk: "safe" | "caution";
  kind: string;
  description: string;
}

export interface Rules {
  version: number;
  forbidden: string[];
  protected: string[];
  catalog: CatalogItem[];
  oemHints: Record<string, string>;
  boost: { always: string[]; animationScale: string; optionalTrimCaches: string };
}

export function loadRules(): Rules {
  return raw as Rules;
}

export const rules: Rules = loadRules();

// Returns the OEM unlock hint for a manufacturer, matched in lowercase.
export function oemHint(manufacturer: string, r: Rules = rules): string {
  const key = manufacturer.trim().toLowerCase();
  return r.oemHints[key] ?? r.oemHints["default"];
}
