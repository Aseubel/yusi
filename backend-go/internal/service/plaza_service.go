package service

import (
	"errors"
	"math"
	"sort"
	"time"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"
)

type SoulPlazaService struct {
	cardRepo *repository.SoulCardRepository
	resRepo  *repository.SoulResonanceRepository
}

func NewSoulPlazaService() *SoulPlazaService {
	return &SoulPlazaService{
		cardRepo: repository.NewSoulCardRepository(),
		resRepo:  repository.NewSoulResonanceRepository(),
	}
}

func (s *SoulPlazaService) Submit(userId, content, originId, cardType string) (*model.SoulCard, error) {
	if len(content) < 5 {
		return nil, errors.New("内容太短")
	}
	
	// Mock emotion analysis
	emotion := "Neutral"
	
	card := &model.SoulCard{
		UserID:         userId,
		Content:        content,
		OriginID:       originId,
		Type:           cardType,
		Emotion:        emotion,
		ResonanceCount: 0,
		CreatedAt:      time.Now(),
	}
	
	err := s.cardRepo.Create(card)
	return card, err
}

func (s *SoulPlazaService) GetFeed(userId string, page, size int, emotion string) (map[string]interface{}, error) {
	var cards []model.SoulCard
	var err error
	
	// If specific emotion filter, use DB filtering
	if emotion != "" && emotion != "All" {
		cards, err = s.cardRepo.GetFeed(userId, page, size, emotion)
		if err != nil {
			return nil, err
		}
	} else {
		// Complex sorting algorithm
		cards, err = s.getSoulMatchedFeed(userId, page, size)
		if err != nil {
			return nil, err
		}
	}
	
	// Check resonances
	if len(cards) > 0 {
		var ids []int64
		for _, c := range cards {
			ids = append(ids, c.ID)
		}
		res, _ := s.resRepo.FindByUserAndCards(userId, ids)
		resMap := make(map[int64]bool)
		for _, r := range res {
			resMap[r.CardID] = true
		}
		
		for i := range cards {
			if _, ok := resMap[cards[i].ID]; ok {
				cards[i].IsResonated = true
			}
		}
	}
	
	// Mock page object
	result := map[string]interface{}{
		"content": cards,
		"page": map[string]interface{}{
			"size": size,
			"number": page - 1,
		},
	}
	return result, nil
}

func (s *SoulPlazaService) getSoulMatchedFeed(userId string, page, size int) ([]model.SoulCard, error) {
	// 1. Fetch larger candidate set (e.g. 3x page size or 100)
	fetchSize := size * 3
	if fetchSize < 100 {
		fetchSize = 100
	}
	
	// Use repo to fetch candidates (sorted by time desc by default)
	candidates, err := s.cardRepo.GetFeed(userId, 1, fetchSize, "")
	if err != nil {
		return nil, err
	}
	
	if len(candidates) == 0 {
		return []model.SoulCard{}, nil
	}
	
	// 2. Get User Preferences (Resonated Emotions)
	// For MVP, skip or mock preferences
	// In Java: fetched user resonances -> preferred emotions set
	
	// 3. Score and Sort
	now := time.Now()
	
	sort.Slice(candidates, func(i, j int) bool {
		scoreI := s.calculateScore(candidates[i], now)
		scoreJ := s.calculateScore(candidates[j], now)
		return scoreI > scoreJ
	})
	
	// 4. Paginate
	start := (page - 1) * size
	if start >= len(candidates) {
		return []model.SoulCard{}, nil
	}
	end := start + size
	if end > len(candidates) {
		end = len(candidates)
	}
	
	return candidates[start:end], nil
}

func (s *SoulPlazaService) calculateScore(card model.SoulCard, now time.Time) float64 {
	// Popularity Score: log(1 + resonance) * 10
	popularityScore := math.Log1p(float64(card.ResonanceCount)) * 10
	
	// Time Decay: 100 * exp(-hours / 72.0)
	hoursAgo := now.Sub(card.CreatedAt).Hours()
	timeScore := 100 * math.Exp(-hoursAgo/72.0)
	
	// Affinity: 1.0 default (skip impl for now)
	affinity := 1.0
	
	return (popularityScore + timeScore) * affinity
}


func (s *SoulPlazaService) Resonate(userId string, cardId int64, resType string) (*model.SoulResonance, error) {
	card, err := s.cardRepo.FindById(cardId)
	if err != nil {
		return nil, errors.New("Card not found")
	}
	if card.UserID == userId {
		return nil, errors.New("不能与自己共鸣")
	}
	if s.resRepo.Exists(cardId, userId) {
		return nil, errors.New("已经共鸣过了")
	}
	
	res := &model.SoulResonance{
		CardID:    cardId,
		UserID:    userId,
		Type:      resType,
		CreatedAt: time.Now(),
	}
	
	err = s.resRepo.Create(res)
	if err == nil {
		card.ResonanceCount++
		s.cardRepo.Update(card)
	}
	return res, err
}
