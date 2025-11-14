import { Button, Textarea, toast } from '../ui'
import { useState } from 'react'
import { submitNarrative } from '../../lib'
import { countChars } from '../../utils'

export const RoomSubmit = ({ code, userId }: { code: string; userId: string }) => {
  const [narrative, setNarrative] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async () => {
    if (!narrative.trim()) {
      toast.error('请输入你的叙事')
      return
    }
    if (countChars(narrative) > 1000) {
      toast.error('叙事过长（>1000字符）')
      return
    }
    setLoading(true)
    try {
      await submitNarrative({ code, userId, content: narrative })
      toast.success('提交成功')
      window.location.reload()
    } catch (e) {
      // error handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h3 className="text-xl font-semibold mb-3">写下你的叙事（≤1000字符）</h3>
      <Textarea
        value={narrative}
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setNarrative(e.target.value)}
        rows={8}
        placeholder="描述你在该情景下会采取的行动与想法..."
      />
      <div className="mt-2 text-sm text-gray-600">
        已输入 {countChars(narrative)} / 1000 字符
      </div>
      <div className="mt-4 flex gap-3">
        <Button disabled={loading} onClick={handleSubmit}>
          {loading ? '提交中...' : '提交叙事'}
        </Button>
      </div>
    </div>
  )
}