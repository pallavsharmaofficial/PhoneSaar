// Builders for every change command, each paired with its undo command.
export interface Change { label: string; cmd: string; undo: string }

const ANIM_KEYS = ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"];

export function settingChange(label: string, ns: "global" | "secure", key: string, value: string, prev: string): Change {
  const p = prev.trim();
  const undo = !p || p === "null" ? `settings delete ${ns} ${key}` : `settings put ${ns} ${key} ${p}`;
  return { label, cmd: `settings put ${ns} ${key} ${value}`, undo };
}

export function animationChanges(value: string, prev: string[]): Change[] {
  return ANIM_KEYS.map((k, i) => settingChange(`Animation speed (${k.split("_")[0]}) to ${value}`, "global", k, value, prev[i] ?? ""));
}

export const animationKeys = ANIM_KEYS;

export const disableChange = (pkg: string): Change => ({ label: `Turn off ${pkg}`, cmd: `pm disable-user --user 0 ${pkg}`, undo: `pm enable ${pkg}` });

export const restrictChange = (pkg: string): Change => ({
  label: `Restrict background for ${pkg}`,
  cmd: `cmd appops set ${pkg} RUN_ANY_IN_BACKGROUND ignore`,
  undo: `cmd appops set ${pkg} RUN_ANY_IN_BACKGROUND allow`,
});

export const grantChange = (pkg: string, perm: string): Change => ({
  label: `Allow ${perm.replace("android.permission.", "")} for ${pkg}`,
  cmd: `pm grant ${pkg} ${perm}`,
  undo: `pm revoke ${pkg} ${perm}`,
});

export const autoRevokeChange = (pkg: string): Change => ({
  label: `Stop auto-removing permissions for ${pkg}`,
  cmd: `cmd appops set ${pkg} AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore`,
  undo: `cmd appops set ${pkg} AUTO_REVOKE_PERMISSIONS_IF_UNUSED allow`,
});

export const PERMISSIONS = [
  "android.permission.CAMERA",
  "android.permission.RECORD_AUDIO",
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.READ_CONTACTS",
  "android.permission.POST_NOTIFICATIONS",
  "android.permission.READ_EXTERNAL_STORAGE",
];
