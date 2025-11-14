import { Button, Textarea } from './ui'
import { toast } from 'sonner'
import { useState } from 'react'
import { api } from '../lib/api'

export const Diary = () => {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(false)
  const userId = localStorage.getItem('yusi-user-id') || ''

  const handleSave = async () => {
    if (!title.trim() || !content.trim()) {
      toast.error('标题与内容不能为空')
      return
    }
    setLoading(true)
    try {
      await api.post('/diary', { userId, title, content })
      toast.success('日记已保存')
      setTitle('')
      setContent('')
    } catch (e) {
      // error handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-3xl mx-auto">
      <div className="text-center mb-6">
        <h2 className="text-2xl md:text-3xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-indigo-600 to-fuchsia-600">AI知己 · 私密日记</h2>
        <p className="mt-2 text-sm text-gray-600">端到端加密，仅你可见。</p>
      </div>
      <div className="card p-6 space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">标题</label>
          <input
            value={title}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setTitle(e.target.value)}
            placeholder="给今天起个名字"
            className="w-full rounded-md border px-3 py-2 text-sm bg-white dark:bg-zinc-900"
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">内容</label>
          <Textarea
            value={content}
            onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setContent(e.target.value)}
            rows={10}
            placeholder="写下你的经历、想法与感受..."
          />
        </div>
        <div className="text-xs text-gray-600">所有内容端到端加密，仅用于AI分析。</div>
        <div className="flex justify-end">
          <Button disabled={loading} onClick={handleSave}>
            {loading ? '保存中...' : '保存日记'}
          </Button>
        </div>
      </div>
    </div>
  )
}