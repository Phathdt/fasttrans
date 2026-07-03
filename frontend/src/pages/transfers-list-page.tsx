import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AlertCircle, ArrowRight, LogOut, Plus, Loader2, Inbox } from 'lucide-react'
import { listTransfers, type Transfer } from '@/api/client'
import { Button } from '@/components/ui/button'
import { TransferStatusBadge } from '@/components/transfer-status-badge'
import { formatDateTime, formatVnd, shortId } from '@/lib/format'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Card } from '@/components/ui/card'

export default function TransfersListPage() {
  const navigate = useNavigate()
  const [transfers, setTransfers] = useState<Transfer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listTransfers()
      .then(setTransfers)
      .catch(() => setError('Failed to load transfers.'))
      .finally(() => setLoading(false))
  }, [])

  function handleLogout() {
    localStorage.removeItem('token')
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-dvh bg-background">
      <header className="border-b bg-card">
        <div className="mx-auto flex max-w-4xl items-center justify-between gap-4 px-4 py-4">
          <div>
            <h1 className="text-xl font-semibold tracking-tight">Transfers</h1>
            <p className="text-sm text-muted-foreground">Your recent money transfers</p>
          </div>
          <div className="flex items-center gap-2">
            <Button onClick={() => navigate('/transfers/new')}>
              <Plus className="size-4" aria-hidden="true" />
              <span className="hidden sm:inline">New transfer</span>
              <span className="sm:hidden">New</span>
            </Button>
            <Button variant="outline" onClick={handleLogout} aria-label="Log out">
              <LogOut className="size-4" aria-hidden="true" />
              <span className="hidden sm:inline">Logout</span>
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-4xl px-4 py-6">
        {loading && (
          <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground">
            <Loader2 className="size-5 animate-spin" aria-hidden="true" />
            <span>Loading transfers…</span>
          </div>
        )}

        {error && (
          <p
            role="alert"
            className="flex items-center gap-2 rounded-md bg-destructive/10 px-4 py-3 text-sm text-destructive"
          >
            <AlertCircle className="size-4 shrink-0" aria-hidden="true" />
            {error}
          </p>
        )}

        {!loading && !error && transfers.length === 0 && (
          <Card className="flex flex-col items-center gap-3 py-16 text-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
              <Inbox className="size-6" aria-hidden="true" />
            </div>
            <div>
              <p className="font-medium">No transfers yet</p>
              <p className="text-sm text-muted-foreground">Create your first transfer to get started.</p>
            </div>
            <Button onClick={() => navigate('/transfers/new')}>
              <Plus className="size-4" aria-hidden="true" />
              New transfer
            </Button>
          </Card>
        )}

        {!loading && !error && transfers.length > 0 && (
          <Card className="overflow-hidden py-0">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>ID</TableHead>
                    <TableHead>From</TableHead>
                    <TableHead>To</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Created</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {transfers.map((t) => (
                    <TableRow
                      key={t.id}
                      className="cursor-pointer"
                      onClick={() => navigate(`/transfers/${t.id}`)}
                    >
                      <TableCell>
                        <Link
                          to={`/transfers/${t.id}`}
                          className="inline-flex items-center gap-1 font-mono text-sm text-primary hover:underline"
                          onClick={(e) => e.stopPropagation()}
                        >
                          {shortId(t.id)}
                          <ArrowRight className="size-3" aria-hidden="true" />
                        </Link>
                      </TableCell>
                      <TableCell className="font-mono text-sm">{t.fromAccountRef}</TableCell>
                      <TableCell className="font-mono text-sm">{t.toAccountRef}</TableCell>
                      <TableCell className="text-right font-medium tabular-nums whitespace-nowrap">
                        {formatVnd(t.amount)}
                      </TableCell>
                      <TableCell>
                        <TransferStatusBadge status={t.status} />
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                        {formatDateTime(t.createdAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </Card>
        )}
      </main>
    </div>
  )
}
