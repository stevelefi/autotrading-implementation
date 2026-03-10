import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Form, FormField, FormItem, FormLabel, FormControl } from '@/components/ui/form'
import { ScrollArea } from '@/components/ui/scroll-area'

const SERVICES = [
  'all', 'ingress-gateway-service', 'risk-service', 'order-service',
  'ibkr-connector-service', 'event-processor-service',
  'agent-runtime-service', 'performance-service', 'monitoring-api',
]
const LEVELS = ['all', 'DEBUG', 'INFO', 'WARN', 'ERROR']
const SINCE_OPTIONS = ['15m', '30m', '1h', '2h', '6h', '1d']

const SHORT_NAMES: Record<string, string> = {
  'ingress-gateway-service': 'ingress',
  'risk-service':            'risk',
  'order-service':           'order',
  'ibkr-connector-service':  'ibkr',
  'event-processor-service': 'events',
  'agent-runtime-service':   'agent',
  'performance-service':     'perf',
  'monitoring-api':          'monitor',
}

const SERVICE_COLORS: Record<string, string> = {
  'risk-service':            'bg-blue-100 text-blue-900',
  'order-service':           'bg-purple-100 text-purple-900',
  'ibkr-connector-service':  'bg-green-100 text-green-900',
  'ingress-gateway-service': 'bg-yellow-100 text-yellow-900',
  'event-processor-service': 'bg-orange-100 text-orange-900',
  'agent-runtime-service':   'bg-pink-100 text-pink-900',
  'performance-service':     'bg-teal-100 text-teal-900',
  'monitoring-api':          'bg-gray-100 text-gray-900',
}

const TraceSchema = z.object({
  traceId:       z.string().optional(),
  clientEventId: z.string().optional(),
  agentId:       z.string().optional(),
  orderIntentId: z.string().optional(),
  signalId:      z.string().optional(),
  service:       z.string().optional(),
  level:         z.string().optional(),
  since:         z.string().default('1h'),
})

interface TraceEntry {
  ts:      string
  service: string
  line:    string
}

export default function TracePage() {
  const form = useForm({ resolver: zodResolver(TraceSchema), defaultValues: { since: '1h' } })
  const [entries, setEntries] = useState<TraceEntry[]>([])

  const query = useMutation({
    mutationFn: (body: z.infer<typeof TraceSchema>) =>
      fetch('/api/trace/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...body,
          service: body.service === 'all' ? undefined : body.service,
          level:   body.level   === 'all' ? undefined : body.level,
        }),
      }).then(r => r.json()),
    onSuccess: (data: { entries: TraceEntry[] }) => setEntries(data.entries ?? []),
  })

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Trace Viewer</h1>

      <Card>
        <CardHeader><CardTitle>Query</CardTitle></CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(v => query.mutate(v))} className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
                <FormField control={form.control} name="traceId" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Trace ID</FormLabel>
                    <FormControl><Input placeholder="trace-id" {...field} /></FormControl>
                  </FormItem>
                )} />
                <FormField control={form.control} name="clientEventId" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Client Event ID</FormLabel>
                    <FormControl><Input placeholder="client-event-id" {...field} /></FormControl>
                  </FormItem>
                )} />
                <FormField control={form.control} name="agentId" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Agent ID</FormLabel>
                    <FormControl><Input placeholder="agent-id" {...field} /></FormControl>
                  </FormItem>
                )} />
                <FormField control={form.control} name="orderIntentId" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Order Intent ID</FormLabel>
                    <FormControl><Input placeholder="order-intent-id" {...field} /></FormControl>
                  </FormItem>
                )} />
                <FormField control={form.control} name="signalId" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Signal ID</FormLabel>
                    <FormControl><Input placeholder="signal-id" {...field} /></FormControl>
                  </FormItem>
                )} />
                <FormField control={form.control} name="service" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Service</FormLabel>
                    <Select onValueChange={field.onChange} defaultValue="all">
                      <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                      <SelectContent>
                        {SERVICES.map(s => <SelectItem key={s} value={s}>{s}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )} />
                <FormField control={form.control} name="level" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Level</FormLabel>
                    <Select onValueChange={field.onChange} defaultValue="all">
                      <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                      <SelectContent>
                        {LEVELS.map(l => <SelectItem key={l} value={l}>{l}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )} />
                <FormField control={form.control} name="since" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Since</FormLabel>
                    <Select onValueChange={field.onChange} defaultValue="1h">
                      <FormControl><SelectTrigger><SelectValue /></SelectTrigger></FormControl>
                      <SelectContent>
                        {SINCE_OPTIONS.map(s => <SelectItem key={s} value={s}>{s}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )} />
              </div>
              <Button type="submit" className="w-fit" disabled={query.isPending}>
                {query.isPending ? 'Querying Loki…' : 'Query Logs'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>
            Results{' '}
            <span className="text-muted-foreground font-normal text-sm">
              ({entries.length} entries)
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ScrollArea className="h-[500px] rounded-md border bg-zinc-950 p-3 font-mono text-xs text-green-400">
            {entries.length === 0 && (
              <p className="text-zinc-500">No results. Run a query above. (Stack must be running for Loki access.)</p>
            )}
            {entries.map((e, i) => (
              <div key={i} className="flex gap-3 py-0.5 hover:bg-white/5 rounded">
                <span className="text-zinc-500 shrink-0 w-28">{e.ts.slice(11, 23)}</span>
                <span className={`shrink-0 w-16 rounded px-1 text-center text-[10px] leading-4 self-start mt-0.5 ${SERVICE_COLORS[e.service] ?? 'bg-zinc-100 text-zinc-900'}`}>
                  {SHORT_NAMES[e.service] ?? e.service}
                </span>
                <span className="whitespace-pre-wrap break-all">{e.line}</span>
              </div>
            ))}
          </ScrollArea>
        </CardContent>
      </Card>
    </div>
  )
}
