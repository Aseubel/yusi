#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_ARGS=(
    --project-name yusi
    --env-file "$SCRIPT_DIR/yusi_prod.env"
    --file "$SCRIPT_DIR/docker-compose.yml"
)
SERVICE_NAME="yusi"
IMAGE_NAME="yusi:latest"
RUN_MAVEN=1
NO_CACHE="${DOCKER_BUILD_NO_CACHE:-0}"

usage() {
    cat <<'EOF'
用法:
  ./rebuild.sh [选项]

选项:
  --docker-only, --skip-maven  跳过 git pull、JDK 检查和 Maven，直接使用现有 target/*.jar 构建 Docker
  --no-cache                   禁用 Docker 构建缓存
  -h, --help                  显示帮助

示例:
  ./rebuild.sh                  完整同步、编译、构建并重启
  ./rebuild.sh --docker-only   复用现有 target，重试 Docker 构建并重启
  ./rebuild.sh --no-cache      完整流程，但 Docker 构建不使用缓存
EOF
}

while (($# > 0)); do
    case "$1" in
        --docker-only|--skip-maven)
            RUN_MAVEN=0
            ;;
        --no-cache)
            NO_CACHE=1
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "未知选项: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
    shift
done

# Define JDK version and installation path
JDK_VERSION="21"
JDK_DIR="/root/jdks"
JDK_NAME="jdk-21"
JDK_ARCHIVE="OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz"
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz"

if (( RUN_MAVEN )); then
    # 同步代码。不要清理全局 Docker 资源，否则会同时破坏其他项目和构建缓存。
    git pull --ff-only

    # Ensure JDK version is available for Maven.
    mkdir -p "$JDK_DIR"

    if [ ! -d "$JDK_DIR/$JDK_NAME" ]; then
        echo "JDK $JDK_VERSION not found. Downloading..."

        if [ ! -f "$JDK_DIR/$JDK_ARCHIVE" ]; then
            if ! wget -O "$JDK_DIR/$JDK_ARCHIVE" "$JDK_URL"; then
                echo "Failed to download JDK. Please check your network connection."
                exit 1
            fi
        fi

        echo "Extracting JDK..."
        tar -xzf "$JDK_DIR/$JDK_ARCHIVE" -C "$JDK_DIR"

        EXTRACTED_DIR="$(find "$JDK_DIR" -maxdepth 1 -type d -name 'jdk-21*' ! -name "$JDK_NAME" -print -quit)"
        if [ -n "$EXTRACTED_DIR" ] && [ -d "$EXTRACTED_DIR" ]; then
            mv "$EXTRACTED_DIR" "$JDK_DIR/$JDK_NAME"
        else
            echo "Failed to extract JDK correctly."
            exit 1
        fi

        rm "$JDK_DIR/$JDK_ARCHIVE"
        echo "JDK installed successfully at $JDK_DIR/$JDK_NAME"
    else
        echo "JDK $JDK_VERSION found at $JDK_DIR/$JDK_NAME"
    fi

    export JAVA_HOME="$JDK_DIR/$JDK_NAME"
    export PATH="$JAVA_HOME/bin:$PATH"

    echo "Using Java version:"
    java -version

    echo "Building project with Maven..."
    if ! ./mvnw clean package -DskipTests -s settings.xml; then
        echo "Maven build failed."
        exit 1
    fi
    echo "Maven build successful."
else
    echo "Docker-only mode: skipping git pull, JDK setup and Maven build."
fi

echo "Starting Docker build..."

# 记录旧镜像 ID。构建成功并切换容器后，只回收这个项目上一版镜像。
OLD_IMAGE_ID="$(docker image inspect "$IMAGE_NAME" --format '{{.Id}}' 2>/dev/null || true)"

# 默认使用 BuildKit 的已有层缓存；需要强制重建时可临时设置
# DOCKER_BUILD_NO_CACHE=1 ./rebuild.sh。
BUILD_ARGS=()
if [[ "$NO_CACHE" == "1" ]]; then
    BUILD_ARGS+=(--no-cache)
fi

if ! compgen -G "$SCRIPT_DIR/target/*.jar" > /dev/null; then
    echo "No JAR found under $SCRIPT_DIR/target/. Run ./rebuild.sh first, or provide a valid existing target." >&2
    exit 1
fi

if ! DOCKER_BUILDKIT=1 docker compose "${COMPOSE_ARGS[@]}" build "${BUILD_ARGS[@]}" "$SERVICE_NAME"; then
    echo "Docker build failed."
    exit 1
fi

echo "Docker build successful. Restarting container..."
docker compose "${COMPOSE_ARGS[@]}" up -d --force-recreate --remove-orphans "$SERVICE_NAME"

NEW_IMAGE_ID="$(docker image inspect "$IMAGE_NAME" --format '{{.Id}}')"
if [[ -n "$OLD_IMAGE_ID" && "$OLD_IMAGE_ID" != "$NEW_IMAGE_ID" ]]; then
    echo "Removing previous yusi image: $OLD_IMAGE_ID"
    if ! docker image rm "$OLD_IMAGE_ID"; then
        echo "Previous image is still referenced by another container; leaving it untouched."
    fi
fi

echo "Done."
