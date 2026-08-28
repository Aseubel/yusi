#!/usr/bin/env bash
# 停掉遗留的 kubectl port-forward（20611 -> kind 集群 yusi-backend）
pids=$(pgrep -f 'kubectl.*port-forward.*20611' || true)
if [ -n "$pids" ]; then
  echo "killing port-forward pids: $pids"
  kill $pids
else
  echo "no port-forward process found"
fi
