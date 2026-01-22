package api

import (
	"yusi-go/internal/service"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type RoomChatHandler struct {
	svc *service.RoomChatService
}

func NewRoomChatHandler() *RoomChatHandler {
	return &RoomChatHandler{
		svc: service.NewRoomChatService(),
	}
}

type SendMessageRequest struct {
	RoomCode string `json:"roomCode"`
	Content  string `json:"content"`
}

func (h *RoomChatHandler) Send(c *gin.Context) {
	var req SendMessageRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	msg, err := h.svc.SendMessage(req.RoomCode, userId, req.Content)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, msg)
}

func (h *RoomChatHandler) GetHistory(c *gin.Context) {
	roomCode := c.Query("roomCode")
	userId := c.GetString("userId")
	msgs, err := h.svc.GetHistory(roomCode, userId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, msgs)
}

func (h *RoomChatHandler) Poll(c *gin.Context) {
	roomCode := c.Query("roomCode")
	after := c.Query("after")
	userId := c.GetString("userId")
	msgs, err := h.svc.PollMessages(roomCode, userId, after)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, msgs)
}
