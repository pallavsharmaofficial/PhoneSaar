import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { debloatCandidates, topDrainers } from "./debloat";
import { Executor, type Shell } from "./executor";
import { describeAge } from "./format";
import * as P from "./parsers";
import { buildReport, reportRows } from "./report";
import { oemHint, rules } from "./rules";
import { SafetyGate, targetPackage } from "./safety";
import { UndoLog, type KeyValueStore } from "./undo";
import { animationChanges, disableChange, restrictChange, settingChange } from "./commands";

const fx = (name: string) => readFileSync(fileURLToPath(new URL(`../../../rules/fixtures/${name}.txt`, import.meta.url)), "utf8");
const memStore = (): KeyValueStore & { data: Map<string, string> } => {
  const data = new Map<string, string>();
  return { data, getItem: (k) => data.get(k) ?? null, setItem: (k, v) => void data.set(k, v) };
};

describe("parsers", () => {
  it("meminfo", () => {
    expect(P.parseMeminfo(fx("meminfo"))).toEqual({ totalKb: 2883928, freeKb: 85152, availableKb: 781764 });
  });
  it("df", () => {
    const [row] = P.parseDf(fx("df_data"));
    expect(row).toMatchObject({ sizeKb: 23960540, availKb: 1737272, usePct: 93, mount: "/data/user/0" });
  });
  it("battery", () => {
    expect(P.parseBattery(fx("battery"))).toEqual({ level: 47, scale: 100, tempC: 40.7, health: "Good", charging: true });
  });
  it("thermal", () => {
    const t = P.parseThermal(fx("thermal"));
    expect(t.status).toBe(3);
    expect(t.statusLabel).toBe("Severe");
    expect(t.hottest).toEqual({ name: "CPU4", celsius: 63.6 });
  });
  it("batterystats to uid drain", () => {
    const d = P.parseBatterystats(fx("batterystats"));
    expect(d.slice(0, 3)).toEqual([{ uid: 1000, mah: 676 }, { uid: 10106, mah: 591 }, { uid: 10413, mah: 512 }]);
    expect(d.find((x) => x.uid === 0)?.mah).toBe(376);
    expect(P.uidFromLabel("u10a5")).toBe(1010005);
  });
  it("uid map", () => {
    const m = P.parsePackageUids(fx("packages_uid"));
    expect(m.get(10031)).toEqual(["com.google.android.gms", "com.google.android.gsf"]);
    expect(m.get(10106)).toEqual(["com.google.android.apps.maps"]);
  });
  it("package lists", () => {
    expect(P.parsePackageList(fx("packages_enabled"))).toContain("com.mygalaxy");
    expect(P.parsePackageList(fx("packages_disabled"))).toEqual(["com.facebook.system"]);
  });
  it("usagestats", () => {
    const u = P.parseUsageStats(fx("usagestats"));
    expect(u.get("com.whatsapp")?.getFullYear()).toBe(2026);
    expect(u.get("com.mygalaxy")).toBeNull();
  });
  it("pss", () => {
    const p = P.parsePss(fx("meminfo_pss"));
    expect(p[0]).toEqual({ pkg: "com.netflix.ninja", kb: 87841 });
    expect(p).toHaveLength(5);
  });
  it("top", () => {
    const t = P.parseTop(fx("top"));
    expect(t[0]).toEqual({ cpu: 334, name: "dex2oat32" });
    expect(t.map((r) => r.name)).toContain("com.android.vending:background");
  });
  it("getprop", () => {
    expect(P.parseGetprop(fx("getprop"))["ro.product.manufacturer"]).toBe("OPPO");
  });
  it("security exception", () => {
    expect(P.hasSecurityException(fx("security_exception"))).toBe(true);
    expect(P.hasSecurityException(fx("battery"))).toBe(false);
    expect(oemHint("OPPO")).toContain("Disable permission monitoring");
    expect(oemHint("Unknownco")).toBe(rules.oemHints.default);
  });
  it("heat advice", () => {
    expect(P.heatAdvice("dex2oat32")).toContain("tuning");
    expect(P.heatAdvice("top")).toBeNull();
  });
});

describe("SafetyGate", () => {
  const gate = new SafetyGate();
  it.each([
    "pm clear com.foo",
    "pm uninstall com.foo",
    "cmd package uninstall com.foo",
    "am broadcast -a android.intent.action.MASTER_CLEAR",
    "rm -rf /sdcard/DCIM",
    "rm -rf /data/local/tmp",
    "wipe data",
    "reboot recovery",
    "settings put global adb_enabled 0",
    "sm fstrim; pm clear com.foo",
  ])("refuses %s", (c) => expect(gate.check(c)).not.toBeNull());

  it("allows the thumbnail cleanup and the normal commands", () => {
    expect(gate.check("rm -rf /storage/emulated/0/DCIM/.thumbnails/*")).toBeNull();
    for (const c of ["am kill-all", "sm fstrim", "pm trim-caches 999G", "pm disable-user --user 0 com.mygalaxy", "cmd appops set com.facebook.lite RUN_ANY_IN_BACKGROUND ignore"]) {
      expect(gate.check(c)).toBeNull();
    }
  });
  it("blocks disable and restrict of protected packages", () => {
    expect(gate.check("pm disable-user --user 0 com.google.android.gms")).toMatch(/protected/);
    expect(gate.check("pm disable-user com.android.systemui")).toMatch(/protected/);
    expect(gate.check("cmd appops set com.whatsapp RUN_ANY_IN_BACKGROUND ignore")).toMatch(/protected/);
    expect(gate.check("pm disable-user --user 0 com.mygalaxy; pm disable-user com.android.vending")).toMatch(/protected/);
  });
  it("allows re-enabling and allowing a protected package", () => {
    expect(gate.check("pm enable com.google.android.gms")).toBeNull();
    expect(gate.check("cmd appops set com.whatsapp RUN_ANY_IN_BACKGROUND allow")).toBeNull();
  });
  it("extracts targets", () => {
    expect(targetPackage("pm disable-user --user 0 com.a.b")).toBe("com.a.b");
    expect(targetPackage("am kill-all")).toBeNull();
  });
  it("never lets a catalog entry be protected", () => {
    expect(rules.catalog.filter((c) => gate.isProtected(c.pkg))).toEqual([]);
  });
});

describe("UndoLog", () => {
  it("persists, reverts and exports newest first", () => {
    const store = memStore();
    const log = new UndoLog(store);
    const a = log.add("S1", "Turn off a", "pm disable-user --user 0 com.a", "pm enable com.a");
    log.add("S1", "Turn off b", "pm disable-user --user 0 com.b", "pm enable com.b");
    log.add("S2", "other", "x", "pm enable com.c");
    expect(new UndoLog(store).list("S1")).toHaveLength(2);
    log.markUndone(a.id);
    const script = new UndoLog(store).exportScript("S1");
    expect(script.startsWith("#!/bin/sh")).toBe(true);
    expect(script).toContain("adb -s 'S1' shell 'pm enable com.b'");
    expect(script).not.toContain("com.a'");
    expect(script).not.toContain("com.c");
  });
  it("quotes single quotes in commands", () => {
    const log = new UndoLog(memStore());
    log.add("S", "l", "d", "echo 'hi'");
    expect(log.exportScript()).toContain(`'echo '\\''hi'\\'''`);
  });
  it("survives storage that throws or holds junk", () => {
    const bad: KeyValueStore = { getItem: () => { throw new Error("no"); }, setItem: () => { throw new Error("no"); } };
    const log = new UndoLog(bad);
    log.add("S", "l", "d", "u");
    expect(log.list()).toHaveLength(1);
    const junk = memStore();
    junk.setItem("phonesaar.undo.v1", "{not json");
    expect(new UndoLog(junk).list()).toEqual([]);
    expect(new UndoLog(null).list()).toEqual([]);
  });
});

describe("Executor", () => {
  const make = (shell: Shell, manufacturer = "OPPO") => {
    const log = new UndoLog(memStore());
    const ex = new Executor(shell, new SafetyGate(), log);
    ex.manufacturer = manufacturer;
    ex.serial = "S1";
    return { ex, log };
  };
  const ok: Shell = async () => ({ stdout: "Package com.mygalaxy new state: disabled-user\n", stderr: "", exitCode: 0 });

  it("runs, logs undo and shows real output", async () => {
    const { ex, log } = make(ok);
    const c = disableChange("com.mygalaxy");
    const r = await ex.run(c.label, c.cmd, c.undo);
    expect(r.status).toBe("ok");
    expect(r.output).toContain("disabled-user");
    expect(log.list("S1")).toHaveLength(1);
  });
  it("maps SecurityException to Blocked by <OEM>, never success", async () => {
    const shell: Shell = async () => ({ stdout: "", stderr: fx("security_exception"), exitCode: 0 });
    const { ex, log } = make(shell);
    const r = await ex.run("Location on", "settings put secure location_mode 3", "settings delete secure location_mode");
    expect(r.status).toBe("blocked");
    expect(r.message).toBe("Blocked by OPPO");
    expect(r.hint).toContain("Disable permission monitoring");
    expect(log.list()).toHaveLength(0);
  });
  it("refuses forbidden and protected commands without calling the device", async () => {
    let called = 0;
    const { ex } = make(async () => { called++; return { stdout: "", stderr: "", exitCode: 0 }; });
    expect((await ex.run("x", "pm clear com.foo")).status).toBe("refused");
    expect((await ex.run("x", "pm disable-user --user 0 com.android.vending")).status).toBe("refused");
    await expect(ex.read("pm uninstall com.foo")).rejects.toThrow();
    expect(called).toBe(0);
  });
  it("reports non-zero exit as failed", async () => {
    const { ex } = make(async () => ({ stdout: "", stderr: "Failure", exitCode: 1 }));
    expect((await ex.run("x", "am kill-all")).status).toBe("failed");
  });
});

describe("report, debloat, battery", () => {
  const raw = {
    getprop: fx("getprop"), meminfo: fx("meminfo"), df: fx("df_data"), battery: fx("battery"),
    thermal: fx("thermal"), top: fx("top"), win: "1.0", trans: "1.0", dur: "null", loc: "3",
  };
  it("builds before/after rows", () => {
    const r = buildReport(raw);
    expect(r.manufacturer).toBe("OPPO");
    expect(r.data?.availKb).toBe(1737272);
    const rows = reportRows(r, buildReport({ ...raw, win: "0.5" }));
    expect(rows.find((x) => x.label === "Animation speed")).toEqual({ label: "Animation speed", before: "1.0 / 1.0 / default", after: "0.5 / 1.0 / default" });
    expect(rows.find((x) => x.label === "Location")?.before).toBe("On");
  });
  it("finds debloat candidates, never protected, with RAM and last used", () => {
    const gate = new SafetyGate();
    const list = debloatCandidates({
      enabled: P.parsePackageList(fx("packages_enabled")),
      disabled: P.parsePackageList(fx("packages_disabled")),
      catalog: rules.catalog,
      isProtected: (p) => gate.isProtected(p),
      usage: P.parseUsageStats(fx("usagestats")),
      pss: P.parsePss(fx("meminfo_pss")),
    });
    const names = list.map((c) => c.pkg);
    expect(names).toContain("com.mygalaxy");
    expect(names).not.toContain("com.google.android.gms");
    expect(list.find((c) => c.pkg === "com.facebook.system")?.disabled).toBe(true);
    expect(list.find((c) => c.pkg === "com.mygalaxy")?.lastUsed).toBeNull();
    expect(list.find((c) => c.pkg === "com.microsoft.skydrive")?.ramKb).toBeNull();
  });
  it("maps top drainers to packages", () => {
    const d = topDrainers(P.parseBatterystats(fx("batterystats")), P.parsePackageUids(fx("packages_uid")));
    expect(d[0]).toEqual({ uid: 1000, mah: 676, packages: ["android"] });
    expect(d.map((x) => x.uid)).toContain(10413);
    expect(d.some((x) => x.uid === 0)).toBe(false);
  });
  it("formats age", () => {
    const now = new Date(2026, 10, 12);
    expect(describeAge(null, now)).toBe("never");
    expect(describeAge(new Date(2026, 6, 23), now)).toBe("3 months ago");
  });
  it("builds undo-paired commands", () => {
    expect(settingChange("l", "secure", "long_press_timeout", "400", "null").undo).toBe("settings delete secure long_press_timeout");
    expect(animationChanges("0.5", ["1.0", "1.0", "1.0"])[0].undo).toBe("settings put global window_animation_scale 1.0");
    expect(restrictChange("com.a").undo).toMatch(/allow$/);
    const gate = new SafetyGate();
    for (const c of [disableChange("com.a"), restrictChange("com.a")]) expect(gate.check(c.cmd)).toBeNull();
  });
});
