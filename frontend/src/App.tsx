import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { Home } from './pages/Home'
import { Room } from './pages/Room'
import { Diary } from './pages/Diary'
import { Toaster } from './components/ui'

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/room', element: <Home /> },
  { path: '/room/:code', element: <Room /> },
  { path: '/diary', element: <Diary /> },
])

function App() {
  return (
    <>
      <RouterProvider router={router} />
      <Toaster position="top-center" />
    </>
  )
}

export default App
