package repository

import (
	"yusi-go/internal/config"
	"yusi-go/internal/model"
)

type SoulMatchRepository struct{}

func NewSoulMatchRepository() *SoulMatchRepository {
	return &SoulMatchRepository{}
}

func (r *SoulMatchRepository) FindByUserAId(userId string) ([]model.SoulMatch, error) {
	var matches []model.SoulMatch
	err := config.DB.Where("user_a_id = ?", userId).Find(&matches).Error
	return matches, err
}

func (r *SoulMatchRepository) FindByUserBId(userId string) ([]model.SoulMatch, error) {
	var matches []model.SoulMatch
	err := config.DB.Where("user_b_id = ?", userId).Find(&matches).Error
	return matches, err
}

func (r *SoulMatchRepository) FindById(id int64) (*model.SoulMatch, error) {
	var match model.SoulMatch
	err := config.DB.First(&match, id).Error
	return &match, err
}

func (r *SoulMatchRepository) Update(match *model.SoulMatch) error {
	return config.DB.Save(match).Error
}
