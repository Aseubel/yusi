import { Layout } from '../components/Layout'
import { RoomCreate, RoomJoin } from '../components/room'

export const Home = () => {
  return (
    <Layout>
      <section className="text-center mb-12">
        <h2 className="text-3xl md:text-5xl font-semibold tracking-tight">把灵魂放进情景，更懂彼此</h2>
        <p className="mt-3 text-sm md:text-base text-gray-600">创建一个情景室或加入朋友的房间，一起用叙事探索真实自我与关系合拍度。</p>
      </section>
      <div className="grid md:grid-cols-2 gap-6">
        <div className="card p-8">
          <RoomCreate />
        </div>
        <div className="card p-8">
          <RoomJoin />
        </div>
      </div>
    </Layout>
  )
}