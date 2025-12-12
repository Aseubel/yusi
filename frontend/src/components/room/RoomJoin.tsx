import { Button, Input, toast, Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '../ui'
import { useState } from 'react'
import { joinRoom } from '../../lib'

export const RoomJoin = () => {
  const [code, setCode] = useState('')
  const [userId, setUserId] = useState('')
  const [loading, setLoading] = useState(false)

  const handleJoin = async () => {
    if (!code.trim() || !userId.trim()) {
      toast.error('请完整填写')
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
        <div className="space-y-2">
          <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">你的用户ID</label>
          <Input
            value={userId}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setUserId(e.target.value)}
            placeholder="例如：bob"
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