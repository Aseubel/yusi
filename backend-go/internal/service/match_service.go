package service

import (
	"time"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"
)

type MatchService struct {
	matchRepo *repository.SoulMatchRepository
	userRepo  *repository.UserRepository
}

func NewMatchService() *MatchService {
	return &MatchService{
		matchRepo: repository.NewSoulMatchRepository(),
		userRepo:  repository.NewUserRepository(),
	}
}

func (s *MatchService) UpdateSettings(userId string, enabled bool, intent string) (*model.User, error) {
	user, err := s.userRepo.FindByUserId(userId)
	if err != nil {
		return nil, err
	}
	user.IsMatchEnabled = enabled
	user.MatchIntent = intent
	s.userRepo.Update(user)
	return user, nil
}

func (s *MatchService) GetMatches(userId string) ([]model.SoulMatch, error) {
	matchesA, _ := s.matchRepo.FindByUserAId(userId)
	matchesB, _ := s.matchRepo.FindByUserBId(userId)
	return append(matchesA, matchesB...), nil
}

func (s *MatchService) HandleAction(userId string, matchId int64, action int) (*model.SoulMatch, error) {
	match, err := s.matchRepo.FindById(matchId)
	if err != nil {
		return nil, err
	}
	
	if userId == match.UserAId {
		match.StatusA = action
	} else if userId == match.UserBId {
		match.StatusB = action
	} else {
		return nil, nil
	}
	
	if match.StatusA == 1 && match.StatusB == 1 {
		match.IsMatched = true
	}
	
	match.UpdateTime = time.Now()
	err = s.matchRepo.Update(match)
	return match, err
}
