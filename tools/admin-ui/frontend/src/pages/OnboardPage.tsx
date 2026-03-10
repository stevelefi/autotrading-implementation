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
import { Badge } from '@/components/ui/badge'

// ── Shared table ─────────────────────────────────────────────────────────────

function DataTable({ headers, rows }: { headers: string[]; rows: Record<string, unknown>[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs">
        <thead>
          <tr className="border-b">
            {headers.map(h => (
              <th key={h} className="text-left py-1.5 pr-4 font-medium text-muted-foreground">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className="border-b last:border-0">
              {headers.map(h => (
                <td key={h} className="py-1.5 pr-4 font-mono max-w-xs truncate">
                  {String(row[h] ?? '')}
                </td>
              ))}
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={headers.length} className="py-4 text-center text-muted-foreground">
                No records yet
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

// ── Accounts ─────────────────────────────────────────────────────────────────

const AccountSchema = z.object({
  accountId:   z.string().min(1, 'Required'),
  displayName: z.string().min(1, 'Required'),
})

function AccountsTab() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const { data } = useQuery({
    queryKey: ['accounts'],
    queryFn: () => fetch('/api/onboard/accounts').then(r => r.json()),
    refetchInterval: 5000,
  })
  const mutation = useMutation({
    mutationFn: (body: z.infer<typeof AccountSchema>) =>
      fetch('/api/onboard/accounts', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); setOpen(false) },
  })
  const form = useForm({ resolver: zodResolver(AccountSchema) })

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
      <DataTable headers={['account_id', 'display_name', 'active', 'created_at']} rows={data?.accounts ?? []} />
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
  const { data } = useQuery({
    queryKey: ['agents'],
    queryFn: () => fetch('/api/onboard/agents').then(r => r.json()),
    refetchInterval: 5000,
  })
  const mutation = useMutation({
    mutationFn: (body: z.infer<typeof AgentSchema>) =>
      fetch('/api/onboard/agents', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['agents'] }); setOpen(false) },
  })
  const form = useForm({ resolver: zodResolver(AgentSchema) })

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
      <DataTable headers={['agent_id', 'account_id', 'display_name', 'active', 'created_at']} rows={data?.agents ?? []} />
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
  const { data, refetch } = useQuery({
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

      <DataTable
        headers={['key_hash', 'generation', 'active', 'expires_at', 'created_at']}
        rows={data?.apikeys ?? []}
      />
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
  const { data } = useQuery({
    queryKey: ['brokers'],
    queryFn: () => fetch('/api/onboard/brokers').then(r => r.json()),
    refetchInterval: 5000,
  })
  const mutation = useMutation({
    mutationFn: (body: z.infer<typeof BrokerSchema>) =>
      fetch('/api/onboard/brokers', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['brokers'] }); setOpen(false) },
  })
  const form = useForm({ resolver: zodResolver(BrokerSchema) })

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
      <DataTable
        headers={['broker_account_id', 'agent_id', 'broker_id', 'external_account_id', 'active', 'created_at']}
        rows={data?.brokers ?? []}
      />
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
          <TabsTrigger value="brokers">Brokers</TabsTrigger>
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
