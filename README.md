# Yusi Backend

Backend service for the Yusi platform, a system designed for managing AI-driven situation rooms, interactive narratives, and diary entries. This project leverages Spring Boot, LangChain4j for AI integration, and LMAX Disruptor for high-performance event handling.

## 🛠 Tech Stack

- **Core Framework**: Spring Boot 3.4.5
- **Language**: Java 17
- **Database**: MySQL (with ShardingSphere 5.5.0 for sharding)
- **Cache & Messaging**: Redis (Redisson)
- **AI Integration**: LangChain4j 1.8.0 (Chat Models, Embeddings, Milvus Vector Store)
- **Concurrency**: LMAX Disruptor (High-performance inter-thread messaging)
- **Security**: Spring Security (Custom), JWT, Attribute Encryption
- **ORM**: Spring Data JPA
- **Utils**: Hutool, Lombok

## 🚀 Key Features

- **AI Agents**: Integration with LLMs for chat memory, embeddings, and autonomous agents (`SituationRoomAgent`).
- **Situation Rooms**: Management of interactive scenarios, room creation, joining, and reporting.
- **Disruptor Chain**: High-performance event processing pipeline using the Disruptor pattern.
- **Diary System**: Secure and interactive diary management.
- **Sharding**: Database sharding capabilities using Apache ShardingSphere.

## 📦 Prerequisites

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis

## 🏃‍♂️ Getting Started

1.  **Clone the repository** (with submodule):
    ```bash
    git clone --recursive <repository-url>
    ```

2.  **Configure Database & Redis**:
    - Update `src/main/resources/application-dev.yml` with your database and Redis credentials.
    - Ensure your MySQL and Redis instances are running.

3.  **Build the project**:
    ```bash
    mvn clean install
    ```

4.  **Run the application**:
    ```bash
    mvn spring-boot:run
    ```

## 📂 Project Structure

- `src/main/java/com/aseubel/yusi/common`: Shared utilities, auth logic, and Disruptor core.
- `src/main/java/com/aseubel/yusi/config`: Configuration for AI, Redis, Security, and JPA.
- `src/main/java/com/aseubel/yusi/controller`: REST API endpoints.
- `src/main/java/com/aseubel/yusi/service`: Business logic (AI, Diary, Room, User).
- `frontend/`: Submodule containing the React frontend.
