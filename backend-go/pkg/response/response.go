package response

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

type Response struct {
	Code int         `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data"`
}

const (
	SuccessCode = 200
	FailCode    = 500
	AuthErrCode = 401
)

func Success(c *gin.Context, data interface{}) {
	c.JSON(http.StatusOK, Response{
		Code: SuccessCode,
		Msg:  "success",
		Data: data,
	})
}

func Fail(c *gin.Context, msg string) {
	c.JSON(http.StatusOK, Response{ // Frontend might expect 200 even for errors, checking code
		Code: FailCode,
		Msg:  msg,
		Data: nil,
	})
}

func FailWithCode(c *gin.Context, code int, msg string) {
	c.JSON(http.StatusOK, Response{
		Code: code,
		Msg:  msg,
		Data: nil,
	})
}
