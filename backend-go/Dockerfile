# Build stage
FROM golang:1.24-alpine AS builder

# Change to China mirror for apk
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# Install build dependencies
RUN apk add --no-cache git

# Set working directory
WORKDIR /app

# Set GOPROXY for China
ENV GOPROXY=https://goproxy.cn,direct

# Copy go mod and sum files
COPY go.mod go.sum ./

# Download all dependencies
RUN --mount=type=cache,target=/go/pkg/mod go mod download

# Copy the source code
COPY . .

# Build the application
RUN --mount=type=cache,target=/root/.cache/go-build \
    --mount=type=cache,target=/go/pkg/mod \
    CGO_ENABLED=0 GOOS=linux go build -o server ./cmd/server

# Final stage
FROM alpine:latest

# Change to China mirror for apk
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# Install basic dependencies (ca-certificates for HTTPS, tzdata for timezones, curl for healthcheck)
RUN apk --no-cache add ca-certificates tzdata curl

WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/server .

# Copy config file
COPY --from=builder /app/config/application.yml ./config/application.yml

# Expose port
EXPOSE 20611

# Run the binary
CMD ["./server"]
