import type { PersonalSketch, PairCompatibility } from '../../lib'
import * as Tabs from '@radix-ui/react-tabs'

export const RoomReport = ({ personal, pairs }: { personal: PersonalSketch[]; pairs: PairCompatibility[] }) => {
  return (
    <Tabs.Root defaultValue="personal" className="w-full">
      <Tabs.List className="flex gap-2 mb-4">
        <Tabs.Trigger value="personal" className="px-3 py-1.5 text-sm rounded-md border data-[state=active]:bg-indigo-600 data-[state=active]:text-white">个人速写</Tabs.Trigger>
        <Tabs.Trigger value="pairs" className="px-3 py-1.5 text-sm rounded-md border data-[state=active]:bg-indigo-600 data-[state=active]:text-white">合拍度矩阵</Tabs.Trigger>
      </Tabs.List>

      <Tabs.Content value="personal" className="space-y-2">
        {personal.map((p) => (
          <div key={p.userId} className="flex items-start gap-2">
            <span className="px-2 py-1 rounded-full bg-indigo-50 text-indigo-700 text-xs">{p.userId}</span>
            <p className="text-sm text-gray-700">{p.sketch}</p>
          </div>
        ))}
      </Tabs.Content>

      <Tabs.Content value="pairs" className="space-y-2">
        {pairs.map((pair) => (
          <div key={`${pair.userA}-${pair.userB}`} className="space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-medium text-sm">{pair.userA} ↔ {pair.userB}</span>
              <span className="text-indigo-600 font-semibold">{pair.score} 分</span>
            </div>
            <p className="text-gray-600 text-sm">{pair.reason}</p>
          </div>
        ))}
      </Tabs.Content>
    </Tabs.Root>
  )
}