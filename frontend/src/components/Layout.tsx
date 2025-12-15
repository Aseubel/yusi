import { type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { cn } from '../utils'
import { Moon, Sun, Github, LogOut, User as UserIcon } from 'lucide-react'
import { useAuthStore } from '../store/authStore'
import { Button } from './ui/Button'
import { ChatWidget } from './ChatWidget'

export interface LayoutProps {
  children: ReactNode
}

export const Layout = ({ children }: LayoutProps) => {
  const { pathname } = useLocation()
  const { user, logout } = useAuthStore()
  
  const navItems = [
    { label: '首页', href: '/' },
    { label: '情景室', href: '/room' },
    { label: 'AI知己', href: '/diary' },
  ]

  const toggleTheme = () => {
    document.documentElement.classList.toggle('dark')
  }

  return (
    <div className="flex min-h-screen flex-col bg-background font-sans antialiased">
      <header className="sticky top-0 z-50 w-full border-b border-border/40 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="container-page flex h-14 max-w-screen-2xl items-center">
          <div className="mr-4 flex">
            <Link to="/" className="mr-6 flex items-center space-x-2">
              <span className="inline-flex h-6 w-6 items-center justify-center rounded-lg bg-primary text-primary-foreground font-bold">Y</span>
              <span className="hidden font-bold sm:inline-block">Yusi · 灵魂叙事</span>
            </Link>
            <nav className="flex items-center gap-6 text-sm font-medium">
              {navItems.map((item) => (
                <Link
                  key={item.href}
                  to={item.href}
                  className={cn(
                    "transition-colors hover:text-foreground/80",
                    pathname === item.href ? "text-foreground" : "text-foreground/60"
                  )}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
          </div>
          <div className="flex flex-1 items-center justify-end space-x-2">
            <nav className="flex items-center gap-2">
              {user ? (
                <div className="flex items-center gap-2">
                  <div className="flex items-center gap-2 px-2 py-1.5 rounded-full bg-secondary/50">
                    <UserIcon className="h-4 w-4 text-muted-foreground" />
                    <span className="text-sm font-medium hidden md:inline-block">
                      {user.userName}
                    </span>
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => {
                      logout()
                      localStorage.removeItem('yusi-user-id')
                    }}
                    title="退出登录"
                  >
                    <LogOut className="h-4 w-4" />
                  </Button>
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  <Link to="/login">
                    <Button variant="ghost" size="sm">登录</Button>
                  </Link>
                  <Link to="/register">
                    <Button size="sm">注册</Button>
                  </Link>
                </div>
              )}
              
              <Link
                to="https://github.com"
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-input bg-background px-0 py-2 text-sm font-medium shadow-sm transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
              >
                <Github className="h-4 w-4" />
                <span className="sr-only">GitHub</span>
              </Link>
              <button
                onClick={toggleTheme}
                className="ml-2 inline-flex h-9 w-9 items-center justify-center rounded-md border border-input bg-background px-0 py-2 text-sm font-medium shadow-sm transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
              >
                <Sun className="h-[1.2rem] w-[1.2rem] rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
                <Moon className="absolute h-[1.2rem] w-[1.2rem] rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
                <span className="sr-only">Toggle theme</span>
              </button>
            </nav>
          </div>
        </div>
      </header>
      <main className="flex-1 container-page py-6 md:py-10">
        {children}
      </main>
      <footer className="py-6 md:px-8 md:py-0">
        <div className="container-page flex flex-col items-center justify-between gap-4 md:h-24 md:flex-row">
          <p className="text-center text-sm leading-loose text-muted-foreground md:text-left">
            Built by <span className="font-medium underline underline-offset-4">Yusi Team</span>. The source code is available on <span className="font-medium underline underline-offset-4">GitHub</span>.
          </p>
        </div>
      </footer>
      <ChatWidget />
    </div>
  )
}