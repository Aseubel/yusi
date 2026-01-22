package middleware

import (
	"net/http"
	"sync"
	"yusi-go/pkg/response"

	"github.com/gin-gonic/gin"
	"golang.org/x/time/rate"
)

// Simple in-memory rate limiter
type RateLimiter struct {
	ips map[string]*rate.Limiter
	mu  sync.Mutex
	r   rate.Limit
	b   int
}

func NewRateLimiter(r rate.Limit, b int) *RateLimiter {
	return &RateLimiter{
		ips: make(map[string]*rate.Limiter),
		r:   r,
		b:   b,
	}
}

func (i *RateLimiter) AddIP(ip string) *rate.Limiter {
	i.mu.Lock()
	defer i.mu.Unlock()

	limiter, exists := i.ips[ip]
	if !exists {
		limiter = rate.NewLimiter(i.r, i.b)
		i.ips[ip] = limiter
	}

	return limiter
}

func (i *RateLimiter) GetLimiter(ip string) *rate.Limiter {
	i.mu.Lock()
	limiter, exists := i.ips[ip]
	if !exists {
		i.mu.Unlock()
		return i.AddIP(ip)
	}
	i.mu.Unlock()
	return limiter
}

// RateLimitMiddleware creates a middleware for rate limiting
// limit: requests per second (approx) -> derived from count/time
// burst: max burst
func RateLimitMiddleware(count int, periodSeconds int) gin.HandlerFunc {
	// r = count / period
	r := rate.Limit(float64(count) / float64(periodSeconds))
	b := count // burst size usually equals count or smaller

	limiter := NewRateLimiter(r, b)

	return func(c *gin.Context) {
		// Use UserID if available, else IP
		key := c.ClientIP()
		userId := c.GetString("userId")
		if userId != "" {
			key = userId
		}

		l := limiter.GetLimiter(key)
		if !l.Allow() {
			response.FailWithCode(c, http.StatusTooManyRequests, "Too many requests")
			c.Abort()
			return
		}

		c.Next()
	}
}
