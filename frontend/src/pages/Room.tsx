import { useParams } from 'react-router-dom'
import { useEffect, useState, useRef } from 'react'
import { Layout } from '../components/Layout'
import { RoomSubmit, RoomReport } from '../components/room'
import { getReport, getRoom, cancelRoom, startRoom } from '../lib'
import { useRoomStore } from '../stores'
import { Card, CardContent, CardHeader, CardTitle, Badge, Button } from '../components/ui'
import { toast } from 'sonner'
import type { PersonalSketch, PairCompatibility } from '../lib'
import { Play } from 'lucide-react'

export const Room = () => {
  const { code } = useParams<{ code: string }>()
  const room = useRoomStore((s) => s.rooms[code!])
  const setRoom = useRoomStore((s) => s.setRoom)
  const [report, setReport] = useState<{ personal: PersonalSketch[]; pairs: PairCompatibility[] } | null>(null)
  const userId = localStorage.getItem('yusi-user-id') || ''
  const timerRef = useRef<any>(null)
  const [starting, setStarting] = useState(false)

  const fetchRoom = async () => {
    if (!code) return
    try {
      const data = await getRoom(code)
      setRoom(code, data)
    } catch (e) {
      console.error(e)
    }
  }

  useEffect(() => {
    fetchRoom()
    timerRef.current = setInterval(fetchRoom, 2000)
    return () => {
        if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [code])

  useEffect(() => {
    if (!code) return
    if (room?.status === 'COMPLETED' && !report) {
      getReport(code).then((r) => setReport({ personal: r.personal, pairs: r.pairs }))
    }
  }, [code, room?.status])
  
  const handleCancel = async () => {
      if (!code || !userId) return
      if (!confirm('确定要解散房间吗？')) return
      
      try {
          await cancelRoom(code, userId)
          toast.success('房间已解散')
          window.location.href = '/'
      } catch (e) {
          // handled
      }
  }

  const handleStart = async () => {
      if (!code || !userId) return
      if (room.members.length < 2) {
          toast.error('至少需要2人才能开始')
          return
      }
      setStarting(true)
      try {
          // TODO: Let user select scenario. For now use default '1'
          await startRoom({ code, scenarioId: '1', ownerId: userId })
          toast.success('房间已开始')
          fetchRoom()
      } catch (e) {
          // handled
      } finally {
          setStarting(false)
      }
  }


  if (!room) {
    return (
      <Layout>
        <div className="flex h-[50vh] flex-col items-center justify-center text-muted-foreground gap-4">
            <div className="text-lg">正在寻找房间信息...</div>
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
        </div>
      </Layout>
    )
  }

  const submitted = room.submissions[userId]

  return (
    <Layout>
      <div className="space-y-6 max-w-5xl mx-auto">
        <div className="flex items-center justify-between">
          <h2 className="text-3xl font-bold tracking-tight">房间 {code}</h2>
          <div className="flex gap-2 items-center">
          {room.status === 'WAITING' && room.ownerId === userId && (
              <>
              <Button 
                variant="primary" 
                size="sm" 
                onClick={handleStart}
                isLoading={starting}
                disabled={room.members.length < 2}
              >
                  <Play className="w-4 h-4 mr-1" /> 开始
              </Button>
              <Button variant="danger" size="sm" onClick={handleCancel}>
                  解散
              </Button>
              </>
          )}
          <Badge 
            variant={
              room.status === 'WAITING' ? 'secondary' :
              room.status === 'IN_PROGRESS' ? 'default' :
              'outline'
            }
            className="text-sm px-3 py-1"
          >
            {room.status === 'WAITING' && '等待中'}
            {room.status === 'IN_PROGRESS' && '进行中'}
            {room.status === 'COMPLETED' && '已完成'}
          </Badge>
          </div>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">成员 ({room.members.length}/8)</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex gap-2 flex-wrap">
              {room.members.map((m) => (
                <Badge key={m} variant="secondary" className="px-3 py-1 text-sm">
                  {m}
                </Badge>
              ))}
            </div>
          </CardContent>
        </Card>

        {room.status === 'IN_PROGRESS' && !submitted && (
          <RoomSubmit code={code!} userId={userId} />
        )}

        {room.status === 'IN_PROGRESS' && submitted && (
          <Card>
            <CardContent className="p-8 text-center text-muted-foreground">
              你已提交，等待其他成员...
            </CardContent>
          </Card>
        )}

        {room.status === 'COMPLETED' && report && (
          <RoomReport personal={report.personal} pairs={report.pairs} />
        )}
      </div>
    </Layout>
  )
}