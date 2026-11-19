export function formatKb(kb: number): string {
  if (kb >= 1024 * 1024) return `${(kb / 1024 / 1024).toFixed(1)} GB`;
  return `${Math.round(kb / 1024)} MB`;
}

export function describeAge(date: Date | null | undefined, now: Date = new Date()): string {
  if (!date) return "never";
  const days = Math.floor((now.getTime() - date.getTime()) / 86400000);
  if (days < 1) return "today";
  if (days < 31) return `${days} day${days === 1 ? "" : "s"} ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} month${months === 1 ? "" : "s"} ago`;
  return "over a year ago";
}
