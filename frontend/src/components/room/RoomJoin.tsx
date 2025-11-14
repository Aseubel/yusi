import { Button, Input, toast } from '../ui'
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
    <div>
      <h2 className="text-xl md:text-2xl font-semibold mb-4">加入情景室</h2>
      <div className="space-y-5">
        <div>
          <label className="block text-sm font-medium mb-1">邀请码（6位）</label>
          <Input
            value={code}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setCode(e.target.value.toUpperCase())}
            placeholder="ABC123"
            maxLength={6}
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">你的用户ID</label>
          <Input
            value={userId}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setUserId(e.target.value)}
            placeholder="例如：bob"
          />
        </div>
        <Button disabled={loading} onClick={handleJoin} className="w-full">
          {loading ? '加入中...' : '加入房间'}
        </Button>
      </div>
    </div>
  )
}