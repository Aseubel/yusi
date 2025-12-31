package main

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/aseubel/yusi-mcp-server/internal/config"
	"github.com/aseubel/yusi-mcp-server/internal/crypto"
	"github.com/aseubel/yusi-mcp-server/internal/database"
	"github.com/aseubel/yusi-mcp-server/internal/logging"
	"github.com/aseubel/yusi-mcp-server/internal/repository"
	"github.com/aseubel/yusi-mcp-server/internal/tools"
	"github.com/mark3labs/mcp-go/mcp"
	"github.com/mark3labs/mcp-go/server"
)

func main() {
	// Load configuration
	configPath := os.Getenv("MCP_CONFIG_PATH")
	cfg, err := config.Load(configPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to load configuration: %v\n", err)
		os.Exit(1)
	}

	// Setup logging
	logging.Setup(cfg.Logging.Level, cfg.Logging.Format)

	slog.Info("Starting Yusi MCP Server",
		"name", cfg.Server.Name,
		"version", cfg.Server.Version,
	)

	// Initialize database connection
	db, err := database.New(&cfg.Database)
	if err != nil {
		slog.Error("Failed to connect to database",
			"error", err,
		)
		os.Exit(1)
	}
	defer func() {
		if err := db.Close(); err != nil {
			slog.Error("Error closing database connection",
				"error", err,
			)
		}
	}()

	// Initialize decryptor
	decryptor := crypto.NewDecryptor(cfg.Encryption.Key)
	if decryptor.IsMockMode() {
		slog.Warn("Running in mock decryption mode - content will not be decrypted")
	}

	// Initialize repositories
	diaryRepo := repository.NewDiaryRepository(db)
	matchRepo := repository.NewMatchRepository(db)

	// Initialize tool handler
	toolHandler := tools.NewToolHandler(diaryRepo, matchRepo, decryptor)

	// Create MCP server
	mcpServer := server.NewMCPServer(
		cfg.Server.Name,
		cfg.Server.Version,
		server.WithLogging(),
	)

	// Register tools
	registerTools(mcpServer, toolHandler)

	// Create SSE server
	sseServer := server.NewSSEServer(mcpServer)

	// Setup graceful shutdown
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigChan
		slog.Info("Received shutdown signal",
			"signal", sig.String(),
		)
		cancel()
	}()

	// Start HTTP server for SSE
	addr := fmt.Sprintf("%s:%d", cfg.Server.Host, cfg.Server.Port)
	slog.Info("Starting SSE server",
		"address", addr,
		"endpoint", "/sse",
	)

	// Run server in goroutine
	go func() {
		if err := sseServer.Start(addr); err != nil {
			slog.Error("SSE server error",
				"error", err,
			)
			cancel()
		}
	}()

	// Wait for shutdown
	<-ctx.Done()

	slog.Info("Shutting down MCP Server gracefully")

	// Perform cleanup
	if err := sseServer.Shutdown(context.Background()); err != nil {
		slog.Error("Error during server shutdown",
			"error", err,
		)
	}

	slog.Info("MCP Server stopped")
}

// registerTools registers all MCP tools with the server
func registerTools(s *server.MCPServer, handler *tools.ToolHandler) {
	// Register get_user_recent_diaries tool
	s.AddTool(
		tools.GetUserRecentDiariesTool(),
		handler.HandleGetUserRecentDiaries,
	)
	slog.Info("Registered tool", "name", "get_user_recent_diaries")

	// Register check_match_status tool
	s.AddTool(
		tools.CheckMatchStatusTool(),
		handler.HandleCheckMatchStatus,
	)
	slog.Info("Registered tool", "name", "check_match_status")

	// Log server capabilities
	slog.Info("MCP Server tools registered",
		"total_tools", 2,
		"capabilities", []string{
			"get_user_recent_diaries: 获取用户最近日记",
			"check_match_status: 检查用户匹配状态",
		},
	)
}

// serverInfo returns server information for health checks
func serverInfo(cfg *config.Config) mcp.Implementation {
	return mcp.Implementation{
		Name:    cfg.Server.Name,
		Version: cfg.Server.Version,
	}
}
