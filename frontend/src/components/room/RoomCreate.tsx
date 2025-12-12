import { Button, Input, toast, Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '../ui'
import { useState } from 'react'
import { createRoom } from '../../lib'

export const RoomCreate = () => {
  const [loading, setLoading] = useState(false)
  const [ownerId, setOwnerId] = useState('')
  const [maxMembers, setMaxMembers] = useState(4)

  const handleCreate = async () => {
    if (!ownerId.trim()) {
      toast.error('请输入用户ID')
      return
    }
    setLoading(true)
    try {
      const room = await createRoom({ ownerId, maxMembers })
      toast.success(`房间创建成功，邀请码：${room.code}`)
      window.location.href = `/room/${room.code}`
    } catch (e) {
      // error handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>创建情景室</CardTitle>
        <CardDescription>创建一个新的房间，邀请朋友一起探索。</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">你的用户ID</label>
          <Input
            value={ownerId}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setOwnerId(e.target.value)}
            placeholder="例如：alice"
          />
        </div>
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">最大人数</label>
            <span className="text-sm text-muted-foreground">{maxMembers} 人</span>
          </div>
          <input
            type="range"
            min={2}
            max={8}
            value={maxMembers}
            onChange={(e) => setMaxMembers(parseInt(e.target.value, 10))}
            className="w-full h-2 bg-secondary rounded-lg appearance-none cursor-pointer accent-primary"
          />
        </div>
      </CardContent>
      <CardFooter>
        <Button isLoading={loading} onClick={handleCreate} className="w-full">
          创建房间
        </Button>
      </CardFooter>
    </Card>
  )
}