#!/bin/bash

# 步骤1：先清理
echo "清理旧资源..."
docker system prune -af --volumes
docker builder prune -af

# 步骤2：拉取代码
git pull

# 步骤3：Maven清理并构建（加clean）
export JAVA_HOME="/d/develop/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean compile package -DskipTests -s settings.xml

# 步骤4：Docker构建
docker compose build --no-cache

# 步骤5：启动服务
docker compose up -d

# 步骤6：事后清理
docker image prune -af