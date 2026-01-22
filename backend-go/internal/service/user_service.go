package service

import (
	"errors"
	"yusi-go/internal/model"
	"yusi-go/internal/repository"
	"yusi-go/pkg/utils"

	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

type UserService struct {
	repo *repository.UserRepository
}

func NewUserService() *UserService {
	return &UserService{
		repo: repository.NewUserRepository(),
	}
}

type AuthResponse struct {
	Token        string      `json:"token"`
	RefreshToken string      `json:"refreshToken"`
	User         *model.User `json:"user"`
}

func (s *UserService) Register(req *model.User) (*model.User, error) {
	// Check if user exists
	if _, err := s.repo.FindByUsername(req.UserName); err == nil {
		return nil, errors.New("用户名已存在")
	}

	// Hash password
	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		return nil, err
	}
	req.Password = string(hashedPassword)
	
	// Generate UserID
	req.UserID = uuid.New().String()
	// Set defaults
	req.KeyMode = "DEFAULT"
	
	if err := s.repo.Create(req); err != nil {
		return nil, err
	}
	
	return req, nil
}

func (s *UserService) Login(username, password string) (*AuthResponse, error) {
	user, err := s.repo.FindByUsername(username)
	if err != nil {
		return nil, errors.New("用户不存在")
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return nil, errors.New("密码错误")
	}

	accessToken, err := utils.GenerateAccessToken(user.UserID, user.UserName)
	if err != nil {
		return nil, err
	}

	refreshToken, err := utils.GenerateRefreshToken(user.UserID)
	if err != nil {
		return nil, err
	}

	return &AuthResponse{
		Token:        accessToken,
		RefreshToken: refreshToken,
		User:         user,
	}, nil
}

func (s *UserService) RefreshToken(token string) (*AuthResponse, error) {
	claims, err := utils.ValidateToken(token)
	if err != nil || claims.Type != "refresh" {
		return nil, errors.New("无效的刷新令牌")
	}

	user, err := s.repo.FindByUserId(claims.UserID)
	if err != nil {
		return nil, errors.New("用户不存在")
	}

	accessToken, err := utils.GenerateAccessToken(user.UserID, user.UserName)
	if err != nil {
		return nil, err
	}
	
	// Optionally rotate refresh token
	refreshToken, err := utils.GenerateRefreshToken(user.UserID)
	if err != nil {
		return nil, err
	}

	return &AuthResponse{
		Token:        accessToken,
		RefreshToken: refreshToken,
		User:         user,
	}, nil
}
