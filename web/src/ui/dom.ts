import type { ActionResult } from "../core/executor";

type Child = Node | string | null | undefined | false;

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Record<string, unknown> = {},
  ...kids: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") el.className = String(v);
    else if (k.startsWith("on") && typeof v === "function") el.addEventListener(k.slice(2), v as EventListener);
    else if (v === true) el.setAttribute(k, "");
    else if (v !== false && v != null) el.setAttribute(k, String(v));
  }
  for (const kid of kids) if (kid) el.append(kid);
  return el;
}

export function confirmDialog(title: string, body: string, okLabel: string): Promise<boolean> {
  const dlg = document.getElementById("confirm") as HTMLDialogElement;
  return new Promise((resolve) => {
    const done = (v: boolean) => { dlg.close(); resolve(v); };
    dlg.replaceChildren(
      h("h2", {}, title),
      h("p", {}, body),
      h("div", { class: "row" },
        h("button", { onclick: () => done(false) }, "Cancel"),
        h("button", { class: "primary", onclick: () => done(true) }, okLabel)),
    );
    dlg.oncancel = () => resolve(false);
    dlg.showModal();
  });
}

export function resultView(r: ActionResult): HTMLElement {
  const kind = r.status === "ok" ? "good" : r.status === "failed" ? "bad" : r.status === "blocked" ? "bad" : "";
  return h("div", { class: `notice ${kind}`, role: "status" },
    h("b", {}, `${r.label}: ${r.message}`),
    r.hint ? h("p", { class: "small" }, r.hint) : null,
    r.output ? h("pre", {}, r.output) : null,
  );
}

// A results area a section fills after each action.
export function resultBox(): { el: HTMLElement; show(rs: ActionResult[]): void; clear(): void } {
  const el = h("div", { class: "result", "aria-live": "polite" });
  return {
    el,
    show: (rs) => el.replaceChildren(...rs.map(resultView)),
    clear: () => el.replaceChildren(),
  };
}

export function download(name: string, text: string): void {
  const url = URL.createObjectURL(new Blob([text], { type: "text/x-shellscript" }));
  const a = h("a", { href: url, download: name });
  document.body.append(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
