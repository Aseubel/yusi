package database

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/aseubel/yusi-mcp-server/internal/config"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var (
	ErrDatabaseNotConnected = errors.New("database not connected")
	ErrConnectionFailed     = errors.New("failed to connect to database")
)

// DB wraps the gorm.DB with health check capabilities
type DB struct {
	*gorm.DB
	cfg *config.DatabaseConfig
}

// New creates a new database connection with the given configuration
func New(cfg *config.DatabaseConfig) (*DB, error) {
	slog.Info("Connecting to MySQL database",
		"host", cfg.Host,
		"port", cfg.Port,
		"database", cfg.DBName,
	)

	// Configure GORM logger
	gormLogger := logger.New(
		&slogWriter{},
		logger.Config{
			SlowThreshold:             time.Second,
			LogLevel:                  logger.Warn,
			IgnoreRecordNotFoundError: true,
			Colorful:                  false,
		},
	)

	// Connect to MySQL
	db, err := gorm.Open(mysql.Open(cfg.DSN()), &gorm.Config{
		Logger: gormLogger,
	})
	if err != nil {
		slog.Error("Failed to connect to database",
			"error", err,
			"host", cfg.Host,
			"port", cfg.Port,
		)
		return nil, fmt.Errorf("%w: %v", ErrConnectionFailed, err)
	}

	// Configure connection pool
	sqlDB, err := db.DB()
	if err != nil {
		slog.Error("Failed to get underlying sql.DB",
			"error", err,
		)
		return nil, fmt.Errorf("failed to configure connection pool: %w", err)
	}

	sqlDB.SetMaxIdleConns(cfg.MaxIdleConns)
	sqlDB.SetMaxOpenConns(cfg.MaxOpenConns)
	sqlDB.SetConnMaxLifetime(cfg.ConnMaxLifetimeDuration())

	// Test connection
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := sqlDB.PingContext(ctx); err != nil {
		slog.Error("Failed to ping database",
			"error", err,
		)
		return nil, fmt.Errorf("%w: ping failed: %v", ErrConnectionFailed, err)
	}

	slog.Info("Successfully connected to MySQL database",
		"max_idle_conns", cfg.MaxIdleConns,
		"max_open_conns", cfg.MaxOpenConns,
	)

	return &DB{
		DB:  db,
		cfg: cfg,
	}, nil
}

// HealthCheck performs a health check on the database connection
func (d *DB) HealthCheck(ctx context.Context) error {
	if d.DB == nil {
		return ErrDatabaseNotConnected
	}

	sqlDB, err := d.DB.DB()
	if err != nil {
		return fmt.Errorf("failed to get underlying connection: %w", err)
	}

	if err := sqlDB.PingContext(ctx); err != nil {
		return fmt.Errorf("database ping failed: %w", err)
	}

	return nil
}

// Close closes the database connection
func (d *DB) Close() error {
	if d.DB == nil {
		return nil
	}

	sqlDB, err := d.DB.DB()
	if err != nil {
		return err
	}

	slog.Info("Closing database connection")
	return sqlDB.Close()
}

// slogWriter adapts slog for GORM logger
type slogWriter struct{}

func (w *slogWriter) Printf(format string, args ...interface{}) {
	slog.Debug(fmt.Sprintf(format, args...))
}
