package api

import (
	"yusi-go/internal/model"
	"yusi-go/internal/service"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type UserHandler struct {
	svc *service.UserService
}

func NewUserHandler() *UserHandler {
	return &UserHandler{
		svc: service.NewUserService(),
	}
}

type LoginRequest struct {
	UserName string `json:"userName"`
	Password string `json:"password"`
}

type RegisterRequest struct {
	UserName string `json:"userName"`
	Password string `json:"password"`
	Email    string `json:"email"`
}

func (h *UserHandler) Register(c *gin.Context) {
	var req RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}

	user := &model.User{
		UserName: req.UserName,
		Password: req.Password,
		Email:    req.Email,
	}

	res, err := h.svc.Register(user)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}

	response.Success(c, res)
}

func (h *UserHandler) Login(c *gin.Context) {
	var req LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}

	res, err := h.svc.Login(req.UserName, req.Password)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}

	response.Success(c, res)
}

func (h *UserHandler) Refresh(c *gin.Context) {
	refreshToken := c.GetHeader("X-Refresh-Token")
	if refreshToken == "" {
		response.Fail(c, "Missing refresh token")
		return
	}

	res, err := h.svc.RefreshToken(refreshToken)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}

	response.Success(c, res)
}

func (h *UserHandler) Logout(c *gin.Context) {
	// In a real implementation, we might blacklist the token in Redis
	response.Success(c, nil)
}
