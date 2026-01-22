package config

import (
	"fmt"
	"log"
	"strings"

	"github.com/spf13/viper"
)

type Config struct {
	Server   ServerConfig   `mapstructure:"server"`
	Database DatabaseConfig `mapstructure:"database"`
	Redis    RedisConfig    `mapstructure:"redis"`
	JWT      JWTConfig      `mapstructure:"jwt"`
	AI       AIConfig       `mapstructure:"ai"`
	Milvus   MilvusConfig   `mapstructure:"milvus"`
	MCP      MCPConfig      `mapstructure:"mcp"`
}

type ServerConfig struct {
	Port int `mapstructure:"port"`
}

type DatabaseConfig struct {
	Driver       string `mapstructure:"driver"`
	Source       string `mapstructure:"source"`
	MaxIdleConns int    `mapstructure:"max_idle_conns"`
	MaxOpenConns int    `mapstructure:"max_open_conns"`
}

type RedisConfig struct {
	Addr     string `mapstructure:"addr"`
	Password string `mapstructure:"password"`
	DB       int    `mapstructure:"db"`
	PoolSize int    `mapstructure:"pool_size"`
}

type JWTConfig struct {
	Secret                 string `mapstructure:"secret"`
	AccessTokenExpiration  int64  `mapstructure:"access_token_expiration"`
	RefreshTokenExpiration int64  `mapstructure:"refresh_token_expiration"`
}

type AIConfig struct {
	Chat      ModelConfig `mapstructure:"chat"`
	Embedding ModelConfig `mapstructure:"embedding"`
}

type ModelConfig struct {
	BaseURL string `mapstructure:"base_url"`
	APIKey  string `mapstructure:"api_key"`
	Model   string `mapstructure:"model"`
}

type MilvusConfig struct {
	URI   string `mapstructure:"uri"`
	Token string `mapstructure:"token"`
}

type MCPConfig struct {
	Enabled   bool   `mapstructure:"enabled"`
	ServerURL string `mapstructure:"server_url"`
}

var AppConfig Config

func InitConfig() {
	viper.SetConfigName("application")
	viper.SetConfigType("yml")
	viper.AddConfigPath("./config")
	viper.AutomaticEnv()
	// Replace dot with underscore in env variables
	viper.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))

	if err := viper.ReadInConfig(); err != nil {
		log.Printf("Warning: Config file not found: %v", err)
	}

	if err := viper.Unmarshal(&AppConfig); err != nil {
		log.Fatalf("Unable to decode into struct, %v", err)
	}

	fmt.Printf("Config loaded: Port %d\n", AppConfig.Server.Port)
}
