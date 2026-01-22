package repository

import (
	"time"
	"yusi-go/internal/config"
	"yusi-go/internal/model"
)

type RoomMessageRepository struct{}

func NewRoomMessageRepository() *RoomMessageRepository {
	return &RoomMessageRepository{}
}

func (r *RoomMessageRepository) Create(msg *model.RoomMessage) error {
	return config.DB.Create(msg).Error
}

func (r *RoomMessageRepository) FindByRoomCode(roomCode string) ([]model.RoomMessage, error) {
	var msgs []model.RoomMessage
	err := config.DB.Where("room_code = ?", roomCode).Order("created_at asc").Find(&msgs).Error
	return msgs, err
}

func (r *RoomMessageRepository) FindAfter(roomCode string, after time.Time) ([]model.RoomMessage, error) {
	var msgs []model.RoomMessage
	err := config.DB.Where("room_code = ? AND created_at > ?", roomCode, after).Order("created_at asc").Find(&msgs).Error
	return msgs, err
}
