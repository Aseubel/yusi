import { Button, Textarea, Card, CardHeader, CardTitle, CardContent, CardDescription, CardFooter, Input } from './ui'
import { toast } from 'sonner'
import { useState, useEffect } from 'react'
import { writeDiary, getDiaryList, generateAiResponse, type Diary as DiaryType } from '../lib'
import { Link } from 'react-router-dom'
import { ChevronLeft, Sparkles, Lock, MessageCircle } from 'lucide-react'
import { cn } from '../utils'
import { useChatStore } from '../stores'

export const Diary = () => {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(false)
  const [genLoading, setGenLoading] = useState<string | null>(null)
  const [diaries, setDiaries] = useState<DiaryType[]>([])
  const userId = localStorage.getItem('yusi-user-id') || ''
  
  const { setIsOpen, setInitialMessage } = useChatStore()

  const loadDiaries = async () => {
    if (!userId) return
    try {
      const list = await getDiaryList(userId)
      setDiaries(list)
    } catch (e) {
      console.error('Failed to load diaries', e)
    }
  }

  useEffect(() => {
    loadDiaries()
  }, [userId])

  const handleSave = async () => {
    if (!title.trim() || !content.trim()) {
      toast.error('标题与内容不能为空')
      return
    }
    setLoading(true)
    try {
      await writeDiary({ userId, title, content })
      toast.success('日记已保存')
      setTitle('')
      setContent('')
      loadDiaries()
    } catch (e) {
      // error handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  const handleGenerate = async (diaryId: string) => {
    setGenLoading(diaryId)
    try {
        await generateAiResponse(diaryId)
        toast.success('AI回应生成中，请稍候刷新')
        // In a real app, we might want to poll or use websocket/SSE to get the update.
        // For now, let's just reload after a short delay or rely on manual refresh/polling logic if implemented.
        // Or simply wait a bit and reload list.
        setTimeout(loadDiaries, 3000)
    } catch (e) {
        // error handled
    } finally {
        setGenLoading(null)
    }
  }

  const handleChat = (diary: DiaryType) => {
      const context = `我刚写了一篇日记：\n标题：${diary.title}\n内容：${diary.content}\n\nAI的回应是：${diary.aiResponse}\n\n`
      setInitialMessage(context)
      setIsOpen(true)
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
        <h2 className="text-3xl md:text-5xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-primary to-indigo-500 dark:from-indigo-400 dark:to-cyan-400">
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

      <div className="space-y-6">
        <h3 className="text-2xl font-semibold tracking-tight">往期日记</h3>
        {diaries.length === 0 && (
          <div className="text-center text-muted-foreground py-10">
            暂无日记，写下第一篇吧。
          </div>
        )}
        {diaries.map((diary) => (
          <Card key={diary.diaryId} className="overflow-hidden">
            <CardHeader className="pb-3">
              <div className="flex justify-between items-start">
                <div className="space-y-1">
                  <CardTitle className="text-xl">{diary.title}</CardTitle>
                  <CardDescription>
                    {new Date(diary.createTime).toLocaleString()} <Lock className="inline w-3 h-3 ml-1" />
                  </CardDescription>
                </div>
                <div className="flex gap-2">
                {diary.aiResponse ? (
                  <>
                  <Badge variant="secondary" className="bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300">
                    <Sparkles className="w-3 h-3 mr-1" /> 已回应
                  </Badge>
                  <Button variant="ghost" size="sm" onClick={() => handleChat(diary)}>
                      <MessageCircle className="w-4 h-4 mr-1" /> 聊聊
                  </Button>
                  </>
                ) : (
                    <Button 
                        variant="outline" 
                        size="sm" 
                        isLoading={genLoading === diary.diaryId}
                        onClick={() => handleGenerate(diary.diaryId)}
                    >
                        <Sparkles className="w-3 h-3 mr-1" /> 生成AI回应
                    </Button>
                )}
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="text-sm leading-relaxed whitespace-pre-wrap text-muted-foreground/90">
                {diary.content}
              </div>
              
              {diary.aiResponse && (
                <div className="mt-4 rounded-lg bg-secondary/50 p-4 border border-border/50">
                  <div className="flex items-center gap-2 mb-2 text-indigo-600 dark:text-indigo-400 font-medium text-sm">
                    <Sparkles className="w-4 h-4" />
                    Yusi 的回应
                  </div>
                  <div className="text-sm leading-relaxed whitespace-pre-wrap">
                    {diary.aiResponse}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}

function Badge({ children, className }: any) {
    return (
        <span className={cn("inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2", className)}>
            {children}
        </span>
    )
}