import { useCallback, useEffect, useRef, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

// Strip ANSI escape codes from terminal output
const ANSI_RE = /\x1B\[[0-9;]*[mGKHF]|\x1B\][^\x07]*\x07/g
function stripAnsi(s: string) { return s.replace(ANSI_RE, '') }

interface LogViewerProps {
  /** The URL to POST to for the SSE stream. Changing this value starts a new stream. */
  streamKey: string | null
  body?: Record<string, unknown>
  onDone?: (exitCode: number, summary?: { check: string; passed: boolean }[]) => void
  /** Height tailwind class — defaults to h-96 */
  heightClass?: string
}

const MAX_LINES = 5_000

export function LogViewer({ streamKey, body, onDone, heightClass = 'h-96' }: LogViewerProps) {
  const [lines, setLines]       = useState<string[]>([])
  const [exitCode, setExitCode] = useState<number | null>(null)
  const [running, setRunning]   = useState(false)
  const [atBottom, setAtBottom] = useState(true)

  const scrollRef = useRef<HTMLDivElement>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const abortRef  = useRef<AbortController | null>(null)

  // Auto-scroll only when the user hasn't scrolled away from the bottom
  useEffect(() => {
    if (atBottom) bottomRef.current?.scrollIntoView({ behavior: 'auto' })
  }, [lines, atBottom])

  const handleScroll = useCallback(() => {
    const el = scrollRef.current
    if (!el) return
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    setAtBottom(distFromBottom < 40)
  }, [])

  function jumpToBottom() {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
    setAtBottom(true)
  }

  function stop() {
    abortRef.current?.abort()
    setRunning(false)
  }

  function clear() {
    setLines([])
    setExitCode(null)
  }

  useEffect(() => {
    if (!streamKey) return

    abortRef.current?.abort()
    setLines([])
    setExitCode(null)
    setRunning(true)
    setAtBottom(true)

    const ctrl = new AbortController()
    abortRef.current = ctrl

    ;(async () => {
      try {
        const res = await fetch(streamKey, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body ?? {}),
          signal: ctrl.signal,
        })

        if (!res.ok) {
          setLines([`[Error] HTTP ${res.status}: ${res.statusText}`])
          setRunning(false)
          return
        }

        const reader = res.body?.getReader()
        if (!reader) { setRunning(false); return }

        const dec = new TextDecoder()
        let buf = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buf += dec.decode(value, { stream: true })
          const parts = buf.split('\n\n')
          buf = parts.pop() ?? ''
          for (const part of parts) {
            const dataLine = part.split('\n').find(l => l.startsWith('data: '))
            if (!dataLine) continue
            try {
              const payload = JSON.parse(dataLine.slice(6)) as {
                done?: boolean
                exitCode?: number
                line?: string
                summary?: { check: string; passed: boolean }[]
              }
              if (payload.done) {
                const code = payload.exitCode ?? -1
                setExitCode(code)
                setRunning(false)
                onDone?.(code, payload.summary)
              } else if (typeof payload.line === 'string') {
                const cleaned = stripAnsi(payload.line)
                setLines(prev => {
                  const next = [...prev, cleaned]
                  return next.length > MAX_LINES ? next.slice(-MAX_LINES) : next
                })
              }
            } catch {
              // ignore malformed SSE events
            }
          }
        }
      } catch (e) {
        if ((e as Error)?.name !== 'AbortError') {
          setLines(prev => [...prev, `[Connection error] ${(e as Error).message}`])
          setRunning(false)
        }
      }
    })()

    return () => ctrl.abort()
  }, [streamKey])

  return (
    <div className="flex flex-col gap-2">
      {/* Toolbar */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          {running && (
            <Badge variant="secondary" className="gap-1">
              <span className="inline-block w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
              Running…
            </Badge>
          )}
          {!running && exitCode === null && <Badge variant="outline">Idle</Badge>}
          {exitCode !== null && !running && (
            <Badge variant={exitCode === 0 ? 'default' : 'destructive'}>
              {exitCode === 0 ? '✓ Exit 0' : `✗ Exit ${exitCode}`}
            </Badge>
          )}
          {lines.length > 0 && (
            <span className="text-xs text-muted-foreground tabular-nums">
              {lines.length.toLocaleString()} lines
            </span>
          )}
        </div>
        <div className="flex items-center gap-1.5">
          {running && (
            <Button size="sm" variant="destructive" onClick={stop} className="h-7 text-xs px-2">
              Stop
            </Button>
          )}
          {!running && lines.length > 0 && (
            <Button size="sm" variant="ghost" onClick={clear} className="h-7 text-xs px-2">
              Clear
            </Button>
          )}
          {!atBottom && (
            <Button size="sm" variant="outline" onClick={jumpToBottom} className="h-7 text-xs px-2">
              ↓ Bottom
            </Button>
          )}
        </div>
      </div>

      {/* Log output */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className={`${heightClass} overflow-y-auto rounded-md border bg-zinc-950 p-3 font-mono text-xs text-green-400`}
      >
        {lines.length === 0 && !running && (
          <p className="text-zinc-500">No output yet. Run a command to see output here.</p>
        )}
        {lines.map((line, i) => (
          <div key={i} className="whitespace-pre-wrap break-all leading-5 hover:bg-white/5">
            {line || '\u00A0'}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}

