package main

import (
	"fmt"
	"log"
	"yusi-go/internal/api"
	"yusi-go/internal/config"
	"yusi-go/internal/middleware"

	"github.com/gin-gonic/gin"
)

func main() {
	// 1. Init Config
	config.InitConfig()

	// 2. Init DB & Redis
	config.InitDB()
	config.InitRedis()

	// 3. Setup Router
	r := gin.Default()

	// Global Middleware
	r.Use(middleware.CORSMiddleware())

	// Handlers
	userHandler := api.NewUserHandler()
	diaryHandler := api.NewDiaryHandler()
	aiHandler := api.NewAiHandler()
	keyHandler := api.NewKeyHandler()
	situationHandler := api.NewSituationRoomHandler()
	roomChatHandler := api.NewRoomChatHandler()
	plazaHandler := api.NewSoulPlazaHandler()
	matchHandler := api.NewMatchHandler()
	statsHandler := api.NewStatsHandler()
	promptHandler := api.NewPromptHandler()
	soulChatHandler := api.NewSoulChatHandler()

	// Routes
	apiGroup := r.Group("/api")
	{
		// User
		userGroup := apiGroup.Group("/user")
		{
			userGroup.POST("/register", userHandler.Register)
			userGroup.POST("/login", userHandler.Login)
			userGroup.POST("/refresh", userHandler.Refresh)
			userGroup.POST("/logout", middleware.AuthMiddleware(), userHandler.Logout)
		}

		// Diary
		diaryGroup := apiGroup.Group("/diary")
		diaryGroup.Use(middleware.AuthMiddleware())
		{
			diaryGroup.GET("/list", diaryHandler.GetDiaryList)
			diaryGroup.POST("", diaryHandler.WriteDiary)
			diaryGroup.PUT("", diaryHandler.EditDiary)
			diaryGroup.GET("/:diaryId", diaryHandler.GetDiary)
			diaryGroup.POST("/generate-response/:diaryId", diaryHandler.GenerateResponse)
		}

		// AI
		aiGroup := apiGroup.Group("/ai")
		aiGroup.Use(middleware.AuthMiddleware())
		{
			// Limit: 20 req / 60 sec = 0.33 rps, burst 20
			aiGroup.GET("/chat/stream", middleware.RateLimitMiddleware(20, 60), aiHandler.ChatStream)
		}

		// Key Management
		keyGroup := apiGroup.Group("/key")
		keyGroup.Use(middleware.AuthMiddleware())
		{
			keyGroup.GET("/settings", keyHandler.GetSettings)
			keyGroup.POST("/settings", keyHandler.UpdateSettings)
			keyGroup.GET("/diaries-for-reencrypt", keyHandler.GetDiariesForReEncrypt)
			keyGroup.POST("/reencrypt-diaries", keyHandler.BatchUpdateReEncryptedDiaries)
			keyGroup.GET("/admin/backup-key/:targetUserId", keyHandler.GetBackupKey)
		}

		// Situation Room
		roomGroup := apiGroup.Group("/room")
		roomGroup.Use(middleware.AuthMiddleware())
		{
			roomGroup.POST("/create", situationHandler.Create)
			roomGroup.POST("/join", situationHandler.Join)
			roomGroup.POST("/start", situationHandler.Start)
			roomGroup.POST("/submit", situationHandler.Submit)
			roomGroup.POST("/cancel", situationHandler.Cancel)
			roomGroup.POST("/vote-cancel", situationHandler.VoteCancel)
			roomGroup.GET("/history", situationHandler.GetHistory)
			roomGroup.GET("/report/:code", situationHandler.GetReport)
			roomGroup.GET("/:code", situationHandler.GetRoom)
			
			roomGroup.GET("/scenarios", situationHandler.GetScenarios)
			roomGroup.POST("/scenarios/submit", situationHandler.SubmitScenario)
			roomGroup.POST("/scenarios/review", situationHandler.ReviewScenario)
		}

		// Room Chat
		roomChatGroup := apiGroup.Group("/room-chat")
		roomChatGroup.Use(middleware.AuthMiddleware())
		{
			roomChatGroup.POST("/send", roomChatHandler.Send)
			roomChatGroup.GET("/history", roomChatHandler.GetHistory)
			roomChatGroup.GET("/poll", roomChatHandler.Poll)
		}

		// Soul Plaza
		plazaGroup := apiGroup.Group("/plaza")
		plazaGroup.Use(middleware.AuthMiddleware())
		{
			plazaGroup.POST("/submit", plazaHandler.Submit)
			plazaGroup.GET("/feed", plazaHandler.GetFeed)
			plazaGroup.POST("/:cardId/resonate", plazaHandler.Resonate)
		}

		// Match
		matchGroup := apiGroup.Group("/match")
		matchGroup.Use(middleware.AuthMiddleware())
		{
			matchGroup.POST("/settings", matchHandler.UpdateSettings)
			matchGroup.GET("/recommendations", matchHandler.GetRecommendations)
			matchGroup.POST("/:matchId/action", matchHandler.HandleAction)
			matchGroup.POST("/run", matchHandler.Run)
		}
		
		// Soul Chat (Direct Message)
		soulChatGroup := apiGroup.Group("/soul-chat")
		soulChatGroup.Use(middleware.AuthMiddleware())
		{
			soulChatGroup.POST("/send", soulChatHandler.Send)
			soulChatGroup.GET("/history", soulChatHandler.GetHistory)
			soulChatGroup.POST("/read", soulChatHandler.Read)
			soulChatGroup.GET("/unread/count", soulChatHandler.GetUnreadCount)
		}
		
		// Stats
		statsGroup := apiGroup.Group("/stats")
		{
			statsGroup.GET("/platform", statsHandler.GetPlatformStats)
		}

		// Prompt
		promptGroup := apiGroup.Group("/prompt")
		promptGroup.Use(middleware.AuthMiddleware())
		{
			promptGroup.GET("/:name", promptHandler.GetPrompt)
			promptGroup.POST("/save", promptHandler.SavePrompt)
			promptGroup.POST("/activate", promptHandler.ActivatePrompt)
		}
	}

	// 4. Run
	addr := fmt.Sprintf(":%d", config.AppConfig.Server.Port)
	log.Printf("Server starting on %s", addr)
	if err := r.Run(addr); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
