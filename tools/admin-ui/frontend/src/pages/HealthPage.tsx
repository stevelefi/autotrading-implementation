import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

interface ServiceHealth {
  service: string
  port:    number
  status:  string
}

interface Container {
  Name?:   string
  name?:   string
  Status?: string
  status?: string
  State?:  string
  state?:  string
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
  // Strip docker-compose project/replica suffixes like "autotrading-implementation-risk-service-1"
  return raw.replace(/^[a-z0-9-]+-1$|^[a-z0-9-]+-autotrading-[a-z0-9-]+-1$/, s => {
    const m = s.match(/^(?:[a-z0-9]+-)+?([a-z-]+-(?:service|api|connector|simulator|console|collector|init))(?:-\d+)?$/)
    return m ? m[1] : s
  }).replace(/^.*?((?:ingress-gateway|risk|order|ibkr-connector|agent-runtime|event-processor|monitoring-api|performance|postgres|redpanda(?:-console|-init)?|prometheus|grafana|loki|promtail|otel-collector|tempo|flyway-init|redpanda-init|ibkr-simulator).*)$/, '$1')
    .replace(/-\d+$/, '')
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
  const { data: services, refetch: refetchServices, isFetching: fetchingServices } =
    useQuery<ServiceHealth[]>({
      queryKey: ['health'],
      queryFn: () => fetch('/api/stack/health').then(r => r.json()),
      refetchInterval: 30_000,
    })

  const { data: containers, refetch: refetchContainers, isFetching: fetchingContainers } =
    useQuery<Container[]>({
      queryKey: ['containers'],
      queryFn: () => fetch('/api/stack/status').then(r => r.json()),
      refetchInterval: 30_000,
    })

  function refetch() { void refetchServices(); void refetchContainers() }
  const isFetching = fetchingServices || fetchingContainers

  const appContainers = (containers ?? []).filter(c => {
    const name = cleanContainerName(c.Name ?? c.name ?? '')
    return APP_SERVICE_NAMES.has(name)
  })
  const infraContainers = (containers ?? []).filter(c => {
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
    </div>
  )
}

