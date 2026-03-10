import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { LogViewer } from '@/components/LogViewer'

const CHECKS = [
  { key: 'branch-check',   label: 'Branch Name',   desc: 'Validates Git branch naming convention' },
  { key: 'spec-verify',    label: 'Spec Verify',   desc: 'Checks spec sync is current with SPEC_VERSION.json' },
  { key: 'unit',           label: 'Unit Tests',    desc: 'Maven unit tests — zero failures' },
  { key: 'coverage',       label: 'Coverage',      desc: 'JaCoCo gate ≥ 50% line coverage' },
  { key: 'e2e',            label: 'E2E Tests',     desc: 'All 5 e2e test classes' },
  { key: 'helm-lint',      label: 'Helm Lint',     desc: 'helm lint on trading-service chart' },
  { key: 'helm-template',  label: 'Helm Template', desc: 'helm template dry-run validation' },
]

interface CheckResult {
  check: string
  passed: boolean
}

export default function ChecksPage() {
  const [selected, setSelected] = useState<Set<string>>(new Set(CHECKS.map(c => c.key)))
  const [streamKey, setStreamKey] = useState<string | null>(null)
  const [results, setResults] = useState<CheckResult[]>([])
  const [running, setRunning] = useState(false)

  function toggle(key: string) {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  function selectAll() { setSelected(new Set(CHECKS.map(c => c.key))) }
  function selectNone() { setSelected(new Set()) }

  async function run() {
    const only = [...selected]
    if (!only.length) return
    setResults([])
    setRunning(true)
    const key = `checks-${Date.now()}`
    setStreamKey(key)
    try {
      const params = only.map(k => `only=${k}`).join('&')
      const res = await fetch(`/api/checks/run?${params}`, { method: 'POST' })
      if (res.ok) {
        const data: CheckResult[] = await res.json().catch(() => [])
        setResults(data)
      }
    } catch {
      // results shown via log stream
    } finally {
      setRunning(false)
    }
  }

  const passCount = results.filter(r => r.passed).length
  const failCount = results.filter(r => !r.passed).length

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Pre-Commit Checks</h1>
        <p className="text-muted-foreground text-sm mt-1">
          Run <code className="font-mono bg-muted px-1 rounded">scripts/check.py</code> — select which checks to include.
        </p>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">Select Checks</CardTitle>
            <div className="flex gap-2">
              <Button variant="ghost" size="sm" onClick={selectAll}>All</Button>
              <Button variant="ghost" size="sm" onClick={selectNone}>None</Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {CHECKS.map(c => (
              <div key={c.key} className="flex items-start gap-2">
                <Checkbox
                  id={c.key}
                  checked={selected.has(c.key)}
                  onCheckedChange={() => toggle(c.key)}
                  className="mt-0.5"
                />
                <div>
                  <Label htmlFor={c.key} className="font-medium cursor-pointer">{c.label}</Label>
                  <p className="text-xs text-muted-foreground">{c.desc}</p>
                </div>
              </div>
            ))}
          </div>

          <div className="mt-4 flex items-center gap-3">
            <Button onClick={run} disabled={running || selected.size === 0}>
              {running ? 'Running…' : `Run ${selected.size} Check${selected.size !== 1 ? 's' : ''}`}
            </Button>
            {results.length > 0 && (
              <div className="flex gap-2 items-center text-sm">
                {passCount > 0 && <Badge variant="default" className="bg-green-600">{passCount} passed</Badge>}
                {failCount > 0 && <Badge variant="destructive">{failCount} failed</Badge>}
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {results.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Results</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {results.map(r => (
                <div
                  key={r.check}
                  className={`flex items-center gap-2 rounded-md border px-3 py-2 text-sm font-medium ${
                    r.passed
                      ? 'border-green-600/30 bg-green-600/10 text-green-700 dark:text-green-400'
                      : 'border-red-600/30 bg-red-600/10 text-red-700 dark:text-red-400'
                  }`}
                >
                  <span>{r.passed ? '✓' : '✗'}</span>
                  <span>{CHECKS.find(c => c.key === r.check)?.label ?? r.check}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {streamKey && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-base">Output</CardTitle>
          </CardHeader>
          <CardContent>
            <LogViewer streamKey={streamKey} heightClass="h-[500px]" />
          </CardContent>
        </Card>
      )}
    </div>
  )
}
