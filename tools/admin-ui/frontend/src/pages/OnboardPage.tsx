import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import { Form, FormField, FormItem, FormLabel, FormControl, FormMessage } from '@/components/ui/form'

interface AccountRow {
  account_id: string
  display_name: string
  active: boolean
  created_at: string
}

interface AgentRow {
  agent_id: string
  account_id: string
  display_name: string
  active: boolean
  created_at: string
}

interface ApiKeyRow {
  key_hash: string
  generation: number
  active: boolean
  expires_at: string | null
  created_at: string
}

interface BrokerRow {
  broker_account_id: string
  agent_id: string
  broker_id: string
  external_account_id: string
  active: boolean
  created_at: string
}

function shortHash(v: string): string {
  if (v.length <= 16) return v
  return `${v.slice(0, 8)}…${v.slice(-8)}`
}

async function assertOk(res: Response): Promise<void> {
  if (res.ok) return
  const data = await res.json().catch(() => ({}))
  throw new Error(data.detail ?? `Request failed (${res.status})`)
}

// ── Accounts ─────────────────────────────────────────────────────────────────

const AccountSchema = z.object({
  accountId:   z.string().min(1, 'Required'),
  displayName: z.string().min(1, 'Required'),
})

function AccountsTab() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const { data } = useQuery<{ accounts: AccountRow[] }>({
    queryKey: ['accounts'],
    queryFn: () => fetch('/api/onboard/accounts').then(r => r.json()),
    refetchInterval: 5000,
  })
  const mutation = useMutation({
    mutationFn: (body: z.infer<typeof AccountSchema>) =>
      fetch('/api/onboard/accounts', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); setOpen(false) },
  })
  const deleteMutation = useMutation({
    mutationFn: async (accountId: string) => {
      const res = await fetch(`/api/onboard/accounts/${encodeURIComponent(accountId)}`, { method: 'DELETE' })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data.detail ?? `Failed to delete ${accountId}`)
      }
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }) },
  })
  const updateMutation = useMutation({
    mutationFn: async ({ accountId, body }: { accountId: string; body: { displayName?: string; active?: boolean } }) => {
      const res = await fetch(`/api/onboard/accounts/${encodeURIComponent(accountId)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }) },
  })
  const form = useForm({ resolver: zodResolver(AccountSchema) })

  const accounts = data?.accounts ?? []

  function handleDelete(accountId: string) {
    const ok = window.confirm(
      `Delete account ${accountId}? This also deletes related agents, API keys, and broker account links.`,
    )
    if (!ok) return
    deleteMutation.mutate(accountId)
  }

  function handleEdit(row: AccountRow) {
    const displayName = window.prompt(`Update display name for ${row.account_id}`, row.display_name)
    if (!displayName || displayName.trim().length === 0) return
    updateMutation.mutate({ accountId: row.account_id, body: { displayName: displayName.trim() } })
  }

  function handleToggle(row: AccountRow) {
    updateMutation.mutate({ accountId: row.account_id, body: { active: !row.active } })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild><Button size="sm">+ Create Account</Button></DialogTrigger>
          <DialogContent>
            <DialogHeader><DialogTitle>Create Account</DialogTitle></DialogHeader>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(v => mutation.mutate(v))} className="flex flex-col gap-4">
                <FormField control={form.control} name="accountId" render={({ field }) => (
                  <FormItem><FormLabel>Account ID</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name="displayName" render={({ field }) => (
                  <FormItem><FormLabel>Display Name</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <Button type="submit" disabled={mutation.isPending}>Create</Button>
              </form>
            </Form>
          </DialogContent>
        </Dialog>
      </div>
      <div className="overflow-x-auto rounded-md border">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">account_id</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">display_name</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">active</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">created_at</th>
              <th className="text-right py-2 px-3 font-medium text-muted-foreground">actions</th>
            </tr>
          </thead>
          <tbody>
            {accounts.map((row, i) => {
              const accountId = String(row.account_id ?? '')
              return (
                <tr key={`${accountId}-${i}`} className="border-b last:border-0">
                  <td className="py-2 px-3 font-mono max-w-xs truncate">{accountId}</td>
                  <td className="py-2 px-3 font-mono max-w-xs truncate">{row.display_name}</td>
                  <td className="py-2 px-3 font-mono">{String(row.active)}</td>
                  <td className="py-2 px-3 font-mono max-w-xs truncate">{row.created_at}</td>
                  <td className="py-2 px-3 text-right space-x-2">
                    <Button size="sm" variant="outline" onClick={() => handleEdit(row)}>Edit</Button>
                    <Button size="sm" variant="secondary" onClick={() => handleToggle(row)}>
                      {row.active ? 'Deactivate' : 'Activate'}
                    </Button>
                    <Button
                      size="sm"
                      variant="destructive"
                      disabled={deleteMutation.isPending || updateMutation.isPending}
                      onClick={() => handleDelete(accountId)}
                    >
                      Delete
                    </Button>
                  </td>
                </tr>
              )
            })}
            {accounts.length === 0 && (
              <tr>
                <td colSpan={5} className="py-4 text-center text-muted-foreground">No records yet</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {deleteMutation.isError && (
        <p className="text-sm text-red-600">{(deleteMutation.error as Error).message}</p>
      )}
      {updateMutation.isError && (
        <p className="text-sm text-red-600">{(updateMutation.error as Error).message}</p>
      )}
    </div>
  )
}

// ── Agents ────────────────────────────────────────────────────────────────────

const AgentSchema = z.object({
  agentId:     z.string().min(1, 'Required'),
  accountId:   z.string().min(1, 'Required'),
  displayName: z.string().min(1, 'Required'),
})

function AgentsTab() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const { data } = useQuery<{ agents: AgentRow[] }>({
    queryKey: ['agents'],
    queryFn: () => fetch('/api/onboard/agents').then(r => r.json()),
    refetchInterval: 5000,
  })
  const mutation = useMutation({
    mutationFn: (body: z.infer<typeof AgentSchema>) =>
      fetch('/api/onboard/agents', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['agents'] }); setOpen(false) },
  })
  const updateMutation = useMutation({
    mutationFn: async ({ agentId, body }: { agentId: string; body: { accountId?: string; displayName?: string; active?: boolean } }) => {
      const res = await fetch(`/api/onboard/agents/${encodeURIComponent(agentId)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['agents'] }) },
  })
  const deleteMutation = useMutation({
    mutationFn: async (agentId: string) => {
      const res = await fetch(`/api/onboard/agents/${encodeURIComponent(agentId)}`, { method: 'DELETE' })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['agents'] }) },
  })
  const form = useForm({ resolver: zodResolver(AgentSchema) })

  const agents = data?.agents ?? []

  function handleEdit(row: AgentRow) {
    const displayName = window.prompt(`Update display name for ${row.agent_id}`, row.display_name)
    if (!displayName || displayName.trim().length === 0) return
    updateMutation.mutate({ agentId: row.agent_id, body: { displayName: displayName.trim() } })
  }

  function handleMove(row: AgentRow) {
    const accountId = window.prompt(`Move ${row.agent_id} to account`, row.account_id)
    if (!accountId || accountId.trim().length === 0) return
    updateMutation.mutate({ agentId: row.agent_id, body: { accountId: accountId.trim() } })
  }

  function handleToggle(row: AgentRow) {
    updateMutation.mutate({ agentId: row.agent_id, body: { active: !row.active } })
  }

  function handleDelete(agentId: string) {
    if (!window.confirm(`Delete agent ${agentId}?`)) return
    deleteMutation.mutate(agentId)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild><Button size="sm">+ Create Agent</Button></DialogTrigger>
          <DialogContent>
            <DialogHeader><DialogTitle>Create Agent</DialogTitle></DialogHeader>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(v => mutation.mutate(v))} className="flex flex-col gap-4">
                <FormField control={form.control} name="agentId" render={({ field }) => (
                  <FormItem><FormLabel>Agent ID</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name="accountId" render={({ field }) => (
                  <FormItem><FormLabel>Account ID</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name="displayName" render={({ field }) => (
                  <FormItem><FormLabel>Display Name</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <Button type="submit" disabled={mutation.isPending}>Create</Button>
              </form>
            </Form>
          </DialogContent>
        </Dialog>
      </div>
      <div className="overflow-x-auto rounded-md border">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">agent_id</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">account_id</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">display_name</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">active</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">created_at</th>
              <th className="text-right py-2 px-3 font-medium text-muted-foreground">actions</th>
            </tr>
          </thead>
          <tbody>
            {agents.map((row, i) => (
              <tr key={`${row.agent_id}-${i}`} className="border-b last:border-0">
                <td className="py-2 px-3 font-mono">{row.agent_id}</td>
                <td className="py-2 px-3 font-mono">{row.account_id}</td>
                <td className="py-2 px-3 font-mono max-w-xs truncate">{row.display_name}</td>
                <td className="py-2 px-3 font-mono">{String(row.active)}</td>
                <td className="py-2 px-3 font-mono max-w-xs truncate">{row.created_at}</td>
                <td className="py-2 px-3 text-right space-x-2">
                  <Button size="sm" variant="outline" onClick={() => handleEdit(row)}>Edit</Button>
                  <Button size="sm" variant="outline" onClick={() => handleMove(row)}>Move</Button>
                  <Button size="sm" variant="secondary" onClick={() => handleToggle(row)}>
                    {row.active ? 'Deactivate' : 'Activate'}
                  </Button>
                  <Button size="sm" variant="destructive" onClick={() => handleDelete(row.agent_id)}>
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
            {agents.length === 0 && (
              <tr>
                <td colSpan={6} className="py-4 text-center text-muted-foreground">No records yet</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {updateMutation.isError && <p className="text-sm text-red-600">{(updateMutation.error as Error).message}</p>}
      {deleteMutation.isError && <p className="text-sm text-red-600">{(deleteMutation.error as Error).message}</p>}
    </div>
  )
}

// ── API Keys ──────────────────────────────────────────────────────────────────

const ApiKeySchema = z.object({
  accountId:  z.string().min(1, 'Required'),
  generation: z.number().int().positive().default(1),
})

interface GeneratedKey {
  rawKey: string
  sha256: string
  headerValue: string
}

function ApiKeysTab() {
  const qc = useQueryClient()
  const [accountId, setAccountId] = useState('')
  const [generatedKey, setGeneratedKey] = useState<GeneratedKey | null>(null)
  const { data, refetch } = useQuery<{ apikeys: ApiKeyRow[] }>({
    queryKey: ['apikeys', accountId],
    queryFn: () =>
      accountId
        ? fetch(`/api/onboard/apikeys?accountId=${encodeURIComponent(accountId)}`).then(r => r.json())
        : Promise.resolve({ apikeys: [] }),
    enabled: !!accountId,
  })
  const generateMutation = useMutation({
    mutationFn: (body: z.infer<typeof ApiKeySchema>) =>
      fetch('/api/onboard/apikeys/generate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(r => r.json()),
    onSuccess: (d: GeneratedKey) => { setGeneratedKey(d); qc.invalidateQueries({ queryKey: ['apikeys'] }) },
  })
  const updateMutation = useMutation({
    mutationFn: async ({ keyHash, active }: { keyHash: string; active: boolean }) => {
      const res = await fetch(`/api/onboard/apikeys/${encodeURIComponent(keyHash)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active }),
      })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['apikeys', accountId] }) },
  })
  const revokeMutation = useMutation({
    mutationFn: async (keyHash: string) => {
      const res = await fetch('/api/onboard/apikeys/revoke', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ keyHash }),
      })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['apikeys', accountId] }) },
  })
  const deleteMutation = useMutation({
    mutationFn: async (keyHash: string) => {
      const res = await fetch(`/api/onboard/apikeys/${encodeURIComponent(keyHash)}`, { method: 'DELETE' })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['apikeys', accountId] }) },
  })

  const keys = data?.apikeys ?? []

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end gap-3">
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-foreground">Account ID</label>
          <Input
            placeholder="account-id"
            value={accountId}
            onChange={e => setAccountId(e.target.value)}
            className="w-64"
          />
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()}>Load</Button>
        <Button
          size="sm"
          disabled={!accountId || generateMutation.isPending}
          onClick={() => generateMutation.mutate({ accountId, generation: 1 })}
        >
          Generate Key
        </Button>
      </div>

      {generatedKey && (
        <div className="rounded-md border border-yellow-400 bg-yellow-50 p-4">
          <p className="text-sm font-semibold text-yellow-800 mb-2">⚠ Save this key — shown only once</p>
          <div className="font-mono text-xs flex flex-col gap-1">
            <p><span className="text-yellow-600">RAW KEY : </span>{generatedKey.rawKey}</p>
            <p><span className="text-yellow-600">SHA-256 : </span>{generatedKey.sha256}</p>
            <p><span className="text-yellow-600">HEADER  : </span>{generatedKey.headerValue}</p>
          </div>
          <Button size="sm" variant="outline" className="mt-3" onClick={() => setGeneratedKey(null)}>
            Dismiss
          </Button>
        </div>
      )}

      <div className="overflow-x-auto rounded-md border">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">key_hash</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">generation</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">active</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">expires_at</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">created_at</th>
              <th className="text-right py-2 px-3 font-medium text-muted-foreground">actions</th>
            </tr>
          </thead>
          <tbody>
            {keys.map((row, i) => (
              <tr key={`${row.key_hash}-${i}`} className="border-b last:border-0">
                <td className="py-2 px-3 font-mono" title={row.key_hash}>{shortHash(row.key_hash)}</td>
                <td className="py-2 px-3 font-mono">{row.generation}</td>
                <td className="py-2 px-3 font-mono">{String(row.active)}</td>
                <td className="py-2 px-3 font-mono">{String(row.expires_at ?? '')}</td>
                <td className="py-2 px-3 font-mono">{row.created_at}</td>
                <td className="py-2 px-3 text-right space-x-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => updateMutation.mutate({ keyHash: row.key_hash, active: !row.active })}
                  >
                    {row.active ? 'Deactivate' : 'Activate'}
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => revokeMutation.mutate(row.key_hash)}>
                    Revoke
                  </Button>
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => {
                      if (!window.confirm(`Delete API key ${shortHash(row.key_hash)}?`)) return
                      deleteMutation.mutate(row.key_hash)
                    }}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
            {keys.length === 0 && (
              <tr>
                <td colSpan={6} className="py-4 text-center text-muted-foreground">No records yet</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {updateMutation.isError && <p className="text-sm text-red-600">{(updateMutation.error as Error).message}</p>}
      {revokeMutation.isError && <p className="text-sm text-red-600">{(revokeMutation.error as Error).message}</p>}
      {deleteMutation.isError && <p className="text-sm text-red-600">{(deleteMutation.error as Error).message}</p>}
    </div>
  )
}

// ── Brokers ───────────────────────────────────────────────────────────────────

const BrokerSchema = z.object({
  agentId:           z.string().min(1, 'Required'),
  externalAccountId: z.string().min(1, 'Required'),
})

function BrokersTab() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const { data } = useQuery<{ brokers: BrokerRow[] }>({
    queryKey: ['brokers'],
    queryFn: () => fetch('/api/onboard/brokers').then(r => r.json()),
    refetchInterval: 5000,
  })
  const mutation = useMutation({
    mutationFn: (body: z.infer<typeof BrokerSchema>) =>
      fetch('/api/onboard/brokers', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['brokers'] }); setOpen(false) },
  })
  const updateMutation = useMutation({
    mutationFn: async ({ brokerAccountId, body }: { brokerAccountId: string; body: { externalAccountId?: string; active?: boolean } }) => {
      const res = await fetch(`/api/onboard/brokers/${encodeURIComponent(brokerAccountId)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['brokers'] }) },
  })
  const deleteMutation = useMutation({
    mutationFn: async (brokerAccountId: string) => {
      const res = await fetch(`/api/onboard/brokers/${encodeURIComponent(brokerAccountId)}`, { method: 'DELETE' })
      await assertOk(res)
      return res.json()
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['brokers'] }) },
  })
  const form = useForm({ resolver: zodResolver(BrokerSchema) })

  const brokers = data?.brokers ?? []

  function handleEdit(row: BrokerRow) {
    const externalAccountId = window.prompt(
      `Update external account for ${row.broker_account_id}`,
      row.external_account_id,
    )
    if (!externalAccountId || externalAccountId.trim().length === 0) return
    updateMutation.mutate({
      brokerAccountId: row.broker_account_id,
      body: { externalAccountId: externalAccountId.trim() },
    })
  }

  function handleToggle(row: BrokerRow) {
    updateMutation.mutate({
      brokerAccountId: row.broker_account_id,
      body: { active: !row.active },
    })
  }

  function handleDelete(brokerAccountId: string) {
    if (!window.confirm(`Delete mapping ${brokerAccountId}?`)) return
    deleteMutation.mutate(brokerAccountId)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild><Button size="sm">+ Create Broker Account</Button></DialogTrigger>
          <DialogContent>
            <DialogHeader><DialogTitle>Create Broker Account</DialogTitle></DialogHeader>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(v => mutation.mutate(v))} className="flex flex-col gap-4">
                <FormField control={form.control} name="agentId" render={({ field }) => (
                  <FormItem><FormLabel>Agent ID</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <FormField control={form.control} name="externalAccountId" render={({ field }) => (
                  <FormItem><FormLabel>External Account ID</FormLabel><FormControl><Input {...field} /></FormControl><FormMessage /></FormItem>
                )} />
                <Button type="submit" disabled={mutation.isPending}>Create</Button>
              </form>
            </Form>
          </DialogContent>
        </Dialog>
      </div>
      <div className="overflow-x-auto rounded-md border">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">broker_account_id</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">agent_id</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">broker_id</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">external_account_id</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">active</th>
              <th className="text-left py-2 px-3 font-medium text-muted-foreground">created_at</th>
              <th className="text-right py-2 px-3 font-medium text-muted-foreground">actions</th>
            </tr>
          </thead>
          <tbody>
            {brokers.map((row, i) => (
              <tr key={`${row.broker_account_id}-${i}`} className="border-b last:border-0">
                <td className="py-2 px-3 font-mono">{row.broker_account_id}</td>
                <td className="py-2 px-3 font-mono">{row.agent_id}</td>
                <td className="py-2 px-3 font-mono">{row.broker_id}</td>
                <td className="py-2 px-3 font-mono">{row.external_account_id}</td>
                <td className="py-2 px-3 font-mono">{String(row.active)}</td>
                <td className="py-2 px-3 font-mono">{row.created_at}</td>
                <td className="py-2 px-3 text-right space-x-2">
                  <Button size="sm" variant="outline" onClick={() => handleEdit(row)}>Edit</Button>
                  <Button size="sm" variant="secondary" onClick={() => handleToggle(row)}>
                    {row.active ? 'Deactivate' : 'Activate'}
                  </Button>
                  <Button size="sm" variant="destructive" onClick={() => handleDelete(row.broker_account_id)}>
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
            {brokers.length === 0 && (
              <tr>
                <td colSpan={7} className="py-4 text-center text-muted-foreground">No records yet</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {updateMutation.isError && <p className="text-sm text-red-600">{(updateMutation.error as Error).message}</p>}
      {deleteMutation.isError && <p className="text-sm text-red-600">{(deleteMutation.error as Error).message}</p>}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function OnboardPage() {
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Onboarding</h1>
      <Tabs defaultValue="accounts">
        <TabsList>
          <TabsTrigger value="accounts">Accounts</TabsTrigger>
          <TabsTrigger value="agents">Agents</TabsTrigger>
          <TabsTrigger value="apikeys">API Keys</TabsTrigger>
          <TabsTrigger value="brokers">Mappings</TabsTrigger>
        </TabsList>
        <TabsContent value="accounts">
          <Card><CardContent className="pt-4"><AccountsTab /></CardContent></Card>
        </TabsContent>
        <TabsContent value="agents">
          <Card><CardContent className="pt-4"><AgentsTab /></CardContent></Card>
        </TabsContent>
        <TabsContent value="apikeys">
          <Card><CardContent className="pt-4"><ApiKeysTab /></CardContent></Card>
        </TabsContent>
        <TabsContent value="brokers">
          <Card><CardContent className="pt-4"><BrokersTab /></CardContent></Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
