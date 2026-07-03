import { CheckCircle2, Clock, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { Transfer } from '@/api/client'

// Transfer status badge — uses color + icon + text (not color alone).
// PENDING = amber, COMPLETED = green, FAILED = red.
const STATUS_STYLES: Record<Transfer['status'], { className: string; Icon: typeof Clock }> = {
  PENDING: {
    className: 'bg-warning/12 text-warning border-warning/30',
    Icon: Clock,
  },
  COMPLETED: {
    className: 'bg-success/12 text-success border-success/30',
    Icon: CheckCircle2,
  },
  FAILED: {
    className: 'bg-destructive/12 text-destructive border-destructive/30',
    Icon: XCircle,
  },
}

interface TransferStatusBadgeProps {
  status: Transfer['status']
  className?: string
  size?: 'sm' | 'md'
}

export function TransferStatusBadge({ status, className, size = 'sm' }: TransferStatusBadgeProps) {
  const { className: statusClass, Icon } = STATUS_STYLES[status]
  return (
    <span
      className={cn(
        'inline-flex w-fit shrink-0 items-center gap-1.5 rounded-full border font-medium whitespace-nowrap',
        size === 'sm' ? 'px-2.5 py-0.5 text-xs' : 'px-3 py-1 text-sm',
        statusClass,
        className,
      )}
    >
      <Icon className={size === 'sm' ? 'size-3' : 'size-4'} aria-hidden="true" />
      {status}
    </span>
  )
}
