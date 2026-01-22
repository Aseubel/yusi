package api

import (
	"yusi-go/internal/service"
	"yusi-go/pkg/response"
	"strconv"

	"github.com/gin-gonic/gin"
)

type MatchHandler struct {
	svc *service.MatchService
}

func NewMatchHandler() *MatchHandler {
	return &MatchHandler{
		svc: service.NewMatchService(),
	}
}

type MatchSettingsRequest struct {
	Enabled bool   `json:"enabled"`
	Intent  string `json:"intent"`
}

type MatchActionRequest struct {
	Action int `json:"action"`
}

func (h *MatchHandler) UpdateSettings(c *gin.Context) {
	var req MatchSettingsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	user, err := h.svc.UpdateSettings(userId, req.Enabled, req.Intent)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, user)
}

func (h *MatchHandler) GetRecommendations(c *gin.Context) {
	userId := c.GetString("userId")
	matches, err := h.svc.GetMatches(userId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, matches)
}

func (h *MatchHandler) HandleAction(c *gin.Context) {
	matchIdStr := c.Param("matchId")
	matchId, _ := strconv.ParseInt(matchIdStr, 10, 64)
	var req MatchActionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	match, err := h.svc.HandleAction(userId, matchId, req.Action)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, match)
}

func (h *MatchHandler) Run(c *gin.Context) {
	// Dev endpoint
	response.Success(c, "Matching process triggered.")
}
