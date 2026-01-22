package api

import (
	"io"
	"time"
	"yusi-go/internal/service"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type AiHandler struct {
	lockSvc *service.AiLockService
}

func NewAiHandler() *AiHandler {
	return &AiHandler{
		lockSvc: service.NewAiLockService(),
	}
}

func (h *AiHandler) ChatStream(c *gin.Context) {
	userId := c.GetString("userId")
	message := c.Query("message")

	if !h.lockSvc.TryAcquireLock(userId) {
		// SSE error handling is different, usually we just close connection or send error event
		// But here we might want to return 500 or 429 before starting stream
		response.FailWithCode(c, 429, "您有一个AI请求正在处理中，请等待完成后再试")
		return
	}
	defer h.lockSvc.ReleaseLock(userId)

	// Set headers for SSE
	c.Writer.Header().Set("Content-Type", "text/event-stream")
	c.Writer.Header().Set("Cache-Control", "no-cache")
	c.Writer.Header().Set("Connection", "keep-alive")
	c.Writer.Header().Set("Transfer-Encoding", "chunked")

	c.Stream(func(w io.Writer) bool {
		// Mocking AI response for now. In real impl, call LLM stream API
		// Simulate streaming
		mockResponse := "This is a mock response from Go backend for message: " + message
		
		for _, char := range mockResponse {
			c.SSEvent("message", string(char))
			c.Writer.Flush()
			time.Sleep(50 * time.Millisecond)
		}
		
		return false // Stop stream
	})
}
