package config

import (
	"log"
	"time"
	"yusi-go/internal/model"

	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var DB *gorm.DB

func InitDB() {
	var err error
	dsn := AppConfig.Database.Source
	
	DB, err = gorm.Open(mysql.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
	})
	
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}

	// Auto Migrate
	err = DB.AutoMigrate(
		&model.User{},
		&model.Diary{},
		&model.SituationRoom{},
		&model.SituationScenario{},
		&model.SoulMatch{},
		&model.SoulCard{},
		&model.SoulResonance{},
		&model.SoulMessage{},
	)
	if err != nil {
		log.Fatalf("Failed to auto migrate: %v", err)
	}

	sqlDB, err := DB.DB()
	if err != nil {
		log.Fatalf("Failed to get sql.DB: %v", err)
	}

	sqlDB.SetMaxIdleConns(AppConfig.Database.MaxIdleConns)
	sqlDB.SetMaxOpenConns(AppConfig.Database.MaxOpenConns)
	sqlDB.SetConnMaxLifetime(time.Hour)

	log.Println("Database connected and migrated successfully")
}
