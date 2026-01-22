package model

import "time"

type SoulCard struct {
	ID             int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	Content        string    `gorm:"type:text" json:"content"`
	OriginID       string    `gorm:"column:origin_id" json:"originId"`
	UserID         string    `gorm:"column:user_id" json:"userId"`
	Type           string    `gorm:"size:20" json:"type"` // DIARY, SCENARIO
	Emotion        string    `gorm:"size:50" json:"emotion"`
	ResonanceCount int       `gorm:"column:resonance_count;default:0" json:"resonanceCount"`
	CreatedAt      time.Time `gorm:"column:created_at" json:"createdAt"`
	
	IsResonated bool `gorm:"-" json:"isResonated"`
}

func (SoulCard) TableName() string {
	return "soul_card"
}

type SoulResonance struct {
	ID        int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	CardID    int64     `gorm:"column:card_id" json:"cardId"`
	UserID    string    `gorm:"column:user_id" json:"userId"`
	Type      string    `gorm:"size:20" json:"type"` // LIKE, EMPATHY, SAME_FEELING
	CreatedAt time.Time `gorm:"column:created_at" json:"createdAt"`
}

func (SoulResonance) TableName() string {
	return "soul_resonance"
}
