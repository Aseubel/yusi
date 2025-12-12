import type { PersonalSketch, PairCompatibility } from '../../lib'
import * as Tabs from '@radix-ui/react-tabs'
import { Card, CardHeader, CardTitle, CardContent, Badge } from '../ui'

export const RoomReport = ({ personal, pairs }: { personal: PersonalSketch[]; pairs: PairCompatibility[] }) => {
  return (
    <Card className="w-full">
      <CardHeader>
        <CardTitle>情景分析报告</CardTitle>
      </CardHeader>
      <CardContent>
        <Tabs.Root defaultValue="personal" className="w-full">
          <Tabs.List className="flex w-full rounded-lg bg-secondary p-1 text-muted-foreground mb-6">
            <Tabs.Trigger 
              value="personal" 
              className="flex-1 inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm"
            >
              个人速写
            </Tabs.Trigger>
            <Tabs.Trigger 
              value="pairs" 
              className="flex-1 inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow-sm"
            >
              合拍度矩阵
            </Tabs.Trigger>
          </Tabs.List>

          <Tabs.Content value="personal" className="space-y-4">
            {personal.map((p) => (
              <div key={p.userId} className="flex flex-col gap-2 rounded-lg border p-4 bg-muted/50">
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{p.userId}</Badge>
                </div>
                <p className="text-sm text-muted-foreground leading-relaxed">{p.sketch}</p>
              </div>
            ))}
          </Tabs.Content>

          <Tabs.Content value="pairs" className="space-y-4">
            {pairs.map((pair) => (
              <div key={`${pair.userA}-${pair.userB}`} className="space-y-2 rounded-lg border p-4 bg-muted/50">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-sm">{pair.userA} <span className="text-muted-foreground mx-1">↔</span> {pair.userB}</span>
                  <Badge variant="outline" className="font-mono">{pair.score} 分</Badge>
                </div>
                <p className="text-sm text-muted-foreground leading-relaxed">{pair.reason}</p>
              </div>
            ))}
          </Tabs.Content>
        </Tabs.Root>
      </CardContent>
    </Card>
  )
}