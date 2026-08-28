#!/usr/bin/env bash
curl -s -m 8 -X POST http://127.0.0.1:8080/api/user/login \
  -H 'Content-Type: application/json' \
  -d '{"userName":"star","password":"123456"}' | head -c 300
echo
