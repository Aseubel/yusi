import { useParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { Layout } from '../components/Layout'
import { RoomSubmit, RoomReport } from '../components/room'
import { getReport } from '../lib'
import { useRoomStore } from '../stores'
import { Card, CardContent, CardHeader, CardTitle, Badge } from '../components/ui'
import type { PersonalSketch, PairCompatibility } from '../lib'

export const Room = () => {
  const { code } = useParams<{ code: string }>()
  const room = useRoomStore((s) => s.rooms[code!])
  const [report, setReport] = useState<{ personal: PersonalSketch[]; pairs: PairCompatibility[] } | null>(null)
  const userId = localStorage.getItem('yusi-user-id') || ''

  useEffect(() => {
    if (!code) return
    if (room?.status === 'COMPLETED') {
      getReport(code).then((r) => setReport({ personal: r.personal, pairs: r.pairs }))
    }
  }, [code, room?.status])

  if (!room) {
    return (
      <Layout>
        <div className="flex h-[50vh] items-center justify-center text-muted-foreground">
          房间不存在或已失效
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