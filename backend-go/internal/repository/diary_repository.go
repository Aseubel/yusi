package repository

import (
	"yusi-go/internal/config"
	"yusi-go/internal/model"
)

type DiaryRepository struct{}

func NewDiaryRepository() *DiaryRepository {
	return &DiaryRepository{}
}

func (r *DiaryRepository) Create(diary *model.Diary) error {
	return config.DB.Create(diary).Error
}

func (r *DiaryRepository) Update(diary *model.Diary) error {
	return config.DB.Save(diary).Error
}

func (r *DiaryRepository) FindByDiaryId(userId, diaryId string) (*model.Diary, error) {
	var diary model.Diary
	err := config.DB.Where("diary_id = ?", diaryId).First(&diary).Error
	return &diary, err
}

func (r *DiaryRepository) FindByUserId(userId string, page, size int, sortBy string, asc bool) ([]model.Diary, int64, error) {
	var diaries []model.Diary
	var total int64
	
	db := config.DB.Model(&model.Diary{}).Where("user_id = ?", userId)
	
	// Count
	if err := db.Count(&total).Error; err != nil {
		return nil, 0, err
	}
	
	// Sort
	order := sortBy
	if !asc {
		order += " desc"
	}
	
	// Pagination
	offset := (page - 1) * size
	err := db.Order(order).Offset(offset).Limit(size).Find(&diaries).Error
	
	return diaries, total, err
}
