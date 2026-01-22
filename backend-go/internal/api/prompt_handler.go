package api

import (
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type PromptHandler struct {}

func NewPromptHandler() *PromptHandler {
	return &PromptHandler{}
}

func (h *PromptHandler) SavePrompt(c *gin.Context) {
	response.Success(c, nil)
}

func (h *PromptHandler) GetPrompt(c *gin.Context) {
	name := c.Param("name")
	response.Success(c, "Mock Prompt for " + name)
}

func (h *PromptHandler) ActivatePrompt(c *gin.Context) {
	response.Success(c, nil)
}
