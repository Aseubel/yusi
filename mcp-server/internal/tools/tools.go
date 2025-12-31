package tools

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"

	"github.com/aseubel/yusi-mcp-server/internal/crypto"
	"github.com/aseubel/yusi-mcp-server/internal/models"
	"github.com/aseubel/yusi-mcp-server/internal/repository"
	"github.com/mark3labs/mcp-go/mcp"
)

// ToolHandler provides MCP tool implementations
type ToolHandler struct {
	diaryRepo *repository.DiaryRepository
	matchRepo *repository.MatchRepository
	decryptor *crypto.Decryptor
}

// NewToolHandler creates a new ToolHandler
func NewToolHandler(
	diaryRepo *repository.DiaryRepository,
	matchRepo *repository.MatchRepository,
	decryptor *crypto.Decryptor,
) *ToolHandler {
	return &ToolHandler{
		diaryRepo: diaryRepo,
		matchRepo: matchRepo,
		decryptor: decryptor,
	}
}

// GetUserRecentDiariesTool returns the tool definition for get_user_recent_diaries
func GetUserRecentDiariesTool() mcp.Tool {
	return mcp.Tool{
		Name:        "get_user_recent_diaries",
		Description: "获取指定用户的最近日记内容。返回按日期倒序排列的日记列表，包含日记ID、解密后的内容和日期。适用于了解用户近期的心情、想法和生活状态。",
		InputSchema: mcp.ToolInputSchema{
			Type: "object",
			Properties: map[string]interface{}{
				"user_id": map[string]interface{}{
					"type":        "string",
					"description": "用户的唯一标识符 (user_id)，用于查询该用户的日记",
				},
				"limit": map[string]interface{}{
					"type":        "integer",
					"description": "返回日记的最大数量，默认为5，最大为50",
					"minimum":     1,
					"maximum":     50,
					"default":     5,
				},
			},
			Required: []string{"user_id"},
		},
	}
}

// CheckMatchStatusTool returns the tool definition for check_match_status
func CheckMatchStatusTool() mcp.Tool {
	return mcp.Tool{
		Name:        "check_match_status",
		Description: "检查两个用户之间的灵魂匹配状态。返回两人是否已经成功匹配的信息。适用于确认用户之间的匹配关系。",
		InputSchema: mcp.ToolInputSchema{
			Type: "object",
			Properties: map[string]interface{}{
				"user_id_a": map[string]interface{}{
					"type":        "string",
					"description": "第一个用户的唯一标识符 (user_id)",
				},
				"user_id_b": map[string]interface{}{
					"type":        "string",
					"description": "第二个用户的唯一标识符 (user_id)",
				},
			},
			Required: []string{"user_id_a", "user_id_b"},
		},
	}
}

// HandleGetUserRecentDiaries handles the get_user_recent_diaries tool call
func (h *ToolHandler) HandleGetUserRecentDiaries(ctx context.Context, request mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	slog.Info("Handling get_user_recent_diaries request")

	// Parse arguments using SDK helper methods
	userID := request.GetString("user_id", "")
	if userID == "" {
		return createErrorResult("参数错误: user_id 是必需的字符串参数"), nil
	}

	// Parse limit with default value using SDK helper
	limit := request.GetInt("limit", 5)

	// Validate limit
	if limit < 1 {
		limit = 1
	} else if limit > 50 {
		limit = 50
	}

	// Fetch diaries from database
	diaries, err := h.diaryRepo.GetRecentDiariesByUserID(ctx, userID, limit)
	if err != nil {
		slog.Error("Failed to fetch diaries",
			"user_id", userID,
			"error", err,
		)
		return createErrorResult(fmt.Sprintf("查询日记失败: %v", err)), nil
	}

	// Transform and decrypt diaries
	responses := make([]models.DiaryResponse, 0, len(diaries))
	for _, diary := range diaries {
		// Decrypt content
		decryptedContent, err := h.decryptor.Decrypt(diary.Content)
		if err != nil {
			slog.Warn("Failed to decrypt diary content, using original",
				"diary_id", diary.DiaryID,
				"error", err,
			)
			decryptedContent = diary.Content
		}

		// Format entry date
		entryDateStr := ""
		if diary.EntryDate != nil {
			entryDateStr = diary.EntryDate.Format("2006-01-02")
		}

		responses = append(responses, models.DiaryResponse{
			DiaryID:   diary.DiaryID,
			Title:     diary.Title,
			Content:   decryptedContent,
			EntryDate: entryDateStr,
		})
	}

	// Build result
	result := map[string]interface{}{
		"user_id": userID,
		"count":   len(responses),
		"diaries": responses,
	}

	resultJSON, err := json.Marshal(result)
	if err != nil {
		return createErrorResult("序列化结果失败"), nil
	}

	slog.Info("Successfully processed get_user_recent_diaries",
		"user_id", userID,
		"diary_count", len(responses),
	)

	return &mcp.CallToolResult{
		Content: []mcp.Content{
			mcp.TextContent{
				Type: "text",
				Text: string(resultJSON),
			},
		},
	}, nil
}

// HandleCheckMatchStatus handles the check_match_status tool call
func (h *ToolHandler) HandleCheckMatchStatus(ctx context.Context, request mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	slog.Info("Handling check_match_status request")

	// Parse arguments using SDK helper methods
	userAID := request.GetString("user_id_a", "")
	if userAID == "" {
		return createErrorResult("参数错误: user_id_a 是必需的字符串参数"), nil
	}

	userBID := request.GetString("user_id_b", "")
	if userBID == "" {
		return createErrorResult("参数错误: user_id_b 是必需的字符串参数"), nil
	}

	// Check if same user
	if userAID == userBID {
		return createErrorResult("参数错误: user_id_a 和 user_id_b 不能相同"), nil
	}

	// Check match status
	match, err := h.matchRepo.CheckMatchStatus(ctx, userAID, userBID)
	if err != nil {
		if errors.Is(err, repository.ErrMatchNotFound) {
			// No match record found
			response := models.MatchStatusResponse{
				UserAID:   userAID,
				UserBID:   userBID,
				IsMatched: false,
				Message:   "这两个用户之间没有匹配记录",
			}
			return createSuccessResult(response)
		}

		slog.Error("Failed to check match status",
			"user_a_id", userAID,
			"user_b_id", userBID,
			"error", err,
		)
		return createErrorResult(fmt.Sprintf("查询匹配状态失败: %v", err)), nil
	}

	// Build response
	var message string
	if match.IsMatched {
		message = "这两个用户已经成功匹配"
	} else {
		message = "这两个用户有匹配记录，但尚未确认匹配"
	}

	response := models.MatchStatusResponse{
		UserAID:   userAID,
		UserBID:   userBID,
		IsMatched: match.IsMatched,
		Message:   message,
	}

	slog.Info("Successfully processed check_match_status",
		"user_a_id", userAID,
		"user_b_id", userBID,
		"is_matched", match.IsMatched,
	)

	return createSuccessResult(response)
}

// createSuccessResult creates a successful CallToolResult with JSON content
func createSuccessResult(data interface{}) (*mcp.CallToolResult, error) {
	jsonData, err := json.Marshal(data)
	if err != nil {
		return createErrorResult("序列化结果失败"), nil
	}

	return &mcp.CallToolResult{
		Content: []mcp.Content{
			mcp.TextContent{
				Type: "text",
				Text: string(jsonData),
			},
		},
	}, nil
}

// createErrorResult creates an error CallToolResult
func createErrorResult(message string) *mcp.CallToolResult {
	return &mcp.CallToolResult{
		Content: []mcp.Content{
			mcp.TextContent{
				Type: "text",
				Text: fmt.Sprintf(`{"error": true, "message": "%s"}`, message),
			},
		},
		IsError: true,
	}
}
