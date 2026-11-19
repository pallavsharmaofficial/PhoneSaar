import { Adb, AdbDaemonTransport } from "@yume-chan/adb";
import { AdbDaemonWebUsbDeviceManager } from "@yume-chan/adb-daemon-webusb";
import AdbWebCredentialStore from "@yume-chan/adb-credential-web";
import type { Shell } from "./core/executor";

export interface Connection {
  serial: string;
  shell: Shell;
  close(): Promise<void>;
  disconnected: Promise<void>;
}

export type Support = { ok: true } | { ok: false; reason: "no-webusb" | "insecure" };

export function checkSupport(): Support {
  if (!window.isSecureContext) return { ok: false, reason: "insecure" };
  if (!AdbDaemonWebUsbDeviceManager.BROWSER) return { ok: false, reason: "no-webusb" };
  return { ok: true };
}

export class BusyError extends Error {}
export class NoDeviceError extends Error {}

// Opens the browser's device picker, authenticates and returns a shell runner.
export async function connectUsb(): Promise<Connection> {
  const manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  if (!manager) throw new Error("WebUSB is not available");
  const device = await manager.requestDevice();
  if (!device) throw new NoDeviceError("No device was chosen");
  let connection;
  try {
    connection = await device.connect();
  } catch (e) {
    if (e instanceof Error && (e.name === "DeviceBusyError" || /busy|claim/i.test(e.message))) throw new BusyError(e.message);
    throw e;
  }
  const transport = await AdbDaemonTransport.authenticate({
    serial: device.serial,
    connection,
    credentialStore: new AdbWebCredentialStore("PhoneSaar"),
  });
  const adb = new Adb(transport);
  const shell: Shell = async (cmd) => {
    if ("shellProtocol" in adb.subprocess && adb.subprocess.shellProtocol) {
      const r = await adb.subprocess.shellProtocol.spawnWaitText(cmd);
      return { stdout: r.stdout, stderr: r.stderr, exitCode: r.exitCode };
    }
    return { stdout: await adb.subprocess.noneProtocol.spawnWaitText(cmd), stderr: "", exitCode: 0 };
  };
  return { serial: device.serial, shell, close: () => adb.close(), disconnected: adb.disconnected };
}
