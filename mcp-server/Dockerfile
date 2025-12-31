# Build stage
FROM golang:1.23-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git ca-certificates tzdata

# Set working directory
WORKDIR /app

# Copy go mod files first for caching
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY . .

# Build the binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-w -s" \
    -o /app/mcp-server \
    .

# Runtime stage
FROM alpine:3.19

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata

# Create non-root user
RUN addgroup -g 1000 -S mcp && \
    adduser -u 1000 -S mcp -G mcp

# Set working directory
WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/mcp-server /app/mcp-server

# Copy default config
COPY config/config.yaml /app/config/config.yaml

# Set ownership
RUN chown -R mcp:mcp /app

# Switch to non-root user
USER mcp

# Expose SSE port
EXPOSE 8080

# Set environment variables
ENV MCP_CONFIG_PATH=/app/config/config.yaml

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the server
ENTRYPOINT ["/app/mcp-server"]
