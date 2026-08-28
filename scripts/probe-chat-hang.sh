#!/usr/bin/env bash
# 查两个 backend pod 在 chat 超时窗口（02:07-02:10Z）的行为
docker exec kind-control-plane sh -c '
NS=yusi-prod
for POD in $(kubectl -n $NS get pods -o name | grep yusi-backend); do
  echo "===== $POD ====="
  kubectl -n $NS logs "$POD" --since=35m 2>&1 | grep -iE "chat/stream|agentrun|runId|AI 请求|42901|dashscope|qwen|mcp|web_search| milvus|zilliz|Exception|ERROR" | head -30
done
'
