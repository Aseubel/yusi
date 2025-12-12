import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * 通用工具函数 - 合并 Tailwind 类名
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * 后端地址常量
 */
export const API_BASE = '/api'

/**
 * 简单延迟
 */
export const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/**
 * 生成随机邀请码（6位）
 */
export const randomCode = () => Math.random().toString(36).substring(2, 8).toUpperCase()

/**
 * 字数统计（中文双字节）
 */
export const countChars = (s: string) => {
  let n = 0
  for (let i = 0; i < s.length; i++) {
    n += s.charCodeAt(i) > 255 ? 2 : 1
  }
  return n
}