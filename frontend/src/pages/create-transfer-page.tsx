import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertCircle, ArrowLeft, Loader2 } from 'lucide-react'
import { getAccounts, createTransfer, type Account } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { formatVnd } from '@/lib/format'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

export default function CreateTransferPage() {
  const navigate = useNavigate()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [accountsError, setAccountsError] = useState<string | null>(null)

  const [fromAccountRef, setFromAccountRef] = useState('')
  const [toAccountRef, setToAccountRef] = useState('')
  const [amount, setAmount] = useState('')
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  // Generated once; reused on retry so the idempotency key stays stable for this form session
  const [idempotencyKey] = useState<string>(() => crypto.randomUUID())

  useEffect(() => {
    getAccounts()
      .then((accs) => {
        setAccounts(accs)
        if (accs.length > 0) setFromAccountRef(accs[0].accountRef)
      })
      .catch(() => setAccountsError('Failed to load accounts.'))
      .finally(() => setLoadingAccounts(false))
  }, [])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitError(null)
    const parsedAmount = parseInt(amount, 10)
    if (!toAccountRef.trim()) {
      setSubmitError('Please enter a destination account reference.')
      return
    }
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setSubmitError('Amount must be a positive integer (minor units, e.g. 100000 = 1000 VND).')
      return
    }
    setPending(true)
    try {
      const res = await createTransfer(
        { fromAccountRef, toAccountRef: toAccountRef.trim(), amount: parsedAmount, currency: 'VND' },
        idempotencyKey,
      )
      navigate(`/transfers/${res.id}`)
    } catch (err) {
      const status = (err as { status?: number }).status
      if (status === 403) {
        setSubmitError('You do not own that source account (403).')
      } else if (status === 503) {
        setSubmitError('Account service unavailable. Please try again shortly.')
      } else if (status === 400) {
        setSubmitError('Bad request — check your inputs.')
      } else {
        setSubmitError('Transfer failed. Please try again.')
      }
    } finally {
      setPending(false)
    }
  }

  // Preview the VND-formatted amount right below the input
  const parsedPreview = parseInt(amount, 10)
  const showPreview = !isNaN(parsedPreview) && parsedPreview > 0

  return (
    <div className="min-h-dvh bg-background">
      <main className="mx-auto max-w-lg px-4 py-6">
        <Button
          variant="ghost"
          size="sm"
          className="mb-4 -ml-2 text-muted-foreground"
          onClick={() => navigate('/transfers')}
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back to list
        </Button>

        <Card>
          <CardHeader>
            <CardTitle>New transfer</CardTitle>
            <CardDescription>Move funds between accounts. Amounts are in VND minor units.</CardDescription>
          </CardHeader>
          <CardContent>
            {loadingAccounts && (
              <div className="flex items-center justify-center gap-2 py-8 text-muted-foreground">
                <Loader2 className="size-5 animate-spin" aria-hidden="true" />
                <span>Loading accounts…</span>
              </div>
            )}

            {accountsError && (
              <p
                role="alert"
                className="flex items-center gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                <AlertCircle className="size-4 shrink-0" aria-hidden="true" />
                {accountsError}
              </p>
            )}

            {!loadingAccounts && !accountsError && (
              <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
                <div className="flex flex-col gap-2">
                  <Label htmlFor="from-account">
                    From account <span className="text-destructive">*</span>
                  </Label>
                  <Select value={fromAccountRef} onValueChange={setFromAccountRef} required>
                    <SelectTrigger id="from-account" className="w-full">
                      <SelectValue placeholder="Select a source account" />
                    </SelectTrigger>
                    <SelectContent>
                      {accounts.map((a) => (
                        <SelectItem key={a.accountRef} value={a.accountRef}>
                          <span className="font-mono">{a.accountRef}</span>
                          <span className="text-muted-foreground">· {a.ownerName}</span>
                          <span className="ml-auto font-medium tabular-nums">{formatVnd(a.balance)}</span>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="to-account">
                    To account ref <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="to-account"
                    type="text"
                    inputMode="numeric"
                    placeholder="e.g. 200000000001"
                    value={toAccountRef}
                    onChange={(e) => setToAccountRef(e.target.value)}
                    required
                    className="font-mono"
                  />
                  <p className="text-xs text-muted-foreground">
                    The reference number of the destination account.
                  </p>
                </div>

                <div className="flex flex-col gap-2">
                  <Label htmlFor="amount">
                    Amount <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="amount"
                    type="number"
                    min={1}
                    step={1}
                    inputMode="numeric"
                    placeholder="e.g. 100000"
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    required
                    className="tabular-nums"
                  />
                  <p className="text-xs text-muted-foreground">
                    {showPreview ? (
                      <>
                        Equivalent to <span className="font-medium text-foreground">{formatVnd(parsedPreview)}</span>
                      </>
                    ) : (
                      'Minor units (VND). Positive integer only.'
                    )}
                  </p>
                </div>

                {submitError && (
                  <p
                    role="alert"
                    className="flex items-center gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
                  >
                    <AlertCircle className="size-4 shrink-0" aria-hidden="true" />
                    {submitError}
                  </p>
                )}

                <div className="flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => navigate('/transfers')}
                    disabled={pending}
                  >
                    Cancel
                  </Button>
                  <Button type="submit" disabled={pending}>
                    {pending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
                    {pending ? 'Submitting…' : 'Submit transfer'}
                  </Button>
                </div>

                <p className="border-t pt-3 text-xs text-muted-foreground">
                  Idempotency key (stable for this form session):{' '}
                  <code className="font-mono break-all text-foreground">{idempotencyKey}</code>
                </p>
              </form>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
