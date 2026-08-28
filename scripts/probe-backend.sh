#!/usr/bin/env bash
# 探测 benchmark 依赖链路：后端 / 本地 Redis / MySQL（通过 docker 容器）
set -u

echo '=== backend login probe ==='
code=$(curl -s -m 8 -o /tmp/login_probe.json -w '%{http_code}' \
  -X POST http://127.0.0.1:20611/api/user/login \
  -H 'Content-Type: application/json' \
  -d '{"userName":"probe","password":"x"}')
echo "HTTP $code"
head -c 300 /tmp/login_probe.json; echo

echo '=== redis (container) ==='
docker exec redis redis-cli ping

echo '=== mysql (container) ==='
docker exec mysql mysqladmin ping -uroot -p"$(docker exec mysql printenv MYSQL_ROOT_PASSWORD 2>/dev/null || echo root)" 2>/dev/null | tail -1

echo '=== milvus (container) ==='
docker exec milvus curl -s -m 5 http://127.0.0.1:9091/healthz || echo milvus-healthz-failed
echo
