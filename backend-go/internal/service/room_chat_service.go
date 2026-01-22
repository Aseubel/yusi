package service

import (
	"errors"
	"time"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"
)

type RoomChatService struct {
	msgRepo  *repository.RoomMessageRepository
	roomRepo *repository.SituationRoomRepository
	userRepo *repository.UserRepository
}

func NewRoomChatService() *RoomChatService {
	return &RoomChatService{
		msgRepo:  repository.NewRoomMessageRepository(),
		roomRepo: repository.NewSituationRoomRepository(),
		userRepo: repository.NewUserRepository(),
	}
}

func (s *RoomChatService) SendMessage(roomCode, userId, content string) (*model.RoomMessage, error) {
	room, err := s.roomRepo.FindById(roomCode)
	if err != nil {
		return nil, errors.New("房间不存在")
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
	
	// Check status (simplified)
	if room.Status == "COMPLETED" || room.Status == "CANCELLED" {
		return nil, errors.New("房间已结束")
	}
	
	user, _ := s.userRepo.FindByUserId(userId)
	senderName := "Unknown"
	if user != nil {
		senderName = user.UserName
	}
	
	msg := &model.RoomMessage{
		RoomCode:   roomCode,
		SenderID:   userId,
		SenderName: senderName,
		Content:    content,
		CreatedAt:  time.Now(),
	}
	
	err = s.msgRepo.Create(msg)
	return msg, err
}

func (s *RoomChatService) GetHistory(roomCode, userId string) ([]model.RoomMessage, error) {
	return s.msgRepo.FindByRoomCode(roomCode)
}

func (s *RoomChatService) PollMessages(roomCode, userId string, afterStr string) ([]model.RoomMessage, error) {
	if afterStr == "" {
		// Return recent
		// Or all? Java returns recent 50 reversed.
		// Let's return all for now or recent 50
		return s.msgRepo.FindByRoomCode(roomCode)
	}
	
	afterTime, err := time.Parse(time.RFC3339, afterStr)
	if err != nil {
		// Try standard format or ignore
		return nil, err
	}
	
	return s.msgRepo.FindAfter(roomCode, afterTime)
}
