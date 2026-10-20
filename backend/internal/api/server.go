// Package api 提供 HTTP 路由、鉴权中间件与接口实现。
package api

import (
	"context"
	"errors"
	"log"
	"net/http"
	"time"

	"babycare/backend/internal/auth"
	"babycare/backend/internal/store"
)

// codeTTLDefault 配对码默认有效期。
const codeTTLDefault = 15 * time.Minute

// deviceCtxKey 鉴权中间件往 context 里放设备的键类型。
type deviceCtxKey struct{}

// Server HTTP 服务：路由 + 中间件 + 依赖的存储层。
type Server struct {
	store   *store.Store
	codeTTL time.Duration
	now     func() time.Time // 可注入，便于测试配对码过期
	mux     *http.ServeMux
}

// New 构造服务并注册全部路由；now 传 nil 则用 time.Now。
func New(st *store.Store, codeTTL time.Duration, now func() time.Time) *Server {
	if codeTTL <= 0 {
		codeTTL = codeTTLDefault
	}
	if now == nil {
		now = time.Now
	}
	s := &Server{store: st, codeTTL: codeTTL, now: now}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/ping", s.handlePing)
	mux.HandleFunc("POST /v1/family", s.handleCreateFamily)
	mux.HandleFunc("POST /v1/pair", s.handlePair)
	mux.HandleFunc("POST /v1/pairing-code", s.auth(s.handleRefreshPairingCode))
	mux.HandleFunc("GET /v1/devices", s.auth(s.handleListDevices))
	mux.HandleFunc("POST /v1/sync/push", s.auth(s.handlePush))
	mux.HandleFunc("GET /v1/sync/pull", s.auth(s.handlePull))
	s.mux = mux
	return s
}

// Handler 返回套上 recover + 访问日志的根 Handler。
func (s *Server) Handler() http.Handler {
	return s.accessLog(s.recover(s.mux))
}

// recover 把 handler panic 转成 500，避免进程退出。
func (s *Server) recover(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rec := recover(); rec != nil {
				log.Printf("panic %s %s: %v", r.Method, r.URL.Path, rec)
				http.Error(w, `{"error":"internal"}`, http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}

// statusWriter 记录响应码供日志使用。
type statusWriter struct {
	http.ResponseWriter
	status int
}

func (sw *statusWriter) WriteHeader(code int) {
	sw.status = code
	sw.ResponseWriter.WriteHeader(code)
}

// accessLog 只打 方法/路径/状态/耗时——令牌、请求体一律不进日志。
func (s *Server) accessLog(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		sw := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(sw, r)
		log.Printf("%s %s %d %s", r.Method, r.URL.Path, sw.status, time.Since(start).Round(time.Microsecond))
	})
}

// auth 校验 Bearer 令牌，通过后把设备放进 context。
func (s *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := bearerToken(r.Header.Get("Authorization"))
		if token == "" {
			s.fail(w, http.StatusUnauthorized, "missing_token")
			return
		}
		dev, err := s.store.DeviceByTokenHash(r.Context(), auth.HashToken(token))
		if err != nil {
			if errors.Is(err, store.ErrNotFound) {
				s.fail(w, http.StatusUnauthorized, "invalid_token")
				return
			}
			s.fail(w, http.StatusInternalServerError, "store_error")
			return
		}
		// 活跃时间尽力更新，失败只影响「最后同步」展示，不影响请求
		if err := s.store.TouchDevice(r.Context(), dev.ID, s.now().Unix()); err != nil {
			log.Printf("更新设备活跃时间失败 device=%s: %v", dev.ID, err)
		}
		ctx := context.WithValue(r.Context(), deviceCtxKey{}, dev)
		next(w, r.WithContext(ctx))
	}
}

// deviceFromContext 取出当前请求对应的设备；只应在 auth 之后的 handler 里调用。
func deviceFromContext(ctx context.Context) *store.Device {
	return ctx.Value(deviceCtxKey{}).(*store.Device)
}

// bearerToken 解析 "Bearer <token>"，格式不对返回空串。
func bearerToken(header string) string {
	const prefix = "Bearer "
	if len(header) <= len(prefix) || header[:len(prefix)] != prefix {
		return ""
	}
	return header[len(prefix):]
}
