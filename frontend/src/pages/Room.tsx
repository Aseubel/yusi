import { useParams } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { Layout } from '../components/Layout'
import { RoomSubmit, RoomReport } from '../components/room'
import { getReport } from '../lib'
import { useRoomStore } from '../stores'
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
        <div className="text-center">房间不存在或已失效</div>
      </Layout>
    )
  }

  const submitted = room.submissions[userId]

  return (
    <Layout>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-semibold">房间 {code}</h2>
          <span className={
            `badge ${
              room.status === 'WAITING' ? 'border-yellow-200 text-yellow-700 bg-yellow-50' :
              room.status === 'IN_PROGRESS' ? 'border-blue-200 text-blue-700 bg-blue-50' :
              'border-emerald-200 text-emerald-700 bg-emerald-50'
            }`
          }>{room.status}</span>
        </div>

        <div className="card p-4">
          <div className="text-sm text-gray-600">成员 ({room.members.length}/8)</div>
          <div className="mt-2 flex gap-2 flex-wrap">
            {room.members.map((m) => (
              <span key={m} className="px-2 py-1 rounded-full bg-indigo-50 text-indigo-700 text-sm">{m}</span>
            ))}
          </div>
        </div>

        {room.status === 'IN_PROGRESS' && !submitted && (
          <div className="card p-4">
            <RoomSubmit code={code!} userId={userId} />
          </div>
        )}

        {room.status === 'IN_PROGRESS' && submitted && (
          <div className="card p-4 text-center text-gray-600">你已提交，等待其他成员...</div>
        )}

        {room.status === 'COMPLETED' && report && (
          <div className="card p-4">
            <RoomReport personal={report.personal} pairs={report.pairs} />
          </div>
        )}
      </div>
    </Layout>
  )
}