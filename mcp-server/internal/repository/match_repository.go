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
	ErrMatchNotFound = errors.New("match record not found")
)

// MatchRepository handles soul_match-related database operations
type MatchRepository struct {
	db *database.DB
}

// NewMatchRepository creates a new MatchRepository
func NewMatchRepository(db *database.DB) *MatchRepository {
	return &MatchRepository{db: db}
}

// CheckMatchStatus checks if two users are matched
// It checks both directions: (user_a, user_b) and (user_b, user_a)
func (r *MatchRepository) CheckMatchStatus(ctx context.Context, userAID, userBID string) (*models.SoulMatch, error) {
	slog.Debug("Checking match status",
		"user_a_id", userAID,
		"user_b_id", userBID,
	)

	var match models.SoulMatch

	// Check for match in either direction
	result := r.db.WithContext(ctx).
		Where("(user_a_id = ? AND user_b_id = ?) OR (user_a_id = ? AND user_b_id = ?)",
			userAID, userBID, userBID, userAID).
		First(&match)

	if result.Error != nil {
		if errors.Is(result.Error, gorm.ErrRecordNotFound) {
			slog.Info("No match record found",
				"user_a_id", userAID,
				"user_b_id", userBID,
			)
			return nil, ErrMatchNotFound
		}
		slog.Error("Failed to check match status",
			"user_a_id", userAID,
			"user_b_id", userBID,
			"error", result.Error,
		)
		return nil, result.Error
	}

	slog.Info("Match record found",
		"user_a_id", userAID,
		"user_b_id", userBID,
		"is_matched", match.IsMatched,
	)

	return &match, nil
}

// GetMatchesByUserID retrieves all matches for a user
func (r *MatchRepository) GetMatchesByUserID(ctx context.Context, userID string) ([]models.SoulMatch, error) {
	var matches []models.SoulMatch

	result := r.db.WithContext(ctx).
		Where("user_a_id = ? OR user_b_id = ?", userID, userID).
		Find(&matches)

	if result.Error != nil {
		return nil, result.Error
	}

	return matches, nil
}

// GetActiveMatchesCount counts the number of active matches for a user
func (r *MatchRepository) GetActiveMatchesCount(ctx context.Context, userID string) (int64, error) {
	var count int64

	result := r.db.WithContext(ctx).
		Model(&models.SoulMatch{}).
		Where("(user_a_id = ? OR user_b_id = ?) AND is_matched = ?", userID, userID, true).
		Count(&count)

	if result.Error != nil {
		return 0, result.Error
	}

	return count, nil
}
