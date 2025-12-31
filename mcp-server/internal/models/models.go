package models

import (
	"time"
)

// User represents the user table in database
type User struct {
	ID          uint   `gorm:"primaryKey;autoIncrement"`
	UserID      string `gorm:"column:user_id;type:varchar(64);uniqueIndex;not null"`
	Username    string `gorm:"column:username;type:varchar(128);not null"`
	MatchIntent string `gorm:"column:match_intent;type:text"`
}

// TableName specifies the table name for User
func (User) TableName() string {
	return "user"
}

// Diary represents the diary table in database
// Matches Java's Diary entity with all relevant fields
type Diary struct {
	ID               uint       `gorm:"primaryKey;autoIncrement"`
	DiaryID          string     `gorm:"column:diary_id;type:varchar(64);uniqueIndex;not null"`
	UserID           string     `gorm:"column:user_id;type:varchar(64);index;not null"`
	Title            string     `gorm:"column:title;type:varchar(255)"`
	Content          string     `gorm:"column:content;type:text"` // Encrypted content in DB using AES-GCM
	Visibility       *bool      `gorm:"column:visibility;type:tinyint(1)"`
	EntryDate        *time.Time `gorm:"column:entry_date;type:date;index"`
	AIAnalysisStatus *int       `gorm:"column:ai_analysis_status;type:int"`
	AIResponse       string     `gorm:"column:ai_response;type:varchar(1000)"`
	CreateTime       *time.Time `gorm:"column:create_time;type:datetime"`
	UpdateTime       *time.Time `gorm:"column:update_time;type:datetime"`
}

// TableName specifies the table name for Diary
func (Diary) TableName() string {
	return "diary"
}

// SoulMatch represents the soul_match table in database
type SoulMatch struct {
	ID        uint   `gorm:"primaryKey;autoIncrement"`
	UserAID   string `gorm:"column:user_a_id;type:varchar(64);index;not null"`
	UserBID   string `gorm:"column:user_b_id;type:varchar(64);index;not null"`
	IsMatched bool   `gorm:"column:is_matched;type:tinyint(1);default:0"`
}

// TableName specifies the table name for SoulMatch
func (SoulMatch) TableName() string {
	return "soul_match"
}

// DiaryResponse represents the response structure for diary data
// Used for MCP tool responses to LLM
type DiaryResponse struct {
	DiaryID   string `json:"diary_id"`
	Title     string `json:"title,omitempty"`
	Content   string `json:"content"` // Decrypted content
	EntryDate string `json:"entry_date"`
}

// MatchStatusResponse represents the response structure for match status
type MatchStatusResponse struct {
	UserAID   string `json:"user_a_id"`
	UserBID   string `json:"user_b_id"`
	IsMatched bool   `json:"is_matched"`
	Message   string `json:"message"`
}
