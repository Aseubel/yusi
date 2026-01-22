package model

import "time"

type Diary struct {
	ID              int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	DiaryID         string    `gorm:"column:diary_id;uniqueIndex;size:64" json:"diaryId"`
	UserID          string    `gorm:"column:user_id;index;size:64" json:"userId"`
	Title           string    `gorm:"column:title;size:255" json:"title"`
	Content         string    `gorm:"column:content;type:text" json:"content"`
	ClientEncrypted bool      `gorm:"column:client_encrypted" json:"clientEncrypted"`
	PlainContent    string    `gorm:"-" json:"plainContent,omitempty"` // Transient field
	Visibility      bool      `gorm:"column:visibility" json:"visibility"`
	EntryDate       string    `gorm:"column:entry_date;type:date" json:"entryDate"` // Using string for date YYYY-MM-DD
	Status          int       `gorm:"column:ai_analysis_status" json:"status"`
	AiResponse      string    `gorm:"column:ai_response;size:1000" json:"aiResponse"`
	CreateTime      time.Time `gorm:"column:create_time;autoCreateTime" json:"createTime"`
	UpdateTime      time.Time `gorm:"column:update_time;autoUpdateTime" json:"updateTime"`
}

// TableName is not used directly for Diary because of sharding,
// but GORM needs a default.
func (Diary) TableName() string {
	return "diary"
}
