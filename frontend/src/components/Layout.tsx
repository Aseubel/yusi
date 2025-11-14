import type { ReactNode } from 'react'

export interface LayoutProps {
  children: ReactNode
}

export const Layout = ({ children }: LayoutProps) => (
  <div>
    <header className="sticky top-0 z-10 border-b border-[#e5e5ea] bg-white/90 backdrop-blur-sm">
      <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
        <a href="/" className="flex items-center gap-2">
          <span className="inline-flex h-7 w-7 items-center justify-center rounded-md bg-black text-white">Y</span>
          <span className="text-sm md:text-base font-semibold">Yusi · 灵魂叙事</span>
        </a>
        <nav className="flex items-center gap-6 text-sm">
          <a href="/" className="hover:opacity-70">首页</a>
          <a href="/room" className="hover:opacity-70">情景室</a>
          <a href="/diary" className="hover:opacity-70">AI知己</a>
          <button
            aria-label="切换主题"
            onClick={() => document.documentElement.classList.toggle('dark')}
            className="ml-2 inline-flex h-8 w-8 items-center justify-center rounded-full border border-[#e5e5ea] hover:bg-[#f5f5f7]"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-4 w-4">
              <path d="M17.75 19a8.25 8.25 0 1 1-4.99-14.88 8.25 8.25 0 0 0 9.12 9.12A8.26 8.26 0 0 1 17.75 19Z" />
            </svg>
          </button>
        </nav>
      </div>
    </header>
    <main className="max-w-6xl mx-auto px-4 py-12">{children}</main>
  </div>
)