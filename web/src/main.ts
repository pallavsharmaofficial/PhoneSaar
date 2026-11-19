import "./style.css";
import { animationChanges, animationKeys, autoRevokeChange, disableChange, grantChange, PERMISSIONS, restrictChange, settingChange, type Change } from "./core/commands";
import { debloatCandidates, topDrainers, type Candidate, type Drainer } from "./core/debloat";
import { Executor, type ActionResult } from "./core/executor";
import { describeAge, formatKb } from "./core/format";
import { heatAdvice, parseBatterystats, parsePackageList, parsePackageUids, parsePss, parseUsageStats } from "./core/parsers";
import { collectReport, reportRows, type Report } from "./core/report";
import { rules } from "./core/rules";
import { isValidPackage, SafetyGate } from "./core/safety";
import { UndoLog } from "./core/undo";
import { BusyError, checkSupport, connectUsb, NoDeviceError, type Connection } from "./device";
import { confirmDialog, download, h, resultBox } from "./ui/dom";

const APK_URL = "https://github.com/pallavsharmaofficial/PhoneSaar/releases/download/latest/phonesaar.apk";
const gate = new SafetyGate();
const log = new UndoLog();

const $ = (id: string) => document.getElementById(id) as HTMLElement;
const put = (el: HTMLElement, ...kids: (Node | string | null)[]) => el.replaceChildren(...kids.filter((k): k is Node | string => k !== null));

const state: {
  conn: Connection | null;
  ex: Executor | null;
  before: Report | null;
  after: Report | null;
  drainers: Drainer[] | null;
  candidates: Candidate[] | null;
  apps: string[];
} = { conn: null, ex: null, before: null, after: null, drainers: null, candidates: null, apps: [] };

// Disables a button while an async action runs.
async function busy(btn: HTMLButtonElement, fn: () => Promise<void>): Promise<void> {
  btn.disabled = true;
  try {
    await fn();
  } finally {
    btn.disabled = false;
  }
}

function errText(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

// Runs one confirmed change, shows the real result and refreshes the report.
async function applyChange(c: Change, box: ReturnType<typeof resultBox>): Promise<ActionResult> {
  const r = await state.ex!.run(c.label, c.cmd, c.undo);
  box.show([r]);
  return r;
}

async function refreshAfter(): Promise<void> {
  if (!state.ex) return;
  try {
    state.after = await collectReport(state.ex);
    renderReport();
    renderUndo();
  } catch (e) {
    $("report").append(h("p", { class: "notice bad" }, `Could not refresh the report: ${errText(e)}`));
  }
}

// ---------- Connect ----------
function renderConnect(message?: Node): void {
  const el = $("connect");
  const support = checkSupport();
  const kids: (Node | null)[] = [h("h2", {}, "1. Connect"), h("p", { class: "muted" }, "Plug your phone or TV into this computer with a USB cable.")];
  if (!support.ok) {
    kids.push(h("div", { class: "notice bad" },
      h("b", {}, support.reason === "insecure" ? "This page needs a secure (https) connection." : "This browser cannot talk to a phone over USB."),
      h("span", {}, "Use Chrome or Edge on a computer. Firefox, Safari and iPhone do not support USB for this. To connect over Wi-Fi instead, "),
      h("a", { href: APK_URL }, "download the PhoneSaar Android app"), "."));
  } else if (state.conn) {
    kids.push(
      h("div", { class: "notice good" }, h("b", {}, "Connected"), `${state.before ? [state.before.manufacturer, state.before.model].join(" ") : state.conn.serial}`),
      h("button", { onclick: () => void state.conn?.close() }, "Disconnect"),
    );
  } else {
    kids.push(
      h("ol", { class: "small" },
        h("li", {}, "On the phone, turn on Developer options, then USB debugging."),
        h("li", {}, "Press the button below and pick your device."),
        h("li", {}, "On the phone, tap Allow on the USB debugging question (tick Always allow).")),
      h("div", { class: "notice" }, h("b", {}, "Close other adb programs first"),
        "Android Studio, or adb on the computer, because only one program can hold the USB connection."),
    );
    const btn = h("button", { class: "primary" }, "Connect phone or TV") as HTMLButtonElement;
    btn.onclick = () => void busy(btn, connect);
    kids.push(btn, h("p", { class: "muted small" }, "Want Wi-Fi instead? ", h("a", { href: APK_URL }, "Get the Android app"), "."));
  }
  if (message) kids.push(message);
  el.replaceChildren(...(kids.filter(Boolean) as Node[]));
}

function note(text: string, kind = "bad"): Node {
  return h("div", { class: `notice ${kind}`, role: "alert" }, text);
}

async function connect(): Promise<void> {
  try {
    renderConnect(note("Connecting. If the phone asks, tap Allow.", ""));
    const conn = await connectUsb();
    const ex = new Executor(conn.shell, gate, log);
    ex.serial = conn.serial;
    state.conn = conn;
    state.ex = ex;
    conn.disconnected.then(onDisconnected, onDisconnected);
    state.before = await collectReport(ex);
    ex.manufacturer = state.before.manufacturer;
    state.after = null;
    renderConnect();
    $("device").hidden = false;
    renderAll();
    void scanBattery();
    void scanDebloat();
    void scanApps();
  } catch (e) {
    state.conn = null;
    state.ex = null;
    if (e instanceof NoDeviceError || (e instanceof Error && e.name === "NotFoundError")) return renderConnect();
    if (e instanceof BusyError) {
      return renderConnect(note("Another program is using the phone. Close Android Studio and any adb running on the computer, unplug and replug the cable, then try again."));
    }
    renderConnect(note(`Could not connect: ${errText(e)}. Check the phone for an Allow USB debugging question and try again.`));
  }
}

function onDisconnected(): void {
  state.conn = null;
  state.ex = null;
  state.drainers = state.candidates = null;
  $("device").hidden = true;
  renderConnect(note("The phone was disconnected.", ""));
  renderUndo();
}

// ---------- Report ----------
function renderReport(): void {
  const el = $("report");
  if (!state.before) return;
  const rows = reportRows(state.before, state.after ?? undefined);
  const table = h("table", { class: "report" },
    h("thead", {}, h("tr", {}, h("th", {}, ""), h("th", {}, "Before"), h("th", {}, state.after ? "After" : ""))),
    h("tbody", {}, ...rows.map((r) => h("tr", {}, h("th", { scope: "row" }, r.label), h("td", {}, r.before), h("td", {}, r.after)))));
  const refresh = h("button", {}, "Refresh") as HTMLButtonElement;
  refresh.onclick = () => void busy(refresh, refreshAfter);
  el.replaceChildren(h("h2", {}, "2. Report"), h("p", { class: "muted" }, "Read only. \"Before\" was taken when you connected; \"After\" updates after each action."), table, h("div", { class: "row" }, refresh));
}

// ---------- Boost ----------
function renderBoost(): void {
  const box = resultBox();
  const trim = h("input", { type: "checkbox", id: "trim" }) as HTMLInputElement;
  const go = h("button", { class: "primary" }, "Boost now") as HTMLButtonElement;
  go.onclick = () => void busy(go, async () => {
    const ex = state.ex!;
    const results: ActionResult[] = [];
    for (const cmd of rules.boost.always) results.push(await ex.run(`Boost: ${cmd}`, cmd));
    const prev = await Promise.all(animationKeys.map((k) => ex.read(`settings get global ${k}`)));
    for (const c of animationChanges(rules.boost.animationScale, prev)) results.push(await ex.run(c.label, c.cmd, c.undo));
    if (trim.checked) results.push(await ex.run("Clear temporary app files", rules.boost.optionalTrimCaches));
    box.show(results);
    await refreshAfter();
  });
  put($("boost"), 
    h("h2", {}, "3. Boost"),
    h("p", { class: "muted" }, "One tap. Closes background apps, tidies storage and makes animations quicker. Nothing is deleted."),
    h("label", { class: "toggle", for: "trim" }, trim, h("span", {}, "Also trim caches ", h("span", { class: "muted" }, "(clears temporary app files; never logs out)"))),
    go, box.el);
}

// ---------- Battery ----------
async function scanBattery(): Promise<void> {
  const ex = state.ex;
  if (!ex) return;
  renderBattery("Looking at what uses your battery...");
  try {
    const [stats, uids] = await Promise.all([ex.read("dumpsys batterystats --charged"), ex.read("cmd package list packages -U")]);
    state.drainers = topDrainers(parseBatterystats(stats), parsePackageUids(uids));
    renderBattery();
  } catch (e) {
    renderBattery(`Could not read battery use: ${errText(e)}`);
  }
}

function renderBattery(status?: string): void {
  const box = resultBox();
  const heat = (state.after ?? state.before)?.top.map((t) => ({ t, advice: heatAdvice(t.name) })).filter((x) => x.advice) ?? [];
  const items = (state.drainers ?? []).map((d) =>
    h("li", { class: "item" },
      h("div", { class: "grow" },
        h("div", { class: "name" }, d.packages.join(", ")),
        h("div", { class: "muted small" }, `${Math.round(d.mah)} mAh used since last full charge`)),
      h("div", { class: "row" }, ...d.packages.map((pkg) => {
        if (gate.isProtected(pkg)) return h("span", { class: "tag" }, d.packages.length > 1 ? `${pkg}: protected` : "Protected");
        const b = h("button", {}, d.packages.length > 1 ? `Restrict ${pkg}` : "Restrict background") as HTMLButtonElement;
        b.onclick = () => void busy(b, async () => {
          const c = restrictChange(pkg);
          if (!(await confirmDialog("Restrict background use?", `${pkg} will stop running in the background. It still opens normally. You can undo this.`, "Restrict"))) return;
          await applyChange(c, box);
          await refreshAfter();
        });
        return b;
      }))));
  const rescan = h("button", { onclick: () => void scanBattery() }, "Scan again");
  put($("battery"), 
    h("h2", {}, "4. Battery"),
    h("p", { class: "muted" }, "Apps that used the most battery since the last full charge."),
    status ? h("p", { class: "muted" }, status) : null,
    state.drainers && !state.drainers.length ? h("p", {}, "No heavy apps found.") : null,
    h("ul", { class: "list" }, ...items),
    heat.length ? h("h3", {}, "Why it may be warm") : null,
    ...heat.map((x) => h("p", { class: "small" }, h("b", {}, `${x.t.name} (${x.t.cpu}% CPU): `), x.advice)),
    h("div", { class: "row" }, rescan), box.el);
}

// ---------- Debloat ----------
async function scanDebloat(): Promise<void> {
  const ex = state.ex;
  if (!ex) return;
  renderDebloat("Checking installed apps. This can take a few seconds...");
  try {
    const [enabled, disabled, usage, mem] = await Promise.all([
      ex.read("pm list packages -e"), ex.read("pm list packages -d"), ex.read("dumpsys usagestats"), ex.read("dumpsys meminfo"),
    ]);
    state.candidates = debloatCandidates({
      enabled: parsePackageList(enabled),
      disabled: parsePackageList(disabled),
      catalog: rules.catalog,
      isProtected: (p) => gate.isProtected(p),
      usage: parseUsageStats(usage),
      pss: parsePss(mem),
    });
    renderDebloat();
  } catch (e) {
    renderDebloat(`Could not list apps: ${errText(e)}`);
  }
}

function renderDebloat(status?: string): void {
  const box = resultBox();
  const items = (state.candidates ?? []).map((c) => {
    const action = c.disabled
      ? h("span", { class: "tag" }, "Already turned off")
      : (() => {
          const b = h("button", {}, "Turn off") as HTMLButtonElement;
          b.onclick = () => void busy(b, async () => {
            const ch = disableChange(c.pkg);
            if (!(await confirmDialog(`Turn off ${c.pkg}?`, `${c.description} The app is hidden, not deleted, and you can turn it back on from Undo.`, "Turn off"))) return;
            await applyChange(ch, box);
            await scanDebloat();
            await refreshAfter();
          });
          return b;
        })();
    return h("li", { class: "item" },
      h("div", { class: "grow" },
        h("div", { class: "name" }, c.pkg),
        h("div", { class: "small" }, c.description),
        h("div", { class: "tags" },
          h("span", { class: "tag" }, c.vendor),
          h("span", { class: `tag ${c.risk}` }, c.risk === "safe" ? "Safe" : "Caution"),
          h("span", { class: "tag" }, `Last used: ${describeAge(c.lastUsed)}`),
          h("span", { class: "tag" }, `Memory: ${c.ramKb ? formatKb(c.ramKb) : "not running"}`))),
      action);
  });
  put($("debloat"), 
    h("h2", {}, "5. Debloat"),
    h("p", { class: "muted" }, "Preinstalled apps from a known list. Turning one off hides it; it is never deleted."),
    status ? h("p", { class: "muted" }, status) : null,
    state.candidates && !state.candidates.length ? h("p", {}, "None of the known extras were found.") : null,
    h("ul", { class: "list" }, ...items),
    h("div", { class: "row" }, h("button", { onclick: () => void scanDebloat() }, "Scan again")), box.el);
}

// ---------- Fix-ups ----------
async function scanApps(): Promise<void> {
  try {
    state.apps = parsePackageList(await state.ex!.read("pm list packages -3")).sort();
    renderFixups();
  } catch {
    state.apps = [];
  }
}

function renderFixups(): void {
  const ex = state.ex;
  if (!ex) return;
  const box = resultBox();
  const confirmed = async (title: string, body: string, c: Change | ((prev: string) => Change), prevCmd?: string) => {
    if (!(await confirmDialog(title, body, "Do it"))) return;
    const ch = typeof c === "function" ? c(prevCmd ? await ex.read(prevCmd) : "") : c;
    await applyChange(ch, box);
    await refreshAfter();
  };
  const btn = (label: string, fn: () => Promise<void>) => {
    const b = h("button", {}, label) as HTMLButtonElement;
    b.onclick = () => void busy(b, fn);
    return b;
  };
  const scale = h("select", { "aria-label": "Animation speed" },
    h("option", { value: "0.5" }, "Quick (0.5x)"), h("option", { value: "1.0" }, "Normal (1x)"), h("option", { value: "0" }, "Off")) as HTMLSelectElement;
  const pkgIn = h("input", { type: "text", list: "apps", placeholder: "App name, e.g. com.example.app", "aria-label": "App package name", autocapitalize: "off", autocomplete: "off" }) as HTMLInputElement;
  const perm = h("select", { "aria-label": "Permission" }, ...PERMISSIONS.map((p) => h("option", { value: p }, p.replace("android.permission.", "")))) as HTMLSelectElement;
  const withPkg = (fn: (pkg: string) => Promise<void>) => async () => {
    const pkg = pkgIn.value.trim();
    if (!isValidPackage(pkg)) return box.show([{ label: "App name", cmd: "", status: "refused", output: "", message: "Type or pick an app name first." }]);
    await fn(pkg);
  };
  put($("fixups"), 
    h("h2", {}, "6. Fix-ups"),
    h("p", { class: "muted" }, "Each one asks first and can be undone."),
    h("div", { class: "row" },
      btn("Turn location on", () => confirmed("Turn location on?", "Maps and weather need it.", (p) => settingChange("Location on", "secure", "location_mode", "3", p), "settings get secure location_mode")),
      btn("Faster touch and hold", () => confirmed("Shorten touch and hold?", "Long presses will register in 0.4 seconds.", (p) => settingChange("Touch and hold delay 400 ms", "secure", "long_press_timeout", "400", p), "settings get secure long_press_timeout"))),
    h("h3", {}, "Animation speed"),
    h("div", { class: "row" }, scale, btn("Set speed", async () => {
      const v = scale.value;
      if (!(await confirmDialog("Change animation speed?", `Screen animations will run at ${v === "0" ? "off" : v + "x"}.`, "Do it"))) return;
      const prev = await Promise.all(animationKeys.map((k) => ex.read(`settings get global ${k}`)));
      const rs: ActionResult[] = [];
      for (const c of animationChanges(v, prev)) rs.push(await ex.run(c.label, c.cmd, c.undo));
      box.show(rs);
      await refreshAfter();
    })),
    h("h3", {}, "App permissions"),
    h("p", { class: "small muted" }, "Pick an app, then choose what to do."),
    pkgIn, h("datalist", { id: "apps" }, ...state.apps.map((a) => h("option", { value: a }))),
    h("div", { class: "row", style: "margin-top:8px" }, perm,
      btn("Grant permission", withPkg((pkg) => confirmed(`Allow ${perm.value.replace("android.permission.", "")}?`, `${pkg} will get this permission.`, grantChange(pkg, perm.value)))),
      btn("Stop auto-removing permissions", withPkg((pkg) => confirmed("Turn off permission auto-removal?", `Android will keep ${pkg}'s permissions even if you do not open it for a while.`, autoRevokeChange(pkg))))),
    box.el);
}

// ---------- Undo ----------
function renderUndo(): void {
  const box = resultBox();
  const serial = state.conn?.serial;
  const entries = log.list(serial, true);
  const connected = !!state.ex;
  const revert = async (ids: typeof entries) => {
    const rs: ActionResult[] = [];
    for (const e of ids) {
      const r = await state.ex!.run(`Undo: ${e.label}`, e.undoCmd);
      if (r.status === "ok") log.markUndone(e.id);
      rs.push(r);
    }
    box.show(rs);
    renderUndo();
    if (state.ex) {
      void scanDebloat();
      void refreshAfter();
    }
  };
  const items = entries.map((e) => {
    const b = h("button", { disabled: !connected }, "Undo") as HTMLButtonElement;
    b.onclick = () => void busy(b, () => revert([e]));
    return h("li", { class: "item" },
      h("div", { class: "grow" }, h("div", { class: "name" }, e.label), h("div", { class: "muted small" }, new Date(e.time).toLocaleString())),
      b);
  });
  const all = h("button", { class: "danger", disabled: !connected || !entries.length }, "Undo everything") as HTMLButtonElement;
  all.onclick = () => void busy(all, () => revert(entries));
  const dl = h("button", { disabled: !entries.length, onclick: () => download("phonesaar-undo.sh", log.exportScript(serial)) }, "Download undo script");
  put($("undo"), 
    h("h2", {}, "7. Undo"),
    h("p", { class: "muted" }, "Every change PhoneSaar made is listed here. The script reverts them from a computer with adb."),
    entries.length ? h("ul", { class: "list" }, ...items) : h("p", {}, "Nothing to undo."),
    !connected && entries.length ? h("p", { class: "small muted" }, "Connect the phone to undo from here.") : null,
    h("div", { class: "row" }, all, dl), box.el);
}

function renderAll(): void {
  renderReport();
  renderBoost();
  renderBattery();
  renderDebloat();
  renderFixups();
  renderUndo();
}

renderConnect();
renderUndo();
