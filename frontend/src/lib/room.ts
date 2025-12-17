import { api } from './api'

export interface Room {
  code: string
  status: 'WAITING' | 'IN_PROGRESS' | 'COMPLETED'
  ownerId?: string
  scenarioId?: string
  members: string[]
  submissions: Record<string, string>
}

export interface CreateRoomRequest {
  ownerId: string
  maxMembers: number
}

export interface JoinRoomRequest {
  code: string
  userId: string
}

export interface StartRoomRequest {
  code: string
  scenarioId: string
  ownerId: string
}

export interface SubmitNarrativeRequest {
  code: string
  userId: string
  narrative: string
}

export interface PersonalSketch {
  userId: string
  sketch: string
}

export interface PairCompatibility {
  userA: string
  userB: string
  score: number
  reason: string
}

export interface SituationReport {
  scenarioId: string
  personal: PersonalSketch[]
  pairs: PairCompatibility[]
}

export const createRoom = async (req: CreateRoomRequest): Promise<Room> => {
  const { data } = await api.post('/room/create', req)
  return data.data
}

export const joinRoom = async (req: JoinRoomRequest): Promise<Room> => {
  const { data } = await api.post('/room/join', req)
  return data.data
}

export const startRoom = async (req: StartRoomRequest): Promise<Room> => {
  const { data } = await api.post('/room/start', req)
  return data.data
}

export const submitNarrative = async (req: SubmitNarrativeRequest): Promise<Room> => {
  const { data } = await api.post('/room/submit', req)
  return data.data
}

export const cancelRoom = async (code: string, userId: string): Promise<void> => {
  await api.post('/room/cancel', { code, userId })
}

export const getReport = async (code: string): Promise<SituationReport> => {
  const { data } = await api.get(`/room/report/${code}`)
  return data.data
}

export const getRoom = async (code: string): Promise<Room> => {
  const { data } = await api.get(`/room/${code}`)
  return data.data
}
