package model

import "time"

type User struct {
	ID                  int64     `gorm:"primaryKey;autoIncrement" json:"id"`
	UserID              string    `gorm:"column:user_id;uniqueIndex;size:64" json:"userId"`
	UserName            string    `gorm:"column:username;size:64" json:"userName"`
	Password            string    `gorm:"column:password;size:255" json:"-"` // Don't return password
	Email               string    `gorm:"column:email;size:128" json:"email"`
	IsMatchEnabled      bool      `gorm:"column:is_match_enabled;default:false" json:"isMatchEnabled"`
	MatchIntent         string    `gorm:"column:match_intent;size:255" json:"matchIntent"`
	KeyMode             string    `gorm:"column:key_mode;default:DEFAULT;size:32" json:"keyMode"`
	HasCloudBackup      bool      `gorm:"column:has_cloud_backup;default:false" json:"hasCloudBackup"`
	EncryptedBackupKey  string    `gorm:"column:encrypted_backup_key;size:1024" json:"encryptedBackupKey"`
	KeySalt             string    `gorm:"column:key_salt;size:255" json:"keySalt"`
	ServerEncryptionKey string    `gorm:"column:server_encryption_key;size:255" json:"serverEncryptionKey"`
	CreatedAt           time.Time `gorm:"column:create_time;autoCreateTime" json:"createTime"`
	UpdatedAt           time.Time `gorm:"column:update_time;autoUpdateTime" json:"updateTime"`
}

func (User) TableName() string {
	return "user"
}
