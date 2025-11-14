import { create } from 'zustand'

export type RoomStatus = 'WAITING' | 'IN_PROGRESS' | 'COMPLETED'

export interface Room {
  code: string
  status: RoomStatus
  scenarioId?: string
  members: string[]
  submissions: Record<string, string>
}

export interface RoomStore {
  rooms: Record<string, Room>
  setRoom: (code: string, room: Room) => void
  setStatus: (code: string, status: RoomStatus) => void
  addMember: (code: string, userId: string) => void
  addSubmission: (code: string, userId: string, narrative: string) => void
}

export const useRoomStore = create<RoomStore>((set) => ({
  rooms: {},
  setRoom: (code, room) =>
    set((s) => ({ rooms: { ...s.rooms, [code]: room } })),
  setStatus: (code, status) =>
    set((s) => ({
      rooms: { ...s.rooms, [code]: { ...s.rooms[code], status } },
    })),
  addMember: (code, userId) =>
    set((s) => ({
      rooms: {
        ...s.rooms,
        [code]: {
          ...s.rooms[code],
          members: Array.from(new Set([...s.rooms[code].members, userId])),
        },
      },
    })),
  addSubmission: (code, userId, narrative) =>
    set((s) => ({
      rooms: {
        ...s.rooms,
        [code]: {
          ...s.rooms[code],
          submissions: { ...s.rooms[code].submissions, [userId]: narrative },
        },
      },
    })),
}))