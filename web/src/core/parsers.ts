export interface Meminfo { totalKb: number; freeKb: number; availableKb: number }
export interface DfRow { filesystem: string; sizeKb: number; usedKb: number; availKb: number; usePct: number; mount: string }
export interface Battery { level: number; scale: number; tempC: number; health: string; charging: boolean }
export interface Thermal { status: number; statusLabel: string; hottest: { name: string; celsius: number } | null }
export interface Drain { uid: number; mah: number }
export interface Pss { pkg: string; kb: number }
export interface TopRow { cpu: number; name: string }

function num(text: string, re: RegExp): number {
  const m = text.match(re);
  return m ? Number(m[1]) : 0;
}

export function parseMeminfo(text: string): Meminfo {
  return {
    totalKb: num(text, /^MemTotal:\s+(\d+)/m),
    freeKb: num(text, /^MemFree:\s+(\d+)/m),
    availableKb: num(text, /^MemAvailable:\s+(\d+)/m),
  };
}

export function parseDf(text: string): DfRow[] {
  const rows: DfRow[] = [];
  for (const line of text.split("\n")) {
    const m = line.trim().match(/^(\S+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)%\s+(\S+)$/);
    if (m) rows.push({ filesystem: m[1], sizeKb: +m[2], usedKb: +m[3], availKb: +m[4], usePct: +m[5], mount: m[6] });
  }
  return rows;
}

const HEALTH: Record<number, string> = { 1: "Unknown", 2: "Good", 3: "Overheating", 4: "Dead", 5: "Over voltage", 6: "Failure", 7: "Cold" };

export function parseBattery(text: string): Battery {
  const on = (key: string) => new RegExp(`${key} powered:\\s*true`, "i").test(text);
  return {
    level: num(text, /^\s*level:\s*(\d+)/m),
    scale: num(text, /^\s*scale:\s*(\d+)/m) || 100,
    tempC: num(text, /^\s*temperature:\s*(-?\d+)/m) / 10,
    health: HEALTH[num(text, /^\s*health:\s*(\d+)/m)] ?? "Unknown",
    charging: on("AC") || on("USB") || on("Wireless"),
  };
}

const THERMAL: Record<number, string> = { 0: "Normal", 1: "Light", 2: "Moderate", 3: "Severe", 4: "Critical", 5: "Emergency", 6: "Shutdown" };

export function parseThermal(text: string): Thermal {
  const status = num(text, /^Thermal Status:\s*(\d+)/m);
  const start = text.indexOf("Current temperatures from HAL:");
  const section = start >= 0 ? text.slice(start) : text;
  let hottest: Thermal["hottest"] = null;
  for (const m of section.matchAll(/Temperature\{mValue=(-?[\d.]+),[^}]*mName=([^,}]+)/g)) {
    const celsius = Number(m[1]);
    if (!hottest || celsius > hottest.celsius) hottest = { name: m[2], celsius };
  }
  return { status, statusLabel: THERMAL[status] ?? "Unknown", hottest };
}

// "u0a106" -> 10106, "u10a5" -> 1010005, "1000" -> 1000.
export function uidFromLabel(label: string): number | null {
  const m = label.match(/^u(\d+)a(\d+)$/);
  if (m) return Number(m[1]) * 100000 + 10000 + Number(m[2]);
  return /^\d+$/.test(label) ? Number(label) : null;
}

export function parseBatterystats(text: string): Drain[] {
  const out: Drain[] = [];
  for (const m of text.matchAll(/^\s*Uid (\S+?):\s*([\d.]+)/gm)) {
    const uid = uidFromLabel(m[1]);
    if (uid !== null) out.push({ uid, mah: Number(m[2]) });
  }
  return out.sort((a, b) => b.mah - a.mah);
}

export function parsePackageUids(text: string): Map<number, string[]> {
  const map = new Map<number, string[]>();
  for (const m of text.matchAll(/^package:(\S+)\s+uid:(\d+)/gm)) {
    const uid = Number(m[2]);
    map.set(uid, [...(map.get(uid) ?? []), m[1]]);
  }
  return map;
}

export function parsePackageList(text: string): string[] {
  return [...text.matchAll(/^package:(\S+)/gm)].map((m) => m[1]);
}

// Package -> last used time; the 1970 epoch placeholder means never.
export function parseUsageStats(text: string): Map<string, Date | null> {
  const map = new Map<string, Date | null>();
  for (const m of text.matchAll(/package=(\S+)[^\n]*?lastTimeUsed="(\d{4})-(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)"/g)) {
    const [y, mo, d, h, mi, s] = m.slice(2).map(Number);
    const date = y <= 1970 ? null : new Date(y, mo - 1, d, h, mi, s);
    const prev = map.get(m[1]);
    if (prev === undefined || (date && (!prev || date > prev))) map.set(m[1], date);
  }
  return map;
}

// Package -> RAM from the "Total PSS by process" block, summing ":service" processes.
export function parsePss(text: string): Pss[] {
  const start = text.indexOf("Total PSS by process:");
  if (start < 0) return [];
  const end = text.indexOf("Total PSS by OOM", start);
  const block = text.slice(start, end < 0 ? undefined : end);
  const sums = new Map<string, number>();
  for (const m of block.matchAll(/^\s*([\d,]+)K:\s+(\S+)/gm)) {
    const pkg = m[2].split(":")[0];
    sums.set(pkg, (sums.get(pkg) ?? 0) + Number(m[1].replace(/,/g, "")));
  }
  return [...sums].map(([pkg, kb]) => ({ pkg, kb })).sort((a, b) => b.kb - a.kb);
}

export function parseTop(text: string): TopRow[] {
  const rows: TopRow[] = [];
  let seenHeader = false;
  for (const line of text.split("\n")) {
    if (/%CPU/.test(line) && /NAME/.test(line)) { seenHeader = true; continue; }
    const m = line.trim().match(/^(\d+(?:\.\d+)?)\s+(\S+)$/);
    if (seenHeader && m) rows.push({ cpu: Number(m[1]), name: m[2] });
  }
  return rows.sort((a, b) => b.cpu - a.cpu);
}

export function parseGetprop(text: string): Record<string, string> {
  const props: Record<string, string> = {};
  for (const m of text.matchAll(/^\[([^\]]+)\]:\s*\[([^\]]*)\]/gm)) props[m[1]] = m[2];
  return props;
}

export function hasSecurityException(text: string): boolean {
  return /SecurityException|Permission denial/i.test(text);
}

// Plain-language advice for a process that is heating the device.
export function heatAdvice(name: string): string | null {
  if (/dex2oat/.test(name)) return "Android is tuning apps after an update. It stops by itself; leave the phone on charge for a while.";
  if (/com\.android\.vending/.test(name)) return "The Play Store is updating apps. Wait for it to finish, or pause updates in the Play Store.";
  if (/system_server/.test(name)) return "This is Android itself. Heavy use here usually settles once other apps calm down.";
  return null;
}
