import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { LogViewer } from '@/components/LogViewer'

const COMMANDS = [
  { cmd: 'unit',     label: 'Unit',     desc: 'Maven unit tests (fast, no Docker)' },
  { cmd: 'coverage', label: 'Coverage', desc: 'JaCoCo gate — minimum 50% line coverage' },
  { cmd: 'e2e',      label: 'E2E',      desc: 'All 5 e2e test classes (in-process)' },
  { cmd: 'smoke',    label: 'Smoke',    desc: '6-phase smoke suite (requires stack running)' },
  { cmd: 'load',     label: 'Load',     desc: '20 concurrent order submissions' },
  { cmd: 'all',      label: 'All',      desc: 'unit + coverage + e2e sequentially' },
]

export default function TestsPage() {
  const [module,     setModule]     = useState('')
  const [streamKey,  setStreamKey]  = useState<string | null>(null)
  const [activeCmd,  setActiveCmd]  = useState<string>('idle')
  const [body,       setBody]       = useState<Record<string, unknown>>({})
  const [running,    setRunning]    = useState(false)
  const [lastResult, setLastResult] = useState<Record<string, number>>({})

  function run(cmd: string) {
    setActiveCmd(cmd)
    setRunning(true)
    setBody({ module: module.trim() || undefined })
    setStreamKey(`/api/tests/${cmd}?t=${Date.now()}`)
  }

  function handleDone(exitCode: number) {
    setRunning(false)
    setLastResult(prev => ({ ...prev, [activeCmd]: exitCode }))
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Test Runner</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Run scripts/test.py suites with live streaming output</p>
      </div>

      {/* Options */}
      <Card>
        <CardHeader><CardTitle>Options</CardTitle></CardHeader>
        <CardContent>
          <div className="flex flex-col gap-1 max-w-sm">
            <Label htmlFor="module">Module path <span className="text-muted-foreground">(optional — for unit/coverage)</span></Label>
            <Input
              id="module"
              placeholder="e.g. services/risk-service"
              value={module}
              onChange={e => setModule(e.target.value)}
              disabled={running}
            />
            <p className="text-xs text-muted-foreground">Leave blank to run against all modules</p>
          </div>
        </CardContent>
      </Card>

      {/* Commands grid */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Test Suites</CardTitle>
            {running && (
              <Badge variant="secondary" className="gap-1">
                <span className="inline-block w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
                Running {activeCmd}…
              </Badge>
            )}
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
            {COMMANDS.map(({ cmd, label, desc }) => {
              const result = lastResult[cmd]
              const hasResult = result !== undefined
              return (
                <button
                  key={cmd}
                  disabled={running}
                  onClick={() => run(cmd)}
                  className={[
                    'flex flex-col gap-1.5 rounded-lg border px-4 py-3 text-left text-sm transition-colors',
                    'hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed',
                    activeCmd === cmd && running
                      ? 'border-primary bg-primary/5'
                      : 'bg-background',
                  ].join(' ')}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold">{label}</span>
                    {hasResult && (
                      <span className={result === 0 ? 'text-green-600 text-xs' : 'text-red-600 text-xs'}>
                        {result === 0 ? '✓' : '✗'}
                      </span>
                    )}
                  </div>
                  <span className="text-xs text-muted-foreground leading-snug">{desc}</span>
                </button>
              )
            })}
          </div>
        </CardContent>
      </Card>

      {/* Output */}
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
          <LogViewer streamKey={streamKey} body={body} onDone={handleDone} heightClass="h-[420px]" />
        </CardContent>
      </Card>
    </div>
  )
}

