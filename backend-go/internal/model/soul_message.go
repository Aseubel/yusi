package model

import "time"

type SoulMessage struct {
	ID         int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	MatchID    int64     `gorm:"column:match_id;index" json:"matchId"`
	SenderID   string    `gorm:"column:sender_id;size:64;index" json:"senderId"`
	ReceiverID string    `gorm:"column:receiver_id;size:64;index" json:"receiverId"`
	Content    string    `gorm:"size:1000" json:"content"`
	IsRead     bool      `gorm:"column:is_read;default:false" json:"isRead"`
	CreateTime time.Time `gorm:"column:create_time;index" json:"createTime"`
}

func (SoulMessage) TableName() string {
	return "soul_message"
}
