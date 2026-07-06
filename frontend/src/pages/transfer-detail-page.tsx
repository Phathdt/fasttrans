import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { AlertCircle, ArrowLeft, Loader2 } from 'lucide-react'
import { useGetTransfer } from '@/api/generated/transfers/transfers'
import type { TransferResponse } from '@/api/generated/models'
import { extractApiError } from '@/api/axios-instance'
import { TransferStatusBadge } from '@/components/transfer-status-badge'
import { formatDateTime, formatVnd } from '@/lib/format'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

const POLL_INTERVAL_MS = 1500
const MAX_POLL_ATTEMPTS = 20

export default function TransferDetailPage() {
  const { id } = useParams<{ id: string }>()
  const attemptRef = useRef(0)
  const [timedOut, setTimedOut] = useState(false)

  const { data: transfer, isError, error, dataUpdatedAt } = useGetTransfer(id!, {
    query: {
      enabled: !!id,
      // Refetch every POLL_INTERVAL_MS while status is PENDING and under attempt limit.
      refetchInterval: (query) => {
        const data = query.state.data as TransferResponse | undefined
        if (!data) return POLL_INTERVAL_MS
        if (data.status !== 'PENDING') return false
        if (attemptRef.current >= MAX_POLL_ATTEMPTS) return false
        return POLL_INTERVAL_MS
      },
    },
  })

  // Count each successful server response; detect timeout after MAX_POLL_ATTEMPTS.
  // dataUpdatedAt changes on every successful fetch, making this a reliable counter trigger.
  useEffect(() => {
    if (!transfer || dataUpdatedAt === 0) return
    attemptRef.current += 1
    if (transfer.status === 'PENDING' && attemptRef.current >= MAX_POLL_ATTEMPTS) {
      setTimedOut(true)
    }
  }, [dataUpdatedAt]) // eslint-disable-line react-hooks/exhaustive-deps

  const isPolling = transfer?.status === 'PENDING' && !timedOut

  return (
    <div className="min-h-dvh bg-background">
      <main className="mx-auto max-w-xl px-4 py-6">
        <Link
          to="/transfers"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back to list
        </Link>

        {!transfer && !isError && (
          <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground">
            <Loader2 className="size-5 animate-spin" aria-hidden="true" />
            <span>Loading transfer…</span>
          </div>
        )}

        {isError && (
          <p
            role="alert"
            className="flex items-center gap-2 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"
          >
            <AlertCircle className="size-4 shrink-0" aria-hidden="true" />
            {extractApiError(error).message || 'Failed to load transfer.'}
          </p>
        )}

        {transfer && (
          <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
              <div>
                <CardTitle className="text-base font-medium text-muted-foreground">Transfer</CardTitle>
                <p className="mt-1 text-3xl font-semibold tabular-nums tracking-tight">
                  {formatVnd(transfer.amount ?? 0)}
                </p>
              </div>
              <div className="flex flex-col items-end gap-1.5">
                <TransferStatusBadge status={transfer.status ?? 'PENDING'} size="md" />
                {isPolling && (
                  <span
                    className="flex items-center gap-1 text-xs text-muted-foreground"
                    aria-live="polite"
                  >
                    <Loader2 className="size-3 animate-spin" aria-hidden="true" />
                    Polling…
                  </span>
                )}
              </div>
            </CardHeader>

            <CardContent className="flex flex-col gap-0 border-t pt-4">
              {transfer.status === 'FAILED' && transfer.reason && (
                <div className="mb-4 flex items-start gap-2 rounded-md bg-destructive/10 px-3 py-2.5 text-sm text-destructive">
                  <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                  <span>
                    <span className="font-medium">Reason: </span>
                    {transfer.reason}
                  </span>
                </div>
              )}

              <dl className="divide-y">
                <DetailRow label="Transfer ID" value={<code className="font-mono text-sm break-all">{transfer.id}</code>} />
                <DetailRow label="From" value={<span className="font-mono text-sm">{transfer.fromAccountRef}</span>} />
                <DetailRow label="To" value={<span className="font-mono text-sm">{transfer.toAccountRef}</span>} />
                <DetailRow label="Amount" value={<span className="font-medium tabular-nums">{formatVnd(transfer.amount ?? 0)}</span>} />
                <DetailRow label="Created" value={<span className="text-sm">{formatDateTime(transfer.createdAt ?? '')}</span>} />
              </dl>
            </CardContent>
          </Card>
        )}

        {timedOut && (
          <p
            role="status"
            className="mt-4 flex items-start gap-2 rounded-md bg-warning/12 px-4 py-3 text-sm text-warning"
          >
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            Transfer is still PENDING after {MAX_POLL_ATTEMPTS} attempts. Refresh the page to check again.
          </p>
        )}
      </main>
    </div>
  )
}

// A single label/value row in the details list
function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-2.5">
      <dt className="shrink-0 text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-right break-all">{value}</dd>
    </div>
  )
}
