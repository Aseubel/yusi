package service

import (
	"log"
	"time"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"

	"github.com/google/uuid"
)

type DiaryService struct {
	repo *repository.DiaryRepository
}

func NewDiaryService() *DiaryService {
	return &DiaryService{
		repo: repository.NewDiaryRepository(),
	}
}

func (s *DiaryService) AddDiary(diary *model.Diary) error {
	diary.DiaryID = uuid.New().String()
	diary.CreateTime = time.Now()
	diary.UpdateTime = time.Now()
	
	err := s.repo.Create(diary)
	if err != nil {
		return err
	}
	
	// Async event processing (e.g., AI analysis trigger)
	// In a real app, send to a channel here
	go s.processDiaryEvent(diary, "WRITE")
	
	return nil
}

func (s *DiaryService) EditDiary(diary *model.Diary) error {
	existing, err := s.repo.FindByDiaryId(diary.UserID, diary.DiaryID)
	if err != nil {
		return err
	}
	
	diary.ID = existing.ID
	diary.CreateTime = existing.CreateTime
	diary.UpdateTime = time.Now()
	diary.Status = 0 // Reset status
	diary.AiResponse = ""
	
	err = s.repo.Update(diary)
	if err != nil {
		return err
	}

	go s.processDiaryEvent(diary, "MODIFY")
	
	return nil
}

func (s *DiaryService) GetDiary(userId, diaryId string) (*model.Diary, error) {
	diary, err := s.repo.FindByDiaryId(userId, diaryId)
	if err == nil {
		go s.processDiaryEvent(diary, "READ")
	}
	return diary, err
}

func (s *DiaryService) GetDiaryList(userId string, page, size int, sortBy string, asc bool) ([]model.Diary, int64, error) {
	if sortBy == "" {
		sortBy = "create_time"
	}
	return s.repo.FindByUserId(userId, page, size, sortBy, asc)
}

func (s *DiaryService) processDiaryEvent(diary *model.Diary, eventType string) {
	// This replaces Disruptor consumer logic
	log.Printf("Processing diary event: %s for diary %s", eventType, diary.DiaryID)
	// If WRITE or MODIFY, trigger AI analysis if configured
}

func (s *DiaryService) GenerateAiResponse(userId, diaryId string) error {
	// Trigger AI generation (async)
	// Logic to call LLM and update diary
	return nil
}
