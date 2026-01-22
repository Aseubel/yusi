package api

import (
	"yusi-go/internal/service"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type KeyHandler struct {
	svc *service.KeyManagementService
}

func NewKeyHandler() *KeyHandler {
	return &KeyHandler{
		svc: service.NewKeyManagementService(),
	}
}

func (h *KeyHandler) GetSettings(c *gin.Context) {
	userId := c.GetString("userId")
	res, err := h.svc.GetKeySettings(userId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, res)
}

func (h *KeyHandler) UpdateSettings(c *gin.Context) {
	var req service.KeyModeUpdateRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	if err := h.svc.UpdateKeyMode(userId, req); err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, nil)
}

func (h *KeyHandler) GetDiariesForReEncrypt(c *gin.Context) {
	userId := c.GetString("userId")
	res, err := h.svc.GetAllDiariesForReEncrypt(userId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, res)
}

func (h *KeyHandler) BatchUpdateReEncryptedDiaries(c *gin.Context) {
	var req service.DiaryReEncryptRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	if err := h.svc.BatchUpdateReEncryptedDiaries(userId, req); err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, nil)
}

func (h *KeyHandler) GetBackupKey(c *gin.Context) {
	adminId := c.GetString("userId")
	targetUserId := c.Param("targetUserId")
	key, err := h.svc.GetBackupKeyForRecovery(adminId, targetUserId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, key)
}
