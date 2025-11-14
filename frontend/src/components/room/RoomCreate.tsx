import { Button, Input, toast } from '../ui'
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
    <div>
      <h2 className="text-xl md:text-2xl font-semibold mb-4">创建情景室</h2>
      <div className="space-y-5">
        <div>
          <label className="block text-sm font-medium mb-1">你的用户ID</label>
          <Input
            value={ownerId}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setOwnerId(e.target.value)}
            placeholder="例如：alice"
            className="w-full"
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">最大人数（2–8）</label>
          <input
            type="range"
            min={2}
            max={8}
            value={maxMembers}
            onChange={(e) => setMaxMembers(parseInt(e.target.value, 10))}
            className="w-full accent-indigo-600"
          />
          <div className="text-xs text-gray-600 mt-1">{maxMembers} 人</div>
        </div>
        <Button disabled={loading} onClick={handleCreate} className="w-full">
          {loading ? '创建中...' : '创建房间'}
        </Button>
      </div>
    </div>
  )
}