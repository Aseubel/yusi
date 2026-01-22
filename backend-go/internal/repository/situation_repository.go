package repository

import (
	"yusi-go/internal/config"
	"yusi-go/internal/model"
)

type SituationRoomRepository struct{}

func NewSituationRoomRepository() *SituationRoomRepository {
	return &SituationRoomRepository{}
}

func (r *SituationRoomRepository) Create(room *model.SituationRoom) error {
	return config.DB.Create(room).Error
}

func (r *SituationRoomRepository) FindById(code string) (*model.SituationRoom, error) {
	var room model.SituationRoom
	err := config.DB.Where("code = ?", code).First(&room).Error
	return &room, err
}

func (r *SituationRoomRepository) Update(room *model.SituationRoom) error {
	return config.DB.Save(room).Error
}

func (r *SituationRoomRepository) ExistsById(code string) bool {
	var count int64
	config.DB.Model(&model.SituationRoom{}).Where("code = ?", code).Count(&count)
	return count > 0
}

func (r *SituationRoomRepository) FindByMember(userId string) ([]model.SituationRoom, error) {
	var rooms []model.SituationRoom
	// JSON_CONTAINS works for JSON columns, but here we used TEXT.
	// Since we serialized Set<String> as JSON array in TEXT, LIKE might work if simple,
	// but strictly speaking standard SQL for JSON text search is limited.
	// However, GORM + MySQL supports JSON functions if column type is JSON.
	// We defined type:text. 
	// For "members" which is stored as ["uid1", "uid2"], we can use LIKE '%"uid"%'
	// This is a bit hacky but consistent with simple text storage.
	err := config.DB.Where("members LIKE ?", "%\""+userId+"\"%").Order("created_at desc").Find(&rooms).Error
	return rooms, err
}

type SituationScenarioRepository struct{}

func NewSituationScenarioRepository() *SituationScenarioRepository {
	return &SituationScenarioRepository{}
}

func (r *SituationScenarioRepository) Create(scenario *model.SituationScenario) error {
	return config.DB.Create(scenario).Error
}

func (r *SituationScenarioRepository) FindById(id string) (*model.SituationScenario, error) {
	var scenario model.SituationScenario
	err := config.DB.Where("id = ?", id).First(&scenario).Error
	return &scenario, err
}

func (r *SituationScenarioRepository) FindByStatus(status int) ([]model.SituationScenario, error) {
	var scenarios []model.SituationScenario
	err := config.DB.Where("status = ?", status).Find(&scenarios).Error
	return scenarios, err
}

func (r *SituationScenarioRepository) FindByStatusGreaterThanEqual(status int) ([]model.SituationScenario, error) {
	var scenarios []model.SituationScenario
	err := config.DB.Where("status >= ?", status).Find(&scenarios).Error
	return scenarios, err
}

func (r *SituationScenarioRepository) Update(scenario *model.SituationScenario) error {
	return config.DB.Save(scenario).Error
}

func (r *SituationScenarioRepository) Count() int64 {
	var count int64
	config.DB.Model(&model.SituationScenario{}).Count(&count)
	return count
}
