import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { LogViewer } from '@/components/LogViewer'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'

// ── Command definitions ───────────────────────────────────────────────────────

const LAUNCH_COMMANDS = [
  { cmd: 'infra-up',    label: 'Infra Up',    desc: 'postgres, redpanda, observability' },
  { cmd: 'app-up',      label: 'App Up',      desc: '8 app services (infra must be up)' },
  { cmd: 'restart-app', label: 'Restart App', desc: 'stop → build → start app only' },
  { cmd: 'up',          label: 'Full Up',     desc: 'infra + all 8 services' },
]

const UTIL_COMMANDS = [
  { cmd: 'build',       label: 'Build',       desc: 'rebuild Docker images' },
  { cmd: 'logs',        label: 'Logs',        desc: 'tail all service logs' },
  { cmd: 'status',      label: 'Status',      desc: 'show running containers' },
]

const DESTRUCTIVE_COMMANDS = [
  { cmd: 'down',        label: 'Full Down',   desc: 'stop everything + volumes' },
  { cmd: 'ci',          label: 'CI',          desc: 'down → build → up → validate → teardown' },
]

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Strip Docker Compose project prefix (e.g. "autotrading-local-") and trailing replica index */
function cleanName(raw: string): string {
  // Strip known compose prefix variants
  let name = raw.replace(/^[a-z0-9_-]+-local-/, '')
  // Strip trailing -1, -2, etc.
  name = name.replace(/-\d+$/, '')
  return name || raw
}

function stateVariant(state: string): 'default' | 'destructive' | 'secondary' | 'outline' {
  switch (state) {
    case 'running': return 'default'
    case 'exited':  return 'destructive'
    case 'paused':  return 'secondary'
    default:        return 'outline'
  }
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function StackPage() {
  const [streamKey,  setStreamKey]  = useState<string | null>(null)
  const [activeCmd,  setActiveCmd]  = useState<string>('idle')
  const [running,    setRunning]    = useState(false)
  const [confirmCmd, setConfirmCmd] = useState<typeof DESTRUCTIVE_COMMANDS[number] | null>(null)

  const { data: statusData, refetch } = useQuery({
    queryKey: ['stack-status'],
    queryFn: () => fetch('/api/stack/status').then(r => r.json()),
    refetchInterval: 5000,
  })

  function run(cmd: string) {
    setActiveCmd(cmd)
    setRunning(true)
    setStreamKey(`/api/stack/${cmd}?t=${Date.now()}`)
  }

  function handleConfirm() {
    if (confirmCmd) { run(confirmCmd.cmd); setConfirmCmd(null) }
  }

  const containers: Record<string, string>[] = statusData?.containers ?? []
  const runningCount = containers.filter(c => (c.State ?? c.state ?? '').toLowerCase() === 'running').length

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Stack Control</h1>
          <p className="text-sm text-muted-foreground mt-0.5">Manage the local Docker Compose stack</p>
        </div>
        {containers.length > 0 && (
          <Badge variant={runningCount > 0 ? 'default' : 'outline'} className="text-sm px-3 py-1">
            {runningCount}/{containers.length} running
          </Badge>
        )}
      </div>

      {/* Commands */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {/* Launch group */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-muted-foreground uppercase tracking-wide">Launch</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {LAUNCH_COMMANDS.map(({ cmd, label, desc }) => (
              <button
                key={cmd}
                disabled={running}
                onClick={() => run(cmd)}
                className={[
                  'flex items-start gap-3 rounded-md border px-3 py-2.5 text-left text-sm transition-colors',
                  'hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed',
                  activeCmd === cmd && running ? 'border-primary bg-primary/5' : 'bg-background',
                ].join(' ')}
              >
                <div className="mt-0.5 h-2 w-2 rounded-full shrink-0 bg-green-500" />
                <div>
                  <div className="font-medium leading-none">{label}</div>
                  <div className="mt-1 text-xs text-muted-foreground">{desc}</div>
                </div>
              </button>
            ))}
          </CardContent>
        </Card>

        {/* Utility group */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-muted-foreground uppercase tracking-wide">Utilities</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {UTIL_COMMANDS.map(({ cmd, label, desc }) => (
              <button
                key={cmd}
                disabled={running}
                onClick={() => run(cmd)}
                className={[
                  'flex items-start gap-3 rounded-md border px-3 py-2.5 text-left text-sm transition-colors',
                  'hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed',
                  activeCmd === cmd && running ? 'border-primary bg-primary/5' : 'bg-background',
                ].join(' ')}
              >
                <div className="mt-0.5 h-2 w-2 rounded-full shrink-0 bg-blue-500" />
                <div>
                  <div className="font-medium leading-none">{label}</div>
                  <div className="mt-1 text-xs text-muted-foreground">{desc}</div>
                </div>
              </button>
            ))}
          </CardContent>
        </Card>

        {/* Destructive group */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-muted-foreground uppercase tracking-wide">Destructive</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {DESTRUCTIVE_COMMANDS.map(item => (
              <button
                key={item.cmd}
                disabled={running}
                onClick={() => setConfirmCmd(item)}
                className={[
                  'flex items-start gap-3 rounded-md border border-destructive/30 px-3 py-2.5 text-left text-sm transition-colors',
                  'hover:bg-destructive/10 disabled:opacity-50 disabled:cursor-not-allowed',
                  'bg-background',
                ].join(' ')}
              >
                <div className="mt-0.5 h-2 w-2 rounded-full shrink-0 bg-red-500" />
                <div>
                  <div className="font-medium leading-none text-destructive">{item.label}</div>
                  <div className="mt-1 text-xs text-muted-foreground">{item.desc}</div>
                </div>
              </button>
            ))}
          </CardContent>
        </Card>
      </div>

      {/* Current command indicator */}
      {running && (
        <div className="flex items-center gap-2 rounded-md border border-primary/30 bg-primary/5 px-4 py-2.5">
          <span className="inline-block h-2 w-2 rounded-full bg-green-500 animate-pulse" />
          <span className="text-sm font-medium">Running: <code className="font-mono">{activeCmd}</code></span>
        </div>
      )}

      {/* Container status */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Container Status</CardTitle>
            <Button variant="ghost" size="sm" onClick={() => refetch()}>↻ Refresh</Button>
          </div>
        </CardHeader>
        <CardContent>
          {containers.length === 0 ? (
            <p className="text-sm text-muted-foreground">No containers detected. Start the stack using the controls above.</p>
          ) : (
            <div className="grid grid-cols-2 gap-1.5 md:grid-cols-3 lg:grid-cols-4">
              {containers.map((c, i) => {
                const state  = (c.State ?? c.state ?? 'unknown').toLowerCase()
                const name   = cleanName(c.Name ?? c.name ?? `container-${i}`)
                const health = (c.Health ?? '').toLowerCase()
                return (
                  <div key={i} className="flex items-center justify-between rounded border px-2.5 py-1.5 bg-background">
                    <span className="font-mono text-xs truncate mr-2" title={c.Name ?? c.name}>{name}</span>
                    <div className="flex items-center gap-1 shrink-0">
                      {health === 'healthy' && <span className="text-green-500 text-[10px]">♥</span>}
                      {health === 'unhealthy' && <span className="text-red-500 text-[10px]">♥</span>}
                      <Badge variant={stateVariant(state)} className="text-[10px] px-1.5 py-0">
                        {state}
                      </Badge>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Log output */}
      <Card>
        <CardHeader>
          <CardTitle>
            Output
            {activeCmd !== 'idle' && (
              <code className="ml-2 text-sm font-normal text-muted-foreground">— {activeCmd}</code>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <LogViewer
            streamKey={streamKey}
            heightClass="h-[420px]"
            onDone={() => setRunning(false)}
          />
        </CardContent>
      </Card>

      {/* Confirm destructive dialog */}
      <Dialog open={!!confirmCmd} onOpenChange={open => !open && setConfirmCmd(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm: {confirmCmd?.label}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {confirmCmd?.cmd === 'down'
              ? 'This will stop all containers and remove all volumes. Database and Kafka state will be lost.'
              : 'This runs a full CI cycle: down → build → up → validate → teardown. It will wipe all local state.'}
          </p>
          <div className="flex justify-end gap-3 mt-4">
            <Button variant="outline" onClick={() => setConfirmCmd(null)}>Cancel</Button>
            <Button variant="destructive" onClick={handleConfirm}>Yes, run {confirmCmd?.label}</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}

