package api

import (
	"fmt"
	"io"
	"yusi-go/internal/config"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type SSEHandler struct{}

func NewSSEHandler() *SSEHandler {
	return &SSEHandler{}
}

func (h *SSEHandler) Stream(c *gin.Context) {
	userId := c.GetString("userId")
	if userId == "" {
		response.Fail(c, "Unauthorized")
		return
	}

	// Set headers for SSE
	c.Writer.Header().Set("Content-Type", "text/event-stream")
	c.Writer.Header().Set("Cache-Control", "no-cache")
	c.Writer.Header().Set("Connection", "keep-alive")
	c.Writer.Header().Set("Transfer-Encoding", "chunked")

	ctx := c.Request.Context()
	channel := fmt.Sprintf("yusi:sse:%s", userId)

	// Subscribe to Redis
	pubsub := config.RDB.Subscribe(ctx, channel)
	defer pubsub.Close()

	// Wait for subscription to be established
	if _, err := pubsub.Receive(ctx); err != nil {
		// Log error?
		return
	}

	ch := pubsub.Channel()

	c.Stream(func(w io.Writer) bool {
		select {
		case msg, ok := <-ch:
			if !ok {
				return false
			}
			// Forward the message exactly as received
			c.SSEvent("message", msg.Payload)
			return true
		case <-ctx.Done():
			return false
		}
	})
}
