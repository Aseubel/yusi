package api

import (
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type StatsHandler struct {}

func NewStatsHandler() *StatsHandler {
	return &StatsHandler{}
}

func (h *StatsHandler) GetPlatformStats(c *gin.Context) {
	// Mock stats
	response.Success(c, map[string]interface{}{
		"totalUsers": 100,
		"totalDiaries": 500,
		"activeUsers": 20,
	})
}
