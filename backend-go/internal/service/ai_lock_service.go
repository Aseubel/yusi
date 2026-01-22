package service

import (
	"context"
	"fmt"
	"time"
	"yusi-go/internal/config"
)

type AiLockService struct{}

func NewAiLockService() *AiLockService {
	return &AiLockService{}
}

func (s *AiLockService) TryAcquireLock(userId string) bool {
	key := fmt.Sprintf("ai_lock:%s", userId)
	// Set lock with 3 minute expiration (matching Java code)
	success, err := config.RDB.SetNX(context.Background(), key, "locked", 3*time.Minute).Result()
	if err != nil {
		return false
	}
	return success
}

func (s *AiLockService) ReleaseLock(userId string) {
	key := fmt.Sprintf("ai_lock:%s", userId)
	config.RDB.Del(context.Background(), key)
}
