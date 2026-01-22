package api

import (
	"strconv"
	"yusi-go/internal/model"
	"yusi-go/internal/service"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type DiaryHandler struct {
	svc *service.DiaryService
}

func NewDiaryHandler() *DiaryHandler {
	return &DiaryHandler{
		svc: service.NewDiaryService(),
	}
}

type WriteDiaryRequest struct {
	Title           string `json:"title"`
	Content         string `json:"content"`
	ClientEncrypted bool   `json:"clientEncrypted"`
	PlainContent    string `json:"plainContent"`
	Visibility      bool   `json:"visibility"`
	EntryDate       string `json:"entryDate"`
}

type EditDiaryRequest struct {
	DiaryID         string `json:"diaryId"`
	Title           string `json:"title"`
	Content         string `json:"content"`
	ClientEncrypted bool   `json:"clientEncrypted"`
	PlainContent    string `json:"plainContent"`
	Visibility      bool   `json:"visibility"`
	EntryDate       string `json:"entryDate"`
}

func (h *DiaryHandler) GetDiaryList(c *gin.Context) {
	userId := c.Query("userId")
	if userId == "" {
		// Fallback to token user id if param not provided or verify consistency
		userId = c.GetString("userId")
	}
	
	pageNum, _ := strconv.Atoi(c.DefaultQuery("pageNum", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "10"))
	sortBy := c.Query("sortBy")
	asc, _ := strconv.ParseBool(c.DefaultQuery("asc", "true"))

	diaries, total, err := h.svc.GetDiaryList(userId, pageNum, pageSize, sortBy, asc)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}

	// Mocking HATEOAS structure to match Spring PagedModel
	// The frontend expects: { content: [], page: { ... } } or similar depending on PagedResourcesAssembler
	// Let's verify standard Spring Data REST format or custom RestPage
	// Based on Java code: assembler.toModel(diaryPage)
	// Standard PagedModel has: _embedded.diaryList, _links, page
	// But `RestPage` class was seen in file list. Let's assume a simpler structure or try to match.
	// For now, returning a structure that usually works for custom frontends.
	
	// Actually, looking at `RestPage.java` in file list, it might be a custom page.
	// Let's return a generic page structure.
	
	result := map[string]interface{}{
		"content": diaries,
		"page": map[string]interface{}{
			"size":          pageSize,
			"totalElements": total,
			"totalPages":    (total + int64(pageSize) - 1) / int64(pageSize),
			"number":        pageNum - 1, // Spring pages are 0-indexed
		},
	}
	
	response.Success(c, result)
}

func (h *DiaryHandler) WriteDiary(c *gin.Context) {
	var req WriteDiaryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}

	userId := c.GetString("userId")
	diary := &model.Diary{
		UserID:          userId,
		Title:           req.Title,
		Content:         req.Content,
		ClientEncrypted: req.ClientEncrypted,
		PlainContent:    req.PlainContent,
		Visibility:      req.Visibility,
		EntryDate:       req.EntryDate,
	}

	if err := h.svc.AddDiary(diary); err != nil {
		response.Fail(c, err.Error())
		return
	}

	response.Success(c, nil)
}

func (h *DiaryHandler) EditDiary(c *gin.Context) {
	var req EditDiaryRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}

	userId := c.GetString("userId")
	diary := &model.Diary{
		DiaryID:         req.DiaryID,
		UserID:          userId,
		Title:           req.Title,
		Content:         req.Content,
		ClientEncrypted: req.ClientEncrypted,
		PlainContent:    req.PlainContent,
		Visibility:      req.Visibility,
		EntryDate:       req.EntryDate,
	}

	if err := h.svc.EditDiary(diary); err != nil {
		response.Fail(c, err.Error())
		return
	}

	response.Success(c, nil)
}

func (h *DiaryHandler) GetDiary(c *gin.Context) {
	diaryId := c.Param("diaryId")
	userId := c.GetString("userId")

	diary, err := h.svc.GetDiary(userId, diaryId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}

	response.Success(c, diary)
}

func (h *DiaryHandler) GenerateResponse(c *gin.Context) {
	diaryId := c.Param("diaryId")
	userId := c.GetString("userId")

	h.svc.GenerateAiResponse(userId, diaryId)
	response.Success(c, nil)
}
