import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { Home } from './pages/Home'
import { Room } from './pages/Room'
import { Diary } from './pages/Diary'
import { Login } from './pages/Login'
import { Register } from './pages/Register'
import { Toaster } from './components/ui'

const router = createBrowserRouter([
  { path: '/', element: <Home /> },
  { path: '/room', element: <Home /> },
  { path: '/room/:code', element: <Room /> },
  { path: '/diary', element: <Diary /> },
  { path: '/login', element: <Login /> },
  { path: '/register', element: <Register /> },
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
