# Yusi 后端服务

Yusi 是基于 Spring Boot 的后端服务，提供日记管理、情景房间协作与基于向量检索的对话能力。本文档帮助你在本地或生产环境快速搭建、运行与扩展该服务。

## 技术栈
- Spring Boot 3.4.5，Java 17
- Spring Web、Spring Data JPA、Hibernate、HikariCP
- MySQL 8.x，Redis（Redisson）
- 可选：Apache ShardingSphere JDBC 5.5.0（分库分表）、LangChain4j + Milvus/Zilliz（向量检索/嵌入）、LMAX Disruptor（事件处理）

## 环境要求
- `Java 17` 与 `Maven 3.9+`
- `MySQL 8.x`（创建数据库 `yusi`）
- `Redis`（本地默认：`127.0.0.1:6379`）
- 可选：`Milvus`/`Zilliz Cloud`
- 环境变量：
  - `QWEN_API_KEY`（用于嵌入模型）
  - `YUSI_ENCRYPTION_KEY`（启用字段加密，至少 16 字符）

## 快速开始
1. 配置数据库与缓存：
   - 在 `src/main/resources/application-dev.yml` 调整 `spring.datasource.url/username/password` 指向你的 MySQL
   - 保持 `redis.sdk.config` 与本地 Redis 设置一致
2. 设置环境变量：
   - Windows PowerShell：`$env:QWEN_API_KEY = "<your_key>"`；`$env:YUSI_ENCRYPTION_KEY = "<your_key>"`
3. 启动开发环境：
   - `mvn spring-boot:run`
   - 默认端口 `20611`
4. 打包与运行：
   - `mvn clean package`
   - `java -jar target/yusi-0.0.1-SNAPSHOT.jar`
   - 切换环境：`java -jar target/yusi-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`

## 配置说明
- 基础配置：`src/main/resources/application.yml`
  - 默认激活 `dev`：`spring.profiles.active: dev`
- 开发/生产配置：
  - `application-dev.yml`、`application-prod.yml`
  - 数据源：`spring.datasource.*`（`driver-class-name: com.mysql.cj.jdbc.Driver`）
  - JPA：`show-sql: true`、`hibernate.ddl-auto: none`、命名策略 `CamelCaseToUnderscoresNamingStrategy`
  - 上传限制：`spring.servlet.multipart.max-file-size: 15MB`、`max-request-size: 60MB`
- 日志：`src/main/resources/logback-spring.xml`（控制台输出；日志路径 `${log.path}: ./data/log`）

### 可选：启用 ShardingSphere 分片
- 配置文件：`src/main/resources/shardingsphere-config.yaml`
- 启用步骤（在对应环境 yml 中）：
  - 注释取消：
    - `spring.datasource.driver-class-name: org.apache.shardingsphere.driver.ShardingSphereDriver`
    - `spring.datasource.url: jdbc:shardingsphere:classpath:shardingsphere-config.yaml`
- 当前示例分片：表 `diary` 使用 `user_id` 取模到 `diary_1/diary_2`，主键生成 `SNOWFLAKE`

### 字段加密
- `Diary.content` 使用 `AttributeEncryptor`（`AES/GCM`）进行透明加解密
- 需设置 `YUSI_ENCRYPTION_KEY` 环境变量，否则回退为明文存储

## 运行端口与服务
- 端口：`20611`
- CORS：控制器标注 `@CrossOrigin("*")`
- Redis：`redis.sdk.config`（默认本地）
- Milvus（可选）：`milvus.mode: 2` 支持 Zilliz Cloud；请通过安全方式设置 `uri/token`

## 包结构
- 入口：`com.aseubel.yusi.YusiApplication`
- 控制器：`controller/*`
- 实体/DTO：`pojo/entity/*`、`pojo/dto/*`
- 仓储：`repository/*`
- 业务：`service/*`
- 配置：`config/*`（包含 AI/线程池/Redis/安全）
- 公共组件：`common/*`（分片算法、响应包装、Disruptor 等）

## 主要接口（简要）
- 日记 `@RequestMapping("/api/diary")`
  - `GET /list`：分页列表（`userId`、`pageNum`、`pageSize`、`sortBy`、`asc`）
  - `POST /`：新增日记（`WriteDiaryRequest`）
  - `PUT /`：编辑日记（`EditDiaryRequest`）
  - `GET /{diaryId}`：获取详情
  - `POST /rag`：基于 RAG 的对话（`DiaryChatRequest`）
- 用户 `@RequestMapping("/api/user")`
  - `POST /register`：注册（`RegisterRequest`）
- 情景房间 `@RequestMapping("/api/room")`
  - `POST /create`、`POST /join`、`POST /start`、`POST /submit`
  - `GET /report/{code}`：获取汇总报告

## 示例请求
```bash
# 获取日记分页
curl "http://localhost:20611/api/diary/list?userId=0001&pageNum=1&pageSize=10&asc=true"

# 新增日记
curl -X POST "http://localhost:20611/api/diary" \
  -H "Content-Type: application/json" \
  -d '{"userId":"0001","title":"测试","content":"今天很开心","visibility":true,"entryDate":"2025-11-14"}'
```

## 常用命令
- 开发运行：`mvn spring-boot:run`
- 单元测试：`mvn test`
- 打包：`mvn clean package`
- 生产运行：`java -jar target/yusi-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`

## 安全与敏感信息
- 不要在配置文件中提交真实的 `API Key`、数据库密码或云端 Token
- 使用环境变量或安全的密钥管理方案（本项目已通过 `QWEN_API_KEY`、`YUSI_ENCRYPTION_KEY` 支持）

## 反馈与扩展
- 如需进一步补充接口文档（字段定义、错误码、示例响应）或生成 OpenAPI，请提出需求，我可以基于当前控制器自动化生成说明。