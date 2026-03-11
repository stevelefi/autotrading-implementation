import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

interface ServiceHealth {
  service: string
  port:    number
  status:  string
}

interface ServicesResponse {
  services: ServiceHealth[]
}

interface Container {
  Name?:   string
  name?:   string
  Status?: string
  status?: string
  State?:  string
  state?:  string
}

interface ContainersResponse {
  containers: Container[]
}

interface BrokerStatusResponse {
  brokerId: string
  healthTableStatus: string
  healthTableUpdatedAt?: string
  healthTableDetailJson?: string
  connectorStatus: string
  connectorStats?: Record<string, unknown>
}

interface ActivityItem {
  received_at: string
  ingress_event_id: string
  idempotency_key: string
  agent_id?: string
  ingestion_status: string
  order_intent_id?: string
  trade_state?: string
  last_status_at?: string
}

interface ActivityResponse {
  items: ActivityItem[]
  limit: number
}

// Map raw Docker service names → short display names
const SHORT_SERVICE: Record<string, string> = {
  'ingress-gateway-service': 'Ingress',
  'risk-service':            'Risk',
  'order-service':           'Order',
  'ibkr-connector-service':  'IBKR',
  'agent-runtime-service':   'Agent Runtime',
  'event-processor-service': 'Event Processor',
  'monitoring-api':          'Monitoring',
  'performance-service':     'Performance',
  'postgres':                'Postgres',
  'redpanda':                'Redpanda',
  'redpanda-console':        'Redpanda Console',
  'prometheus':              'Prometheus',
  'grafana':                 'Grafana',
  'loki':                    'Loki',
  'promtail':                'Promtail',
  'otel-collector':          'OTEL Collector',
  'tempo':                   'Tempo',
  'flyway-init':             'Flyway Init',
  'redpanda-init':           'Redpanda Init',
  'ibkr-simulator':          'IBKR Simulator',
}

const APP_SERVICE_NAMES = new Set([
  'ingress-gateway-service', 'risk-service', 'order-service',
  'ibkr-connector-service', 'agent-runtime-service', 'event-processor-service',
  'monitoring-api', 'performance-service',
])

function cleanContainerName(raw: string): string {
  const normalized = raw.toLowerCase().replace(/^\//, '')
  const known = Object.keys(SHORT_SERVICE).sort((a, b) => b.length - a.length)
  for (const name of known) {
    if (normalized.includes(name)) return name
  }
  return normalized.replace(/-\d+$/, '')
}

function displayName(raw: string): string {
  const cleaned = cleanContainerName(raw)
  return SHORT_SERVICE[cleaned] ?? cleaned
}

function statusVariant(s: string): 'default' | 'destructive' | 'secondary' | 'outline' {
  const lower = s.toLowerCase()
  if (lower.includes('running') || lower.includes('up') || lower === 'healthy') return 'default'
  if (lower.includes('exit') || lower.includes('dead') || lower.includes('error')) return 'destructive'
  return 'secondary'
}

export default function HealthPage() {
  const { data: servicesResp, refetch: refetchServices, isFetching: fetchingServices } =
    useQuery<ServicesResponse>({
      queryKey: ['health'],
      queryFn: () => fetch('/api/health/services').then(r => r.json()),
      refetchInterval: 30_000,
    })

  const { data: containersResp, refetch: refetchContainers, isFetching: fetchingContainers } =
    useQuery<ContainersResponse>({
      queryKey: ['containers'],
      queryFn: () => fetch('/api/health/containers').then(r => r.json()),
      refetchInterval: 30_000,
    })

  const { data: brokerResp, refetch: refetchBroker, isFetching: fetchingBroker } =
    useQuery<BrokerStatusResponse>({
      queryKey: ['broker-health'],
      queryFn: () => fetch('/api/health/broker').then(r => r.json()),
      refetchInterval: 30_000,
    })

  const { data: activityResp, refetch: refetchActivity, isFetching: fetchingActivity } =
    useQuery<ActivityResponse>({
      queryKey: ['trade-activity'],
      queryFn: () => fetch('/api/health/activity?limit=50').then(r => r.json()),
      refetchInterval: 15_000,
    })

  function refetch() {
    void refetchServices()
    void refetchContainers()
    void refetchBroker()
    void refetchActivity()
  }
  const isFetching = fetchingServices || fetchingContainers || fetchingBroker || fetchingActivity

  const containers = containersResp?.containers ?? []
  const services = servicesResp?.services ?? []
  const activity = activityResp?.items ?? []

  const appContainers = containers.filter(c => {
    const name = cleanContainerName(c.Name ?? c.name ?? '')
    return APP_SERVICE_NAMES.has(name)
  })
  const infraContainers = containers.filter(c => {
    const name = cleanContainerName(c.Name ?? c.name ?? '')
    return !APP_SERVICE_NAMES.has(name)
  })

  function renderContainerGrid(list: Container[]) {
    if (!list.length) return <p className="text-sm text-muted-foreground">No containers found</p>
    return (
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
        {list.map((c, i) => {
          const raw = c.Name ?? c.name ?? `container-${i}`
          const st  = c.Status ?? c.status ?? c.State ?? c.state ?? 'unknown'
          return (
            <div key={raw} className="flex flex-col gap-1 rounded-md border p-2">
              <span className="text-xs font-semibold truncate">{displayName(raw)}</span>
              <Badge variant={statusVariant(st)} className="text-xs w-fit">{st}</Badge>
            </div>
          )
        })}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">System Health</h1>
          <p className="text-muted-foreground text-sm mt-1">Live container and service status</p>
        </div>
        <Button variant="outline" size="sm" onClick={refetch} disabled={isFetching}>
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      {/* Actuator health */}
      {services && services.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Actuator Health Endpoints</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
              {services.map(s => (
                <div key={s.service} className="flex flex-col gap-1 rounded-md border p-2">
                  <span className="text-xs font-semibold">{SHORT_SERVICE[s.service] ?? s.service}</span>
                  <Badge variant={statusVariant(s.status)} className="text-xs w-fit">{s.status}</Badge>
                  <span className="text-xs text-muted-foreground">:{s.port}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* App containers */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Application Services</CardTitle>
        </CardHeader>
        <CardContent>{renderContainerGrid(appContainers)}</CardContent>
      </Card>

      {/* Infra containers */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Infrastructure</CardTitle>
        </CardHeader>
        <CardContent>{renderContainerGrid(infraContainers)}</CardContent>
      </Card>

      {/* Broker health */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Broker Health Status</CardTitle>
        </CardHeader>
        <CardContent>
          {!brokerResp ? (
            <p className="text-sm text-muted-foreground">No broker health data yet.</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-2">
              <div className="rounded-md border p-3">
                <p className="text-xs text-muted-foreground">Broker</p>
                <p className="font-mono text-sm">{brokerResp.brokerId}</p>
              </div>
              <div className="rounded-md border p-3">
                <p className="text-xs text-muted-foreground">Health Table</p>
                <Badge variant={statusVariant(brokerResp.healthTableStatus)}>{brokerResp.healthTableStatus}</Badge>
              </div>
              <div className="rounded-md border p-3">
                <p className="text-xs text-muted-foreground">Connector</p>
                <Badge variant={statusVariant(brokerResp.connectorStatus)}>{brokerResp.connectorStatus}</Badge>
              </div>
              <div className="rounded-md border p-3">
                <p className="text-xs text-muted-foreground">Updated At</p>
                <p className="font-mono text-xs truncate">{brokerResp.healthTableUpdatedAt ?? 'n/a'}</p>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Ingress / trade status */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Ingress → Trade Status (Recent)</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto rounded-md border">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-2 px-3 font-medium text-muted-foreground">received_at</th>
                  <th className="text-left py-2 px-3 font-medium text-muted-foreground">ingress_event_id</th>
                  <th className="text-left py-2 px-3 font-medium text-muted-foreground">agent_id</th>
                  <th className="text-left py-2 px-3 font-medium text-muted-foreground">ingress_status</th>
                  <th className="text-left py-2 px-3 font-medium text-muted-foreground">order_intent_id</th>
                  <th className="text-left py-2 px-3 font-medium text-muted-foreground">trade_state</th>
                </tr>
              </thead>
              <tbody>
                {activity.map((row, idx) => (
                  <tr key={`${row.ingress_event_id}-${idx}`} className="border-b last:border-0">
                    <td className="py-2 px-3 font-mono">{row.received_at}</td>
                    <td className="py-2 px-3 font-mono" title={row.ingress_event_id}>{row.ingress_event_id}</td>
                    <td className="py-2 px-3 font-mono">{row.agent_id ?? ''}</td>
                    <td className="py-2 px-3"><Badge variant={statusVariant(row.ingestion_status)}>{row.ingestion_status}</Badge></td>
                    <td className="py-2 px-3 font-mono">{row.order_intent_id ?? ''}</td>
                    <td className="py-2 px-3">
                      {row.trade_state ? <Badge variant={statusVariant(row.trade_state)}>{row.trade_state}</Badge> : '-'}
                    </td>
                  </tr>
                ))}
                {activity.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-4 text-center text-muted-foreground">No ingress/trade activity yet</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

