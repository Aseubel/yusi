#!/usr/bin/env bash
docker exec mysql mysql -uroot -proot123456 yusi -e "
SELECT id, request_id, target_user_ref, status, retry_count, failure_category, created_at
FROM account_deletion_request ORDER BY id DESC LIMIT 5\G" 2>/dev/null
