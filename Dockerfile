# 阶段 1: Exploded - 展开 JAR 包并创建 CDS 归档
FROM eclipse-temurin:17-jre-alpine AS cds_creator
WORKDIR /app

# 从构建上下文（即 GitHub Actions 的工作区）复制已经由 Maven 打包好的 JAR 文件
COPY target/*.jar app.jar

# 展开 JAR 以便进行分层和创建 CDS 归档
RUN java -Djarmode=layertools -jar app.jar extract

# 创建 CDS 归档以加快启动速度
RUN java -Xshare:dump -XX:SharedArchiveFile=app.jsa -cp . org.springframework.boot.loader.launch.JarLauncher

# 阶段 2: Final - 构建最终的运行镜像
FROM eclipse-temurin:17-jre-alpine AS final
WORKDIR /app

# 从上一阶段复制 CDS 归档和展开的应用层
COPY --from=cds_creator /app/app.jsa .
COPY --from=cds_creator /app/dependencies/ ./
COPY --from=cds_creator /app/spring-boot-loader/ ./
COPY --from=cds_creator /app/snapshot-dependencies/ ./
COPY --from=cds_creator /app/application/ ./

# 设置启动命令
ENTRYPOINT ["java", "-Xshare:on", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]