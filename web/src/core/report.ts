import { formatKb } from "./format";
import { parseBattery, parseDf, parseGetprop, parseMeminfo, parseThermal, parseTop, type Battery, type DfRow, type Meminfo, type Thermal, type TopRow } from "./parsers";
import type { Executor } from "./executor";

export interface Report {
  manufacturer: string;
  model: string;
  android: string;
  serial: string;
  mem: Meminfo;
  data: DfRow | null;
  sd: DfRow | null;
  battery: Battery;
  thermal: Thermal;
  animations: string;
  location: string;
  top: TopRow[];
}

const LOCATION: Record<string, string> = { "0": "Off", "1": "Device only", "2": "Battery saving", "3": "On" };

const clean = (s: string) => (s.trim() === "null" || !s.trim() ? "default" : s.trim());

export function buildReport(raw: Record<"getprop" | "meminfo" | "df" | "battery" | "thermal" | "top" | "win" | "trans" | "dur" | "loc", string>): Report {
  const p = parseGetprop(raw.getprop);
  const rows = parseDf(raw.df);
  const loc = raw.loc.trim();
  return {
    manufacturer: p["ro.product.manufacturer"] ?? "",
    model: p["ro.product.model"] ?? "",
    android: p["ro.build.version.release"] ?? "",
    serial: p["ro.serialno"] ?? "",
    mem: parseMeminfo(raw.meminfo),
    data: rows.find((r) => /^\/data(\/|$)/.test(r.mount)) ?? null,
    sd: rows.find((r) => /^\/storage\/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$/.test(r.mount)) ?? null,
    battery: parseBattery(raw.battery),
    thermal: parseThermal(raw.thermal),
    animations: [raw.win, raw.trans, raw.dur].map(clean).join(" / "),
    location: LOCATION[loc] ?? clean(loc),
    top: parseTop(raw.top).slice(0, 5),
  };
}

export async function collectReport(ex: Executor): Promise<Report> {
  const [getprop, meminfo, df, battery, thermal, top, win, trans, dur, loc] = await Promise.all([
    ex.read("getprop"),
    ex.read("cat /proc/meminfo"),
    ex.read("df"),
    ex.read("dumpsys battery"),
    ex.read("dumpsys thermalservice"),
    ex.read("top -b -n 1 -m 8 -o %CPU,NAME"),
    ex.read("settings get global window_animation_scale"),
    ex.read("settings get global transition_animation_scale"),
    ex.read("settings get global animator_duration_scale"),
    ex.read("settings get secure location_mode"),
  ]);
  return buildReport({ getprop, meminfo, df, battery, thermal, top, win, trans, dur, loc });
}

export interface ReportRow { label: string; before: string; after: string }

const disk = (r: DfRow | null) => (r ? `${formatKb(r.availKb)} free of ${formatKb(r.sizeKb)}` : "Not found");

function lines(r: Report): [string, string][] {
  const ramFree = r.mem.totalKb ? Math.round((r.mem.availableKb / r.mem.totalKb) * 100) : 0;
  return [
    ["Phone", [r.manufacturer, r.model].filter(Boolean).join(" ") || "Unknown"],
    ["Android", r.android || "Unknown"],
    ["Memory free", `${formatKb(r.mem.availableKb)} of ${formatKb(r.mem.totalKb)} (${ramFree}%)`],
    ["Storage (/data)", disk(r.data)],
    ["SD card", disk(r.sd)],
    ["Battery", `${r.battery.level}%, ${r.battery.tempC.toFixed(1)} °C, ${r.battery.health}${r.battery.charging ? ", charging" : ""}`],
    ["Heat", `${r.thermal.statusLabel}${r.thermal.hottest ? `, hottest ${r.thermal.hottest.name} ${r.thermal.hottest.celsius.toFixed(1)} °C` : ""}`],
    ["Animation speed", r.animations],
    ["Location", r.location],
    ["Busiest now", r.top.map((t) => `${t.name} ${t.cpu}%`).join(", ") || "Nothing"],
  ];
}

export function reportRows(before: Report, after?: Report): ReportRow[] {
  const b = lines(before);
  const a = after ? lines(after) : null;
  return b.map(([label, v], i) => ({ label, before: v, after: a ? a[i][1] : "" }));
}
