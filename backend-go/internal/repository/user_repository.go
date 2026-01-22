package repository

import (
	"yusi-go/internal/config"
	"yusi-go/internal/model"
)

type UserRepository struct{}

func NewUserRepository() *UserRepository {
	return &UserRepository{}
}

func (r *UserRepository) Create(user *model.User) error {
	return config.DB.Create(user).Error
}

func (r *UserRepository) FindByUsername(username string) (*model.User, error) {
	var user model.User
	err := config.DB.Where("username = ?", username).First(&user).Error
	return &user, err
}

func (r *UserRepository) FindByUserId(userId string) (*model.User, error) {
	var user model.User
	err := config.DB.Where("user_id = ?", userId).First(&user).Error
	return &user, err
}

func (r *UserRepository) Update(user *model.User) error {
	return config.DB.Save(user).Error
}
