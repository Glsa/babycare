// Package auth 负责令牌、配对码与 ID 的生成和校验。
// 安全口径：令牌只在响应里出现一次，数据库只存 SHA-256 摘要。
package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"fmt"
	"math/big"
)

// NewToken 生成 32 字节随机数的十六进制令牌（64 字符），作为设备长时凭证。
func NewToken() string {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		// crypto/rand 失败意味着系统熵源不可用，继续运行没有意义
		panic(fmt.Sprintf("crypto/rand 不可用: %v", err))
	}
	return hex.EncodeToString(b)
}

// HashToken 计算令牌摘要；数据库落库与查询都用它，避免明文留存。
func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

// EqualToken 恒定时间比较两个摘要，防时序侧信道。
func EqualToken(a, b string) bool {
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

// NewPairingCode 生成 6 位数字配对码，供另一台设备加入家庭。
func NewPairingCode() string {
	n, err := rand.Int(rand.Reader, big.NewInt(1000000))
	if err != nil {
		panic(fmt.Sprintf("crypto/rand 不可用: %v", err))
	}
	return fmt.Sprintf("%06d", n.Int64())
}

// NewID 生成 16 字节随机十六进制 ID（家庭/设备主键）。
func NewID() string {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		panic(fmt.Sprintf("crypto/rand 不可用: %v", err))
	}
	return hex.EncodeToString(b)
}
