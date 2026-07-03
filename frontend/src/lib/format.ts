// Shared formatting utilities for the whole app

// Format an amount Vietnamese-style: 1000000 -> "1.000.000 ₫"
// Note: the amount is an integer in minor units per the API; display the raw value plus the ₫ symbol.
export function formatVnd(amount: number): string {
  return `${new Intl.NumberFormat('vi-VN').format(amount)} ₫`
}

// Shorten a long ID to the first 8 characters plus an ellipsis
export function shortId(id: string): string {
  return id.slice(0, 8) + '…'
}

// Format date and time using the browser locale
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString()
}
