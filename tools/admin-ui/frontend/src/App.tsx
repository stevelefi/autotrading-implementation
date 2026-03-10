import { useQuery, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom'
import { Layers, FlaskConical, ShieldCheck, Users, Activity, Search } from 'lucide-react'
import { cn } from '@/lib/utils'
import StackPage   from '@/pages/StackPage'
import TestsPage   from '@/pages/TestsPage'
import ChecksPage  from '@/pages/ChecksPage'
import OnboardPage from '@/pages/OnboardPage'
import HealthPage  from '@/pages/HealthPage'
import TracePage   from '@/pages/TracePage'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 10_000 } },
})

const NAV = [
  { to: '/',        label: 'Stack',   Icon: Layers,       desc: 'Manage Docker stack'    },
  { to: '/tests',   label: 'Tests',   Icon: FlaskConical,  desc: 'Run test suites'        },
  { to: '/checks',  label: 'Checks',  Icon: ShieldCheck,   desc: 'Pre-commit gate'        },
  { to: '/onboard', label: 'Onboard', Icon: Users,         desc: 'Accounts & agents'      },
  { to: '/health',  label: 'Health',  Icon: Activity,      desc: 'Service health'         },
  { to: '/trace',   label: 'Trace',   Icon: Search,        desc: 'Loki log viewer'        },
]

function useBackendStatus() {
  const { isSuccess, isError } = useQuery({
    queryKey: ['backend-ping'],
    queryFn:  () => fetch('/api/stack/status').then(r => r.ok ? r.json() : Promise.reject(new Error('not ok'))),
    refetchInterval: 15_000,
    retry: 1,
  })
  return isSuccess ? 'up' : isError ? 'down' : 'checking'
}

function Sidebar() {
  const backendStatus = useBackendStatus()

  return (
    <aside className="w-56 shrink-0 border-r bg-muted/20 flex flex-col">
      {/* Logo / header */}
      <div className="px-5 py-4 border-b bg-background">
        <div className="flex items-center gap-2">
          <div className="h-6 w-6 rounded bg-primary flex items-center justify-center">
            <span className="text-primary-foreground text-[10px] font-bold">AT</span>
          </div>
          <span className="font-semibold text-sm tracking-tight">Admin Console</span>
        </div>
        <p className="text-[11px] text-muted-foreground mt-1">autotrading-implementation</p>
      </div>

      {/* Nav */}
      <nav className="flex-1 p-2 flex flex-col gap-0.5 overflow-y-auto">
        {NAV.map(({ to, label, Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              cn(
                'group flex items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground font-medium'
                  : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
              )
            }
          >
            <Icon className="w-4 h-4 shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Footer with backend status */}
      <div className="px-4 py-3 border-t flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <span
            className={cn(
              'h-2 w-2 rounded-full',
              backendStatus === 'up'       ? 'bg-green-500'
              : backendStatus === 'down'   ? 'bg-red-500'
              : 'bg-yellow-500 animate-pulse',
            )}
          />
          <span>
            {backendStatus === 'up' ? 'Connected' : backendStatus === 'down' ? 'Offline' : 'Connecting…'}
          </span>
        </div>
        <span className="text-[10px] text-muted-foreground/60">:8765</span>
      </div>
    </aside>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="flex h-screen overflow-hidden bg-background">
          <Sidebar />
          <main className="flex-1 overflow-y-auto p-6">
            <Routes>
              <Route path="/"        element={<StackPage   />} />
              <Route path="/tests"   element={<TestsPage   />} />
              <Route path="/checks"  element={<ChecksPage  />} />
              <Route path="/onboard" element={<OnboardPage />} />
              <Route path="/health"  element={<HealthPage  />} />
              <Route path="/trace"   element={<TracePage   />} />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

