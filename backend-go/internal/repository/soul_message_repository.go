package repository

import (
	"yusi-go/internal/config"
	"yusi-go/internal/model"
)

type SoulMessageRepository struct{}

func NewSoulMessageRepository() *SoulMessageRepository {
	return &SoulMessageRepository{}
}

func (r *SoulMessageRepository) Create(msg *model.SoulMessage) error {
	return config.DB.Create(msg).Error
}

func (r *SoulMessageRepository) FindByMatchId(matchId int64) ([]model.SoulMessage, error) {
	var msgs []model.SoulMessage
	err := config.DB.Where("match_id = ?", matchId).Order("create_time asc").Find(&msgs).Error
	return msgs, err
}

func (r *SoulMessageRepository) MarkAsRead(matchId int64, receiverId string) error {
	return config.DB.Model(&model.SoulMessage{}).
		Where("match_id = ? AND receiver_id = ? AND is_read = ?", matchId, receiverId, false).
		Update("is_read", true).Error
}

func (r *SoulMessageRepository) CountUnread(receiverId string) (int64, error) {
	var count int64
	err := config.DB.Model(&model.SoulMessage{}).
		Where("receiver_id = ? AND is_read = ?", receiverId, false).
		Count(&count).Error
	return count, err
}
