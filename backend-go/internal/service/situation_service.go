package service

import (
	"errors"
	"math/rand"
	"time"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"

	"github.com/google/uuid"
)

type SituationRoomService struct {
	roomRepo     *repository.SituationRoomRepository
	scenarioRepo *repository.SituationScenarioRepository
	userRepo     *repository.UserRepository
}

func NewSituationRoomService() *SituationRoomService {
	return &SituationRoomService{
		roomRepo:     repository.NewSituationRoomRepository(),
		scenarioRepo: repository.NewSituationScenarioRepository(),
		userRepo:     repository.NewUserRepository(),
	}
}

const (
	ROOM_STATUS_WAITING     = "WAITING"
	ROOM_STATUS_IN_PROGRESS = "IN_PROGRESS"
	ROOM_STATUS_COMPLETED   = "COMPLETED"
	ROOM_STATUS_CANCELLED   = "CANCELLED"
)

func (s *SituationRoomService) CreateRoom(ownerId string, maxMembers int) (*model.SituationRoom, error) {
	if s.scenarioRepo.Count() == 0 {
		return nil, errors.New("暂无情景可用，请联系管理员添加")
	}
	code := s.generateCode()
	room := &model.SituationRoom{
		Code:                 code,
		Status:               ROOM_STATUS_WAITING,
		OwnerID:              ownerId,
		Members:              []string{ownerId},
		Submissions:          make(map[string]string),
		SubmissionVisibility: make(map[string]bool),
		CreatedAt:            time.Now(),
	}
	err := s.roomRepo.Create(room)
	return room, err
}

func (s *SituationRoomService) JoinRoom(code, userId string) (*model.SituationRoom, error) {
	room, err := s.roomRepo.FindById(code)
	if err != nil {
		return nil, errors.New("房间不存在")
	}
	if room.Status != ROOM_STATUS_WAITING {
		return nil, errors.New("房间不可加入")
	}
	if len(room.Members) >= 8 {
		return nil, errors.New("人数已满")
	}
	
	// Check if already joined
	for _, m := range room.Members {
		if m == userId {
			return room, nil
		}
	}
	
	room.Members = append(room.Members, userId)
	err = s.roomRepo.Update(room)
	return room, err
}

func (s *SituationRoomService) StartRoom(code, scenarioId, ownerId string) (*model.SituationRoom, error) {
	room, err := s.roomRepo.FindById(code)
	if err != nil {
		return nil, err
	}
	if room.Status != ROOM_STATUS_WAITING {
		return nil, errors.New("房间状态错误")
	}
	if room.OwnerID != ownerId {
		return nil, errors.New("非房主")
	}
	if len(room.Members) < 2 {
		return nil, errors.New("至少2人")
	}
	
	room.ScenarioID = scenarioId
	room.Status = ROOM_STATUS_IN_PROGRESS
	err = s.roomRepo.Update(room)
	return room, err
}

func (s *SituationRoomService) Submit(code, userId, narrative string, isPublic bool) (*model.SituationRoom, error) {
	room, err := s.roomRepo.FindById(code)
	if err != nil {
		return nil, err
	}
	
	if room.Status != ROOM_STATUS_IN_PROGRESS {
		return nil, errors.New("未开始或已结束")
	}
	
	isMember := false
	for _, m := range room.Members {
		if m == userId {
			isMember = true
			break
		}
	}
	if !isMember {
		return nil, errors.New("非房间成员")
	}
	
	if _, ok := room.Submissions[userId]; ok {
		return nil, errors.New("已提交")
	}
	
	if len(narrative) > 1000 {
		return nil, errors.New("叙事长度不合法")
	}
	
	if room.Submissions == nil {
		room.Submissions = make(map[string]string)
	}
	room.Submissions[userId] = narrative
	
	if room.SubmissionVisibility == nil {
		room.SubmissionVisibility = make(map[string]bool)
	}
	room.SubmissionVisibility[userId] = isPublic
	
	if len(room.Submissions) == len(room.Members) && len(room.Members) >= 2 {
		room.Status = ROOM_STATUS_COMPLETED
		// Trigger Async Analysis here
		// go s.analyzeReport(room)
	}
	
	err = s.roomRepo.Update(room)
	return room, err
}

func (s *SituationRoomService) GetRoom(code, userId string) (*model.SituationRoom, error) {
	room, err := s.roomRepo.FindById(code)
	if err != nil {
		return nil, err
	}
	
	// Populate extra data
	s.populateMemberNames(room)
	if room.ScenarioID != "" {
		scenario, _ := s.scenarioRepo.FindById(room.ScenarioID)
		room.Scenario = scenario
	}
	
	// Mask submissions
	maskedSubmissions := make(map[string]string)
	if room.Submissions != nil {
		for k, v := range room.Submissions {
			if k == userId {
				maskedSubmissions[k] = v
			} else {
				maskedSubmissions[k] = "" // Masked
			}
		}
	}
	room.Submissions = maskedSubmissions
	
	return room, nil
}

func (s *SituationRoomService) CancelRoom(code, userId string) error {
	room, err := s.roomRepo.FindById(code)
	if err != nil {
		return errors.New("房间不存在")
	}
	if room.OwnerID != userId {
		return errors.New("非房主不可解散房间")
	}
	room.Status = ROOM_STATUS_CANCELLED
	return s.roomRepo.Update(room)
}

func (s *SituationRoomService) VoteCancel(code, userId string) (*model.SituationRoom, error) {
	room, err := s.roomRepo.FindById(code)
	if err != nil {
		return nil, errors.New("房间不存在")
	}
	if room.Status != ROOM_STATUS_IN_PROGRESS {
		return nil, errors.New("房间未进行中，无法投票解散")
	}
	
	isMember := false
	for _, m := range room.Members {
		if m == userId {
			isMember = true
			break
		}
	}
	if !isMember {
		return nil, errors.New("非房间成员")
	}
	
	if room.CancelVotes == nil {
		room.CancelVotes = make([]string, 0)
	}
	
	hasVoted := false
	for _, v := range room.CancelVotes {
		if v == userId {
			hasVoted = true
			break
		}
	}
	
	if !hasVoted {
		room.CancelVotes = append(room.CancelVotes, userId)
	}
	
	if len(room.CancelVotes) > len(room.Members)/2 {
		room.Status = ROOM_STATUS_CANCELLED
	}
	
	err = s.roomRepo.Update(room)
	return room, err
}

func (s *SituationRoomService) GetHistory(userId string) ([]model.SituationRoom, error) {
	rooms, err := s.roomRepo.FindByMember(userId)
	if err != nil {
		return nil, err
	}
	// Mask submissions
	for i := range rooms {
		maskedSubmissions := make(map[string]string)
		if rooms[i].Submissions != nil {
			for k, v := range rooms[i].Submissions {
				if k == userId {
					maskedSubmissions[k] = v
				} else {
					maskedSubmissions[k] = ""
				}
			}
		}
		rooms[i].Submissions = maskedSubmissions
	}
	return rooms, nil
}

func (s *SituationRoomService) GetReport(code string) (*model.SituationReport, error) {
	room, err := s.roomRepo.FindById(code)
	if err != nil {
		return nil, errors.New("房间不存在")
	}
	if room.Status != ROOM_STATUS_COMPLETED {
		return nil, errors.New("未完成")
	}
	if room.Report == nil {
		// Mock report generation
		return nil, errors.New("报告尚未生成")
	}
	return (*model.SituationReport)(room.Report), nil
}

func (s *SituationRoomService) ReviewScenario(adminId, scenarioId string, status int, rejectReason string) (*model.SituationScenario, error) {
	// Simple admin check
	if adminId != "b98758ca6f4d4e7b" {
		return nil, errors.New("无权限")
	}
	
	scenario, err := s.scenarioRepo.FindById(scenarioId)
	if err != nil {
		return nil, errors.New("Scenario not found")
	}
	
	scenario.Status = status
	if status == 1 || status == 2 {
		scenario.RejectReason = rejectReason
	}
	
	err = s.scenarioRepo.Update(scenario)
	return scenario, err
}

func (s *SituationRoomService) populateMemberNames(room *model.SituationRoom) {
	room.MemberNames = make(map[string]string)
	for _, uid := range room.Members {
		user, err := s.userRepo.FindByUserId(uid)
		if err == nil {
			room.MemberNames[uid] = user.UserName
		} else {
			room.MemberNames[uid] = "未知用户"
		}
	}
}

func (s *SituationRoomService) generateCode() string {
	letters := "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	b := make([]byte, 6)
	for i := range b {
		b[i] = letters[rand.Intn(len(letters))]
	}
	code := string(b)
	if s.roomRepo.ExistsById(code) {
		return s.generateCode()
	}
	return code
}

func (s *SituationRoomService) GetScenarios() ([]model.SituationScenario, error) {
	return s.scenarioRepo.FindByStatusGreaterThanEqual(3)
}

func (s *SituationRoomService) SubmitScenario(userId, title, description string) (*model.SituationScenario, error) {
	scenario := &model.SituationScenario{
		ID:          uuid.New().String(),
		Title:       title,
		Description: description,
		SubmitterID: userId,
		Status:      0,
	}
	err := s.scenarioRepo.Create(scenario)
	return scenario, err
}
