package service

import (
	"errors"
	"time"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"
)

type SoulChatService struct {
	msgRepo   *repository.SoulMessageRepository
	matchRepo *repository.SoulMatchRepository
}

func NewSoulChatService() *SoulChatService {
	return &SoulChatService{
		msgRepo:   repository.NewSoulMessageRepository(),
		matchRepo: repository.NewSoulMatchRepository(),
	}
}

func (s *SoulChatService) SendMessage(userId string, matchId int64, content string) (*model.SoulMessage, error) {
	match, err := s.matchRepo.FindById(matchId)
	if err != nil {
		return nil, errors.New("匹配不存在")
	}

	if !match.IsMatched {
		return nil, errors.New("尚未建立连接，无法发送消息")
	}

	var receiverId string
	if userId == match.UserAId {
		receiverId = match.UserBId
	} else if userId == match.UserBId {
		receiverId = match.UserAId
	} else {
		return nil, errors.New("无权发送消息")
	}

	msg := &model.SoulMessage{
		MatchID:    matchId,
		SenderID:   userId,
		ReceiverID: receiverId,
		Content:    content,
		IsRead:     false,
		CreateTime: time.Now(),
	}

	err = s.msgRepo.Create(msg)
	return msg, err
}

func (s *SoulChatService) GetHistory(userId string, matchId int64) ([]model.SoulMessage, error) {
	match, err := s.matchRepo.FindById(matchId)
	if err != nil {
		return nil, errors.New("匹配不存在")
	}

	if userId != match.UserAId && userId != match.UserBId {
		return nil, errors.New("无权查看消息")
	}

	return s.msgRepo.FindByMatchId(matchId)
}

func (s *SoulChatService) MarkAsRead(userId string, matchId int64) error {
	return s.msgRepo.MarkAsRead(matchId, userId)
}

func (s *SoulChatService) GetUnreadCount(userId string) (int64, error) {
	return s.msgRepo.CountUnread(userId)
}
