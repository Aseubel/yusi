# Yusi MCP Server

A Go-based MCP (Model Context Protocol) Server for the Yusi application, providing LLM access to core business data via SSE (Server-Sent Events).

## Features

- **SSE Mode**: Designed for containerized deployment via SSE protocol
- **MCP Tools**:
  - `get_user_recent_diaries`: Fetch a user's recent diary entries
  - `check_match_status`: Check match status between two users
- **Structured Logging**: Using Go 1.21+ slog with JSON/Text formats
- **Robust Error Handling**: Graceful database connection failure handling
- **Mock Decryption**: Placeholder for diary content decryption

## Project Structure

```
mcp-server/
├── main.go                     # Entry point
├── go.mod                      # Go module definition
├── go.sum                      # Dependency checksums
├── Dockerfile                  # Multi-stage Docker build
├── config/
│   └── config.yaml             # Configuration file
└── internal/
    ├── config/
    │   └── config.go           # Configuration management
    ├── crypto/
    │   └── decrypt.go          # Encryption/Decryption utilities
    ├── database/
    │   └── database.go         # Database connection management
    ├── logging/
    │   └── logger.go           # Slog logger setup
    ├── models/
    │   └── models.go           # GORM models
    ├── repository/
    │   ├── diary_repository.go # Diary data access
    │   └── match_repository.go # Match data access
    └── tools/
        └── tools.go            # MCP tool definitions and handlers
```

## Prerequisites

- Go 1.23+
- MySQL 8.x
- Docker (optional, for containerized deployment)

## Configuration

Configuration can be provided via:
1. YAML config file (`config/config.yaml`)
2. Environment variables (prefix: `MCP_`)

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MCP_CONFIG_PATH` | Path to config file | `./config/config.yaml` |
| `MCP_DATABASE_PASSWORD` | Database password (overrides config) | - |
| `MCP_ENCRYPTION_KEY` | 32-byte encryption key (overrides config) | - |
| `MCP_SERVER_PORT` | Server port | `8080` |
| `MCP_DATABASE_HOST` | Database host | `localhost` |
| `MCP_DATABASE_PORT` | Database port | `3306` |
| `MCP_DATABASE_DBNAME` | Database name | `yusi` |

## Building

### Local Build

```bash
cd mcp-server
go mod tidy
go build -o mcp-server .
```

### Docker Build

```bash
docker build -t yusi-mcp-server:latest .
```

## Running

### Local Run

```bash
# Set environment variables
export MCP_DATABASE_PASSWORD=your_password
export MCP_ENCRYPTION_KEY=your-32-byte-secret-key-here!!!

# Run
./mcp-server
```

### Docker Run

```bash
docker run -d \
  --name yusi-mcp-server \
  -p 8080:8080 \
  -e MCP_DATABASE_HOST=host.docker.internal \
  -e MCP_DATABASE_PASSWORD=your_password \
  -e MCP_ENCRYPTION_KEY=your-32-byte-secret-key-here!!! \
  yusi-mcp-server:latest
```

### Docker Compose

Add to your `docker-compose.yml`:

```yaml
services:
  mcp-server:
    build:
      context: ./mcp-server
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - MCP_DATABASE_HOST=mysql
      - MCP_DATABASE_PASSWORD=${DB_PASSWORD}
      - MCP_ENCRYPTION_KEY=${ENCRYPTION_KEY}
    depends_on:
      - mysql
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3
```

## MCP Tool Schemas

### get_user_recent_diaries

Retrieves recent diary entries for a specified user.

**Input:**
```json
{
  "user_id": "string (required) - User's unique identifier",
  "limit": "integer (optional) - Max number of diaries to return (1-50, default: 5)"
}
```

**Output:**
```json
{
  "user_id": "user123",
  "count": 3,
  "diaries": [
    {
      "diary_id": "diary001",
      "content": "Decrypted diary content...",
      "entry_date": "2024-12-31 10:00:00"
    }
  ]
}
```

### check_match_status

Checks the soul match status between two users.

**Input:**
```json
{
  "user_id_a": "string (required) - First user's unique identifier",
  "user_id_b": "string (required) - Second user's unique identifier"
}
```

**Output:**
```json
{
  "user_a_id": "user123",
  "user_b_id": "user456",
  "is_matched": true,
  "message": "这两个用户已经成功匹配"
}
```

## SSE Endpoint

Connect to the MCP server via SSE:

```
GET http://localhost:8080/sse
```

## Development

### Adding New Tools

1. Define the tool schema in `internal/tools/tools.go`
2. Implement the handler function
3. Register the tool in `main.go`

Example:
```go
// Define tool
func NewTool() mcp.Tool {
    return mcp.Tool{
        Name:        "new_tool",
        Description: "Description for LLM",
        InputSchema: mcp.ToolInputSchema{
            Type: "object",
            Properties: map[string]interface{}{
                "param": map[string]interface{}{
                    "type":        "string",
                    "description": "Parameter description",
                },
            },
            Required: []string{"param"},
        },
    }
}

// Implement handler
func (h *ToolHandler) HandleNewTool(ctx context.Context, request mcp.CallToolRequest) (*mcp.CallToolResult, error) {
    // Implementation
}

// Register in main.go
s.AddTool(tools.NewTool(), handler.HandleNewTool)
```

## License

Part of the Yusi project.
