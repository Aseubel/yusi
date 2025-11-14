import { clsx } from 'clsx'
import { forwardRef } from 'react'

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, ...rest }, ref) => {
    return (
      <input
        ref={ref}
        className={clsx(
          'flex w-full rounded-xl border border-[#d1d5db] bg-[#f5f5f7] px-3.5 py-2.5 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-black focus:bg-white disabled:cursor-not-allowed disabled:opacity-50',
          className
        )}
        {...rest}
      />
    )
  }
)
Input.displayName = 'Input'