export function money(value: number): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 4 }).format(value ?? 0);
}

export function compact(value: number): string {
  return new Intl.NumberFormat('en-US', { notation: 'compact' }).format(value ?? 0);
}

export function dateTime(ms: number): string {
  return ms ? new Date(ms).toLocaleString() : '-';
}
