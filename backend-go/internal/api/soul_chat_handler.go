package api

import (
	"strconv"
	"yusi-go/internal/service"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type SoulChatHandler struct {
	svc *service.SoulChatService
}

func NewSoulChatHandler() *SoulChatHandler {
	return &SoulChatHandler{
		svc: service.NewSoulChatService(),
	}
}

type SendSoulMessageRequest struct {
	MatchId int64  `json:"matchId"`
	Content string `json:"content"`
}

type ReadSoulMessageRequest struct {
	MatchId int64 `json:"matchId"`
}

func (h *SoulChatHandler) Send(c *gin.Context) {
	var req SendSoulMessageRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	msg, err := h.svc.SendMessage(userId, req.MatchId, req.Content)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, msg)
}

func (h *SoulChatHandler) GetHistory(c *gin.Context) {
	matchIdStr := c.Query("matchId")
	matchId, _ := strconv.ParseInt(matchIdStr, 10, 64)
	userId := c.GetString("userId")
	
	msgs, err := h.svc.GetHistory(userId, matchId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, msgs)
}

func (h *SoulChatHandler) Read(c *gin.Context) {
	var req ReadSoulMessageRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	if err := h.svc.MarkAsRead(userId, req.MatchId); err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, nil)
}

func (h *SoulChatHandler) GetUnreadCount(c *gin.Context) {
	userId := c.GetString("userId")
	count, err := h.svc.GetUnreadCount(userId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, count)
}
