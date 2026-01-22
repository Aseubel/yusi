package service

import (
	"crypto/rand"
	"encoding/base64"
	"errors"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"
)

type KeyManagementService struct {
	userRepo  *repository.UserRepository
	diaryRepo *repository.DiaryRepository
}

func NewKeyManagementService() *KeyManagementService {
	return &KeyManagementService{
		userRepo:  repository.NewUserRepository(),
		diaryRepo: repository.NewDiaryRepository(),
	}
}

const (
	KEY_MODE_DEFAULT = "DEFAULT"
	KEY_MODE_CUSTOM  = "CUSTOM"
)

var ADMIN_IDS = []string{"b98758ca6f4d4e7b"}

type KeySettingsResponse struct {
	KeyMode             string `json:"keyMode"`
	HasCloudBackup      bool   `json:"hasCloudBackup"`
	ServerKey           string `json:"serverKey,omitempty"`
	KeySalt             string `json:"keySalt,omitempty"`
	EncryptedBackupKey  string `json:"encryptedBackupKey,omitempty"`
}

type KeyModeUpdateRequest struct {
	KeyMode            string `json:"keyMode"`
	KeySalt            string `json:"keySalt"`
	EnableCloudBackup  *bool  `json:"enableCloudBackup"`
	EncryptedBackupKey string `json:"encryptedBackupKey"`
}

type DiaryReEncryptRequest struct {
	NewKeyMode         string             `json:"newKeyMode"`
	NewKeySalt         string             `json:"newKeySalt"`
	EnableCloudBackup  *bool              `json:"enableCloudBackup"`
	EncryptedBackupKey string             `json:"encryptedBackupKey"`
	Diaries            []ReEncryptedDiary `json:"diaries"`
}

type ReEncryptedDiary struct {
	DiaryID          string `json:"diaryId"`
	EncryptedContent string `json:"encryptedContent"`
	EncryptedTitle   string `json:"encryptedTitle"`
}

func (s *KeyManagementService) GetKeySettings(userId string) (*KeySettingsResponse, error) {
	user, err := s.userRepo.FindByUserId(userId)
	if err != nil {
		return nil, errors.New("用户不存在")
	}

	response := &KeySettingsResponse{
		KeyMode:        user.KeyMode,
		HasCloudBackup: user.HasCloudBackup,
	}

	if user.KeyMode == "" {
		user.KeyMode = KEY_MODE_DEFAULT
		response.KeyMode = KEY_MODE_DEFAULT
	}

	if user.KeyMode == KEY_MODE_DEFAULT {
		if user.ServerEncryptionKey == "" {
			newKey, _ := s.generateServerKey()
			user.ServerEncryptionKey = newKey
			s.userRepo.Update(user)
		}
		response.ServerKey = user.ServerEncryptionKey
	} else {
		response.KeySalt = user.KeySalt
	}

	return response, nil
}

func (s *KeyManagementService) UpdateKeyMode(userId string, req KeyModeUpdateRequest) error {
	user, err := s.userRepo.FindByUserId(userId)
	if err != nil {
		return errors.New("用户不存在")
	}

	if req.KeyMode != KEY_MODE_DEFAULT && req.KeyMode != KEY_MODE_CUSTOM {
		return errors.New("无效的密钥模式")
	}

	user.KeyMode = req.KeyMode

	if req.KeyMode == KEY_MODE_CUSTOM {
		user.KeySalt = req.KeySalt
		user.HasCloudBackup = req.EnableCloudBackup != nil && *req.EnableCloudBackup

		if user.HasCloudBackup {
			if req.EncryptedBackupKey == "" {
				return errors.New("开启云端备份时必须提供加密后的密钥")
			}
			user.EncryptedBackupKey = req.EncryptedBackupKey
		} else {
			user.EncryptedBackupKey = ""
		}
	} else {
		if user.ServerEncryptionKey == "" {
			key, _ := s.generateServerKey()
			user.ServerEncryptionKey = key
		}
		user.HasCloudBackup = false
		user.EncryptedBackupKey = ""
		user.KeySalt = ""
	}

	return s.userRepo.Update(user)
}

func (s *KeyManagementService) generateServerKey() (string, error) {
	key := make([]byte, 32)
	_, err := rand.Read(key)
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(key), nil
}

func (s *KeyManagementService) GetAllDiariesForReEncrypt(userId string) ([]model.Diary, error) {
	diaries, _, err := s.diaryRepo.FindByUserId(userId, 1, 10000, "create_time", true)
	return diaries, err
}

func (s *KeyManagementService) BatchUpdateReEncryptedDiaries(userId string, req DiaryReEncryptRequest) error {
	user, err := s.userRepo.FindByUserId(userId)
	if err != nil {
		return errors.New("用户不存在")
	}

	// Update user settings first
	user.KeyMode = req.NewKeyMode
	user.KeySalt = req.NewKeySalt
	user.HasCloudBackup = req.EnableCloudBackup != nil && *req.EnableCloudBackup
	
	if user.HasCloudBackup {
		user.EncryptedBackupKey = req.EncryptedBackupKey
	} else {
		user.EncryptedBackupKey = ""
	}

	if user.KeyMode == KEY_MODE_DEFAULT && user.ServerEncryptionKey == "" {
		key, _ := s.generateServerKey()
		user.ServerEncryptionKey = key
	}

	if err := s.userRepo.Update(user); err != nil {
		return err
	}

	// Update diaries
	for _, item := range req.Diaries {
		diary, err := s.diaryRepo.FindByDiaryId(userId, item.DiaryID)
		if err == nil {
			diary.Content = item.EncryptedContent
			if item.EncryptedTitle != "" {
				diary.Title = item.EncryptedTitle
			}
			s.diaryRepo.Update(diary)
		}
	}
	
	return nil
}

func (s *KeyManagementService) GetBackupKeyForRecovery(adminUserId, targetUserId string) (string, error) {
	isAdmin := false
	for _, id := range ADMIN_IDS {
		if id == adminUserId {
			isAdmin = true
			break
		}
	}
	if !isAdmin {
		return "", errors.New("无权限：仅管理员可访问备份密钥")
	}

	targetUser, err := s.userRepo.FindByUserId(targetUserId)
	if err != nil {
		return "", errors.New("目标用户不存在")
	}

	if !targetUser.HasCloudBackup {
		return "", errors.New("该用户未开启云端密钥备份")
	}

	if targetUser.EncryptedBackupKey == "" {
		return "", errors.New("未找到备份密钥")
	}

	return targetUser.EncryptedBackupKey, nil
}
