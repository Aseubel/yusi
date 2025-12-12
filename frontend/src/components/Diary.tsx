import { Button, Textarea, Card, CardHeader, CardTitle, CardContent, CardDescription, CardFooter, Input } from './ui'
import { toast } from 'sonner'
import { useState } from 'react'
import { api } from '../lib/api'
import { Link } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'

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
    <div className="max-w-3xl mx-auto space-y-8">
      <div>
        <Link to="/" className="inline-flex items-center text-sm text-muted-foreground hover:text-foreground transition-colors">
          <ChevronLeft className="w-4 h-4 mr-1" />
          返回首页
        </Link>
      </div>

      <div className="text-center space-y-4">
        <h2 className="text-3xl md:text-5xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-indigo-600 to-fuchsia-600 dark:from-indigo-400 dark:to-fuchsia-400">
          AI知己 · 私密日记
        </h2>
        <p className="text-muted-foreground text-lg">端到端加密，仅你可见。</p>
      </div>
      
      <Card>
        <CardHeader>
          <CardTitle>写日记</CardTitle>
          <CardDescription>记录你的经历、想法与感受。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">标题</label>
            <Input
              value={title}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setTitle(e.target.value)}
              placeholder="给今天起个名字"
            />
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">内容</label>
            <Textarea
              value={content}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setContent(e.target.value)}
              rows={10}
              placeholder="开始书写..."
              className="resize-none"
            />
          </div>
        </CardContent>
        <CardFooter className="flex flex-col sm:flex-row gap-4 items-center justify-between">
          <div className="text-xs text-muted-foreground">所有内容端到端加密，仅用于AI分析。</div>
          <Button isLoading={loading} onClick={handleSave} className="w-full sm:w-auto">
            保存日记
          </Button>
        </CardFooter>
      </Card>
    </div>
  )
}