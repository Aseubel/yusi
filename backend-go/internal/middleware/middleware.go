package middleware

import (
	"strings"
	"yusi-go/pkg/response"
	"yusi-go/pkg/utils"

	"github.com/gin-gonic/gin"
)

func AuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			// Try query parameter for SSE
			token := c.Query("token")
			if token != "" {
				authHeader = "Bearer " + token
			}
		}

		if authHeader == "" {
			response.FailWithCode(c, response.AuthErrCode, "Missing Authorization header")
			c.Abort()
			return
		}

		parts := strings.Split(authHeader, " ")
		if len(parts) != 2 || parts[0] != "Bearer" {
			response.FailWithCode(c, response.AuthErrCode, "Invalid Authorization format")
			c.Abort()
			return
		}

		tokenString := parts[1]
		claims, err := utils.ValidateToken(tokenString)
		if err != nil {
			response.FailWithCode(c, response.AuthErrCode, "Invalid or expired token")
			c.Abort()
			return
		}

		// Store user info in context
		c.Set("userId", claims.UserID)
		c.Set("username", claims.Username)
		
		c.Next()
	}
}

// OptionalAuthMiddleware for endpoints where auth is optional
func OptionalAuthMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader != "" {
			parts := strings.Split(authHeader, " ")
			if len(parts) == 2 && parts[0] == "Bearer" {
				tokenString := parts[1]
				if claims, err := utils.ValidateToken(tokenString); err == nil {
					c.Set("userId", claims.UserID)
					c.Set("username", claims.Username)
				}
			}
		}
		c.Next()
	}
}

func CORSMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Credentials", "true")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, accept, origin, Cache-Control, X-Requested-With, X-Refresh-Token")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS, GET, PUT, DELETE")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}

		c.Next()
	}
}
