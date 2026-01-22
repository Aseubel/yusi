package api

import (
	"yusi-go/internal/service"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
)

type SituationRoomHandler struct {
	svc *service.SituationRoomService
}

func NewSituationRoomHandler() *SituationRoomHandler {
	return &SituationRoomHandler{
		svc: service.NewSituationRoomService(),
	}
}

type CreateRoomRequest struct {
	OwnerId    string `json:"ownerId"`
	MaxMembers int    `json:"maxMembers"`
}

type JoinRoomRequest struct {
	Code   string `json:"code"`
	UserId string `json:"userId"`
}

type StartRoomRequest struct {
	Code       string `json:"code"`
	ScenarioId string `json:"scenarioId"`
	OwnerId    string `json:"ownerId"`
}

type SubmitNarrativeRequest struct {
	Code      string `json:"code"`
	UserId    string `json:"userId"`
	Narrative string `json:"narrative"`
	IsPublic  bool   `json:"isPublic"`
}

type SubmitScenarioRequest struct {
	Title       string `json:"title"`
	Description string `json:"description"`
}

type ReviewScenarioRequest struct {
	ScenarioId   string `json:"scenarioId"`
	Status       int    `json:"status"`
	RejectReason string `json:"rejectReason"`
}

func (h *SituationRoomHandler) Create(c *gin.Context) {
	var req CreateRoomRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	room, err := h.svc.CreateRoom(req.OwnerId, req.MaxMembers)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, room)
}

func (h *SituationRoomHandler) Join(c *gin.Context) {
	var req JoinRoomRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	room, err := h.svc.JoinRoom(req.Code, req.UserId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, room)
}

func (h *SituationRoomHandler) Start(c *gin.Context) {
	var req StartRoomRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	room, err := h.svc.StartRoom(req.Code, req.ScenarioId, req.OwnerId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, room)
}

func (h *SituationRoomHandler) Submit(c *gin.Context) {
	var req SubmitNarrativeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	room, err := h.svc.Submit(req.Code, req.UserId, req.Narrative, req.IsPublic)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, room)
}

func (h *SituationRoomHandler) GetRoom(c *gin.Context) {
	code := c.Param("code")
	userId := c.GetString("userId")
	room, err := h.svc.GetRoom(code, userId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, room)
}

func (h *SituationRoomHandler) GetScenarios(c *gin.Context) {
	scenarios, err := h.svc.GetScenarios()
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, scenarios)
}

func (h *SituationRoomHandler) SubmitScenario(c *gin.Context) {
	var req SubmitScenarioRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	scenario, err := h.svc.SubmitScenario(userId, req.Title, req.Description)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, scenario)
}

func (h *SituationRoomHandler) Cancel(c *gin.Context) {
	var req JoinRoomRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	err := h.svc.CancelRoom(req.Code, req.UserId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, nil)
}

func (h *SituationRoomHandler) VoteCancel(c *gin.Context) {
	var req JoinRoomRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	room, err := h.svc.VoteCancel(req.Code, req.UserId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, room)
}

func (h *SituationRoomHandler) GetHistory(c *gin.Context) {
	userId := c.GetString("userId")
	rooms, err := h.svc.GetHistory(userId)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, rooms)
}

func (h *SituationRoomHandler) GetReport(c *gin.Context) {
	code := c.Param("code")
	report, err := h.svc.GetReport(code)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, report)
}

func (h *SituationRoomHandler) ReviewScenario(c *gin.Context) {
	var req ReviewScenarioRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, "参数错误")
		return
	}
	userId := c.GetString("userId")
	scenario, err := h.svc.ReviewScenario(userId, req.ScenarioId, req.Status, req.RejectReason)
	if err != nil {
		response.Fail(c, err.Error())
		return
	}
	response.Success(c, scenario)
}
