package model

import "time"

type SoulMatch struct {
	ID          int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	UserAId     string    `gorm:"column:user_a_id" json:"userAId"`
	UserBId     string    `gorm:"column:user_b_id" json:"userBId"`
	LetterAtoB  string    `gorm:"column:letter_a_to_b;size:2000" json:"letterAtoB"`
	LetterBtoA  string    `gorm:"column:letter_b_to_a;size:2000" json:"letterBtoA"`
	StatusA     int       `gorm:"column:status_a" json:"statusA"`
	StatusB     int       `gorm:"column:status_b" json:"statusB"`
	IsMatched   bool      `gorm:"column:is_matched" json:"isMatched"`
	CreateTime  time.Time `gorm:"column:create_time" json:"createTime"`
	UpdateTime  time.Time `gorm:"column:update_time" json:"updateTime"`
}

func (SoulMatch) TableName() string {
	return "soul_match"
}
