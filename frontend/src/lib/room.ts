import { api } from './api'

export interface Room {
  code: string
  ownerId: string
  maxMembers: number
  members: string[]
  status: 'WAITING' | 'IN_PROGRESS' | 'COMPLETED'
}

export interface CreateRoomRequest {
  ownerId: string
  maxMembers: number
}

export interface JoinRoomRequest {
  code: string
  userId: string
}

export interface SubmitNarrativeRequest {
  code: string
  userId: string
  content: string
}

export interface PersonalSketch {
  userId: string
  personality: string
  traits: string[]
  insights: string[]
  sketch: string
}

export interface PairCompatibility {
  userA: string
  userB: string
  score: number
  analysis: string
  advice: string
  reason: string
}

export interface Report {
  personal: PersonalSketch[]
  pairs: PairCompatibility[]
}

export const createRoom = async (req: CreateRoomRequest): Promise<Room> => {
  const { data } = await api.post('/api/rooms', req)
  return data
}

export const joinRoom = async (req: JoinRoomRequest): Promise<Room> => {
  const { data } = await api.post(`/api/rooms/${req.code}/join`, { userId: req.userId })
  return data
}

export const submitNarrative = async (req: SubmitNarrativeRequest): Promise<void> => {
  await api.post(`/api/rooms/${req.code}/submit`, { userId: req.userId, content: req.content })
}

export const getReport = async (code: string): Promise<Report> => {
  const { data } = await api.get(`/api/rooms/${code}/report`)
  return data
}