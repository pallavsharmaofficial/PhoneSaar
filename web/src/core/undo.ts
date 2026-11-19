export interface UndoEntry {
  id: string;
  time: string;
  serial: string;
  label: string;
  doCmd: string;
  undoCmd: string;
  undone: boolean;
}

export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

const KEY = "phonesaar.undo.v1";

function browserStore(): KeyValueStore | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

export function shQuote(s: string): string {
  return `'${s.replace(/'/g, `'\\''`)}'`;
}

export class UndoLog {
  private entries: UndoEntry[] = [];
  private store: KeyValueStore | null;

  constructor(store: KeyValueStore | null = browserStore()) {
    this.store = store;
    this.load();
  }

  private load(): void {
    try {
      const raw = this.store?.getItem(KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      this.entries = Array.isArray(parsed) ? parsed : [];
    } catch {
      this.entries = [];
    }
  }

  private save(): void {
    try {
      this.store?.setItem(KEY, JSON.stringify(this.entries));
    } catch {
      // Storage may be blocked; the log still works for this session.
    }
  }

  add(serial: string, label: string, doCmd: string, undoCmd: string, now: Date = new Date()): UndoEntry {
    const entry: UndoEntry = {
      id: `${now.getTime()}-${Math.random().toString(36).slice(2, 8)}`,
      time: now.toISOString(),
      serial, label, doCmd, undoCmd, undone: false,
    };
    this.entries.push(entry);
    this.save();
    return entry;
  }

  // Newest first; pending only when requested.
  list(serial?: string, pendingOnly = false): UndoEntry[] {
    return this.entries
      .filter((e) => (serial === undefined || e.serial === serial) && (!pendingOnly || !e.undone))
      .reverse();
  }

  markUndone(id: string): void {
    const e = this.entries.find((x) => x.id === id);
    if (e) {
      e.undone = true;
      this.save();
    }
  }

  // A shell script that runs on the computer and reverts pending changes, newest first.
  exportScript(serial?: string): string {
    const lines = ["#!/bin/sh", "# PhoneSaar undo script. Run it on a computer with adb, phone connected by USB.", "set -u"];
    for (const e of this.list(serial, true)) {
      lines.push(`# ${e.time} ${e.label.replace(/[\r\n]+/g, " ")}`);
      lines.push(`adb -s ${shQuote(e.serial)} shell ${shQuote(e.undoCmd)}`);
    }
    return lines.join("\n") + "\n";
  }
}
