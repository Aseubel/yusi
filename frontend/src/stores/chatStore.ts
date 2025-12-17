import { create } from 'zustand'

interface ChatStore {
  isOpen: boolean
  initialMessage: string
  setIsOpen: (isOpen: boolean) => void
  setInitialMessage: (msg: string) => void
  openChatWithContext: (context: string) => void
}

export const useChatStore = create<ChatStore>((set) => ({
  isOpen: false,
  initialMessage: '',
  setIsOpen: (isOpen) => set({ isOpen }),
  setInitialMessage: (initialMessage) => set({ initialMessage }),
  openChatWithContext: (context) => set({ isOpen: true, initialMessage: context })
}))
