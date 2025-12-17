import { Button, Input, toast, Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '../ui'
import { useState } from 'react'
import { joinRoom } from '../../lib'

export const RoomJoin = () => {
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(false)
  const userId = localStorage.getItem('yusi-user-id') || ''

  const handleJoin = async () => {
    if (!userId) {
      toast.error('请先登录')
      return
    }
    if (!code.trim()) {
      toast.error('请输入邀请码')
      return
    }
    setLoading(true)
    try {
      await joinRoom({ code: code.toUpperCase(), userId })
      toast.success('加入成功')
      window.location.href = `/room/${code.toUpperCase()}`
    } catch (e) {
      // error handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>加入情景室</CardTitle>
        <CardDescription>输入邀请码，加入朋友的房间。</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">邀请码（6位）</label>
          <Input
            value={code}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setCode(e.target.value.toUpperCase())}
            placeholder="ABC123"
            maxLength={6}
          />
        </div>
      </CardContent>
      <CardFooter>
        <Button isLoading={loading} onClick={handleJoin} className="w-full" variant="secondary">
          加入房间
        </Button>
      </CardFooter>
    </Card>
  )
}