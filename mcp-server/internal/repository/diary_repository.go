package repository

import (
	"context"
	"errors"
	"log/slog"

	"github.com/aseubel/yusi-mcp-server/internal/database"
	"github.com/aseubel/yusi-mcp-server/internal/models"
	"gorm.io/gorm"
)

var (
	ErrDiaryNotFound = errors.New("diary not found")
	ErrUserNotFound  = errors.New("user not found")
)

// DiaryRepository handles diary-related database operations
type DiaryRepository struct {
	db *database.DB
}

// NewDiaryRepository creates a new DiaryRepository
func NewDiaryRepository(db *database.DB) *DiaryRepository {
	return &DiaryRepository{db: db}
}

// GetRecentDiariesByUserID retrieves recent diaries for a user, ordered by entry_date desc
func (r *DiaryRepository) GetRecentDiariesByUserID(ctx context.Context, userID string, limit int) ([]models.Diary, error) {
	slog.Debug("Fetching recent diaries",
		"user_id", userID,
		"limit", limit,
	)

	var diaries []models.Diary

	result := r.db.WithContext(ctx).
		Where("user_id = ?", userID).
		Order("entry_date DESC").
		Limit(limit).
		Find(&diaries)

	if result.Error != nil {
		slog.Error("Failed to fetch diaries",
			"user_id", userID,
			"error", result.Error,
		)
		return nil, result.Error
	}

	slog.Info("Successfully fetched diaries",
		"user_id", userID,
		"count", len(diaries),
	)

	return diaries, nil
}

// GetDiaryByID retrieves a single diary by its ID
func (r *DiaryRepository) GetDiaryByID(ctx context.Context, diaryID string) (*models.Diary, error) {
	var diary models.Diary

	result := r.db.WithContext(ctx).
		Where("diary_id = ?", diaryID).
		First(&diary)

	if result.Error != nil {
		if errors.Is(result.Error, gorm.ErrRecordNotFound) {
			return nil, ErrDiaryNotFound
		}
		return nil, result.Error
	}

	return &diary, nil
}

// CountDiariesByUserID counts the total number of diaries for a user
func (r *DiaryRepository) CountDiariesByUserID(ctx context.Context, userID string) (int64, error) {
	var count int64

	result := r.db.WithContext(ctx).
		Model(&models.Diary{}).
		Where("user_id = ?", userID).
		Count(&count)

	if result.Error != nil {
		return 0, result.Error
	}

	return count, nil
}
