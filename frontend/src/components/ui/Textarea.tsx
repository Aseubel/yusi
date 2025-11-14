import { clsx } from 'clsx'
import { forwardRef, type TextareaHTMLAttributes } from 'react'

export interface TextareaProps
  extends TextareaHTMLAttributes<HTMLTextAreaElement> {}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, ...rest }, ref) => {
    return (
      <textarea
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
Textarea.displayName = 'Textarea'