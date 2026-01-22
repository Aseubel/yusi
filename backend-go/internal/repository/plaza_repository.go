package repository

import (
	"yusi-go/internal/config"
	"yusi-go/internal/model"
)

type SoulCardRepository struct{}

func NewSoulCardRepository() *SoulCardRepository {
	return &SoulCardRepository{}
}

func (r *SoulCardRepository) Create(card *model.SoulCard) error {
	return config.DB.Create(card).Error
}

func (r *SoulCardRepository) FindById(id int64) (*model.SoulCard, error) {
	var card model.SoulCard
	err := config.DB.First(&card, id).Error
	return &card, err
}

func (r *SoulCardRepository) Update(card *model.SoulCard) error {
	return config.DB.Save(card).Error
}

func (r *SoulCardRepository) GetFeed(userId string, page, size int, emotion string) ([]model.SoulCard, error) {
	var cards []model.SoulCard
	db := config.DB.Where("user_id != ?", userId)
	if emotion != "" && emotion != "All" {
		db = db.Where("emotion = ?", emotion)
	}
	offset := (page - 1) * size
	err := db.Order("created_at desc").Offset(offset).Limit(size).Find(&cards).Error
	return cards, err
}

type SoulResonanceRepository struct{}

func NewSoulResonanceRepository() *SoulResonanceRepository {
	return &SoulResonanceRepository{}
}

func (r *SoulResonanceRepository) Create(res *model.SoulResonance) error {
	return config.DB.Create(res).Error
}

func (r *SoulResonanceRepository) Exists(cardId int64, userId string) bool {
	var count int64
	config.DB.Model(&model.SoulResonance{}).Where("card_id = ? AND user_id = ?", cardId, userId).Count(&count)
	return count > 0
}

func (r *SoulResonanceRepository) FindByUserAndCards(userId string, cardIds []int64) ([]model.SoulResonance, error) {
	var res []model.SoulResonance
	err := config.DB.Where("user_id = ? AND card_id IN ?", userId, cardIds).Find(&res).Error
	return res, err
}
