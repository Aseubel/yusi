#!/usr/bin/env bash
# 将 yusi-backend 服务端口转发到本机 20611（供基准测试访问）
set -euo pipefail
nohup kubectl -n yusi-prod port-forward --address 0.0.0.0 svc/yusi-backend 20611:611 \
  > /tmp/yusi-portforward.log 2>&1 &
echo "port_forward_pid=$!"
sleep 3
ss -tln | grep 20611 && echo forward_listening || { echo forward_failed; cat /tmp/yusi-portforward.log; }
