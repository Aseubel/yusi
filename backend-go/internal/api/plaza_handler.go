package api

import (
	"yusi-go/internal/service"
	"yusi-go/pkg/response"
	"strconv"

	"github.com/gin-gonic/gin"
)

type SoulPlazaHandler struct {
	svc *service.SoulPlazaService
}

func NewSoulPlazaHandler() *SoulPlazaHandler {
	return &SoulPlazaHandler{
		svc: service.NewSoulPlazaService(),
	}
}

type SubmitCardRequest struct {
	Content  string `json:"content"`
	OriginId string `json:"originId"`
	Type     string `json:"type"`
}

type ResonateRequest struct {
	Type string `json:"type"`
}

func (h *SoulPlazaHandler) Submit(c *gin.Context) {
	var req SubmitCardRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	card, err := h.svc.Submit(userId, req.Content, req.OriginId, req.Type)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, card)
}

func (h *SoulPlazaHandler) GetFeed(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	size, _ := strconv.Atoi(c.DefaultQuery("size", "10"))
	emotion := c.Query("emotion")
	userId := c.GetString("userId")
	
	res, err := h.svc.GetFeed(userId, page, size, emotion)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, res)
}

func (h *SoulPlazaHandler) Resonate(c *gin.Context) {
	cardIdStr := c.Param("cardId")
	cardId, _ := strconv.ParseInt(cardIdStr, 10, 64)
	var req ResonateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	res, err := h.svc.Resonate(userId, cardId, req.Type)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, res)
}
