import type { Room } from '../stores/room'

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

export const createRoom = (body: CreateRoomRequest) =>
  api.post<Room>('/room/create', body).then((r) => r.data)

export const joinRoom = (body: JoinRoomRequest) =>
  api.post<Room>('/room/join', body).then((r) => r.data)

export const startRoom = (body: StartRoomRequest) =>
  api.post<Room>('/room/start', body).then((r) => r.data)

export const submitNarrative = (body: SubmitNarrativeRequest) =>
  api.post<Room>('/room/submit', body).then((r) => r.data)

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

export const getReport = (code: string) =>
  api.get<SituationReport>(`/room/report/${code}`).then((r) => r.data)

import { api } from '../lib/api'