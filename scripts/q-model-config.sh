#!/usr/bin/env bash
docker exec mysql mysql -uroot -proot123456 yusi -e "SELECT id, operator_id, remark, created_at, LEFT(config_json, 400) AS cfg FROM model_config_change_log ORDER BY id DESC LIMIT 5\G" 2>/dev/null
