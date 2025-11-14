import { clsx } from 'clsx'
import { forwardRef } from 'react'

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger'
  size?: 'sm' | 'md' | 'lg'
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', ...rest }, ref) => {
    const base = 'inline-flex items-center justify-center rounded-full font-semibold transition-all focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed shadow-sm active:scale-[0.99]'
    const variants = {
      primary: 'bg-black text-white hover:bg-[#111] focus:ring-black',
      secondary: 'bg-[#f5f5f7] text-[#0a0a0a] hover:bg-[#eaeaef] dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700 focus:ring-gray-400',
      danger: 'bg-red-600 text-white hover:bg-red-700 focus:ring-red-500',
    }
    const sizes = {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-5 py-2 text-sm',
      lg: 'px-6 py-2.5 text-base',
    }
    return (
      <button
        ref={ref}
        className={clsx(base, variants[variant], sizes[size], className)}
        {...rest}
      />
    )
  }
)
Button.displayName = 'Button'