#!/usr/bin/env bash
# 查看 kind 集群 yusi-backend 在 benchmark E2E 窗口内的错误日志
NS=yusi-prod
SINCE=${1:-40m}
docker exec kind-control-plane sh -c "
for POD in \$(kubectl -n $NS get pods -o name | grep yusi-backend); do
  echo \"===== \$POD =====\"
  kubectl -n $NS logs \"\$POD\" --since=$SINCE 2>&1 | grep -iE 'Connection reset|JsonEOF|栗子|备用钥匙|bench-e2e|42901|dashscope|ERROR|Exception' | tail -40
done
"
