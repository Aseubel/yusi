#!/usr/bin/env bash
EMAIL="bench-e2e-manual@benchmark.invalid"
CODE="manual7"
docker exec redis redis-cli -a redis123456 --no-auth-warning SET "auth:verification_code:${EMAIL}" "\"${CODE}\"" PX 300000
echo '--- register ---'
curl -s -m 10 -w '\nHTTP %{http_code}\n' -X POST http://127.0.0.1:8080/api/user/register \
  -H 'Content-Type: application/json' \
  -d "{\"userName\":\"benchmanual\",\"password\":\"BenchRun2026\",\"email\":\"${EMAIL}\",\"code\":\"${CODE}\"}"
