import { api } from './api'

export interface Diary {
  diaryId: string
  userId: string
  title: string
  content: string
  visibility: boolean
  aiResponse?: string
  status?: number // 1 = Analyzed
  createTime: string
  updateTime: string
}

export interface WriteDiaryRequest {
  userId: string
  title: string
  content: string
  visibility?: boolean
}

export interface PagedModel<T> {
  _embedded?: {
    diaryList: T[]
  }
  content?: T[] // Fallback if HATEOAS is not fully strictly used or configured differently
  page: {
    size: number
    totalElements: number
    totalPages: number
    number: number
  }
}

export const writeDiary = async (req: WriteDiaryRequest): Promise<void> => {
  await api.post('/diary', req)
}

export const getDiaryList = async (userId: string, pageNum = 1, pageSize = 10): Promise<Diary[]> => {
  const { data } = await api.get(`/diary/list`, {
    params: { userId, pageNum, pageSize }
  })
  
  // Handle Spring HATEOAS structure
  const pagedModel = data.data as PagedModel<Diary>
  
  if (pagedModel._embedded?.diaryList) {
    return pagedModel._embedded.diaryList
  }
  
  // Fallback for standard PageImpl serialization
  if (pagedModel.content) {
    return pagedModel.content
  }
  
  return []
}

export const generateAiResponse = async (diaryId: string): Promise<void> => {
  await api.post(`/diary/generate-response/${diaryId}`)
}
