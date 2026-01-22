package model

import "time"

type RoomMessage struct {
	ID         int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	RoomCode   string    `gorm:"column:room_code;size:32;index" json:"roomCode"`
	SenderID   string    `gorm:"column:sender_id;size:64" json:"senderId"`
	SenderName string    `gorm:"column:sender_name;size:64" json:"senderName"`
	Content    string    `gorm:"type:text" json:"content"`
	CreatedAt  time.Time `gorm:"column:created_at;index" json:"createdAt"`
}

func (RoomMessage) TableName() string {
	return "room_message"
}
