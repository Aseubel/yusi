import { Layout } from '../components/Layout'
import { RoomCreate, RoomJoin } from '../components/room'
import { motion } from 'framer-motion'

export const Home = () => {
  return (
    <Layout>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="space-y-12"
      >
        <section className="text-center space-y-4">
          <h2 className="text-3xl md:text-5xl font-bold tracking-tight bg-gradient-to-r from-gray-900 to-gray-600 bg-clip-text text-transparent dark:from-white dark:to-gray-400">
            把灵魂放进情景，更懂彼此
          </h2>
          <p className="max-w-[42rem] mx-auto text-muted-foreground sm:text-xl sm:leading-8">
            创建一个情景室或加入朋友的房间，一起用叙事探索真实自我与关系合拍度。
          </p>
        </section>
        
        <div className="grid md:grid-cols-2 gap-6 max-w-4xl mx-auto">
          <motion.div
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.2, duration: 0.5 }}
          >
            <RoomCreate />
          </motion.div>
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.3, duration: 0.5 }}
          >
            <RoomJoin />
          </motion.div>
        </div>
      </motion.div>
    </Layout>
  )
}
