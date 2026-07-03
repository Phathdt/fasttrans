import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

// Safely merge Tailwind classes, avoiding conflicts (used by shadcn/ui)
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
