package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf8"

	"babycare/backend/internal/auth"
	"babycare/backend/internal/store"
)

// typeRe 记录类型的格式约束：小写字母开头，小写字母/数字/下划线，≤32 字符。
var typeRe = regexp.MustCompile(`^[a-z][a-z0-9_]{0,31}$`)

// 请求/响应类型（对外 JSON 字段一律 snake_case）。
type (
	createFamilyReq struct {
		FamilyName string `json:"family_name"`
		DeviceName string `json:"device_name"`
	}
	pairReq struct {
		PairingCode string `json:"pairing_code"`
		DeviceName  string `json:"device_name"`
	}
	familyResp struct {
		FamilyID      string `json:"family_id"`
		DeviceID      string `json:"device_id"`
		Token         string `json:"token"`
		PairingCode   string `json:"pairing_code,omitempty"`
		CodeExpiresAt int64  `json:"code_expires_at,omitempty"`
	}
	pushReq struct {
		Changes []store.Record `json:"changes"`
	}
	pushResp struct {
		Applied int   `json:"applied"`
		Skipped int   `json:"skipped"`
		Cursor  int64 `json:"cursor"`
	}
	pullResp struct {
		Changes    []store.Record `json:"changes"`
		NextCursor int64          `json:"next_cursor"`
		HasMore    bool           `json:"has_more"`
	}
	deviceItem struct {
		DeviceID   string `json:"device_id"`
		Name       string `json:"name"`
		LastSeenAt int64  `json:"last_seen_at"`
	}
	devicesResp struct {
		Devices []deviceItem `json:"devices"`
	}
)

// ---- 基础工具 ----

func (s *Server) writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		// 响应头已发出，只能记日志
		_ = err
	}
}

// fail 统一错误响应：{"error":"短蛇形错误码"}。
func (s *Server) fail(w http.ResponseWriter, status int, code string) {
	s.writeJSON(w, status, map[string]string{"error": code})
}

// decodeJSON 限长解码，坏 JSON 统一 400。
func (s *Server) decodeJSON(w http.ResponseWriter, r *http.Request, v any) bool {
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20)) // 请求体整体 ≤1MB
	if err := dec.Decode(v); err != nil {
		s.fail(w, http.StatusBadRequest, "bad_json")
		return false
	}
	return true
}

// validDeviceName 设备名去空格后 1~50 个字符。
func validDeviceName(name string) (string, bool) {
	name = strings.TrimSpace(name)
	if n := utf8.RuneCountInString(name); n < 1 || n > 50 {
		return "", false
	}
	return name, true
}

// ---- 接口实现 ----

func (s *Server) handlePing(w http.ResponseWriter, r *http.Request) {
	s.writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// handleCreateFamily 创建家庭并注册首台设备，返回令牌与配对码。
func (s *Server) handleCreateFamily(w http.ResponseWriter, r *http.Request) {
	var req createFamilyReq
	if !s.decodeJSON(w, r, &req) {
		return
	}
	deviceName, ok := validDeviceName(req.DeviceName)
	if !ok {
		s.fail(w, http.StatusBadRequest, "invalid_device_name")
		return
	}
	familyName := strings.TrimSpace(req.FamilyName)
	if utf8.RuneCountInString(familyName) > 50 {
		s.fail(w, http.StatusBadRequest, "invalid_family_name")
		return
	}
	now := s.now().Unix()
	fam, err := s.store.CreateFamily(r.Context(), auth.NewID(), familyName,
		auth.NewPairingCode(), s.now().Add(s.codeTTL).Unix(), now)
	if err != nil {
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	token := auth.NewToken()
	dev, err := s.store.CreateDevice(r.Context(), auth.NewID(), fam.ID, deviceName, auth.HashToken(token), now)
	if err != nil {
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	s.writeJSON(w, http.StatusCreated, familyResp{
		FamilyID:      fam.ID,
		DeviceID:      dev.ID,
		Token:         token,
		PairingCode:   fam.PairingCode,
		CodeExpiresAt: fam.CodeExpiresAt,
	})
}

// handlePair 凭配对码加入家庭，返回新设备令牌。
func (s *Server) handlePair(w http.ResponseWriter, r *http.Request) {
	var req pairReq
	if !s.decodeJSON(w, r, &req) {
		return
	}
	deviceName, ok := validDeviceName(req.DeviceName)
	if !ok {
		s.fail(w, http.StatusBadRequest, "invalid_device_name")
		return
	}
	code := strings.TrimSpace(req.PairingCode)
	if code == "" {
		s.fail(w, http.StatusBadRequest, "invalid_pairing_code")
		return
	}
	fam, err := s.store.FamilyByPairingCode(r.Context(), code)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			s.fail(w, http.StatusBadRequest, "invalid_pairing_code")
			return
		}
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	if s.now().Unix() >= fam.CodeExpiresAt {
		s.fail(w, http.StatusBadRequest, "pairing_code_expired")
		return
	}
	token := auth.NewToken()
	dev, err := s.store.CreateDevice(r.Context(), auth.NewID(), fam.ID, deviceName, auth.HashToken(token), s.now().Unix())
	if err != nil {
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	s.writeJSON(w, http.StatusOK, familyResp{
		FamilyID: fam.ID,
		DeviceID: dev.ID,
		Token:    token,
	})
}

// handleRefreshPairingCode 刷新配对码（需登录）。
func (s *Server) handleRefreshPairingCode(w http.ResponseWriter, r *http.Request) {
	dev := deviceFromContext(r.Context())
	code := auth.NewPairingCode()
	expires := s.now().Add(s.codeTTL).Unix()
	if err := s.store.RefreshPairingCode(r.Context(), dev.FamilyID, code, expires); err != nil {
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"pairing_code": code, "code_expires_at": expires})
}

// handleListDevices 家庭设备列表（谁在共记）。
func (s *Server) handleListDevices(w http.ResponseWriter, r *http.Request) {
	dev := deviceFromContext(r.Context())
	list, err := s.store.ListDevices(r.Context(), dev.FamilyID)
	if err != nil {
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	out := devicesResp{Devices: make([]deviceItem, 0, len(list))}
	for _, d := range list {
		out.Devices = append(out.Devices, deviceItem{DeviceID: d.ID, Name: d.Name, LastSeenAt: d.LastSeenAt})
	}
	s.writeJSON(w, http.StatusOK, out)
}

// validateChange 校验单条上行变更，返回规范化后的记录。
func (s *Server) validateChange(ch *store.Record) (ok bool, errCode string) {
	ch.UUID = strings.TrimSpace(ch.UUID)
	if ch.UUID == "" || len(ch.UUID) > 64 {
		return false, "invalid_uuid"
	}
	if !typeRe.MatchString(ch.Type) {
		return false, "invalid_type"
	}
	// payload 必须是 JSON 对象（而非数字/字符串/null），长度受限
	if len(ch.Payload) == 0 || ch.Payload[0] != '{' || len(ch.Payload) > store.MaxPayloadBytes {
		return false, "invalid_payload"
	}
	if ch.UpdatedAt <= 0 {
		return false, "invalid_updated_at"
	}
	return true, ""
}

// handlePush 上行一批变更；author 一律取自令牌，不信任请求体。
func (s *Server) handlePush(w http.ResponseWriter, r *http.Request) {
	dev := deviceFromContext(r.Context())
	var req pushReq
	if !s.decodeJSON(w, r, &req) {
		return
	}
	if len(req.Changes) > store.MaxChanges {
		s.fail(w, http.StatusBadRequest, "too_many_changes")
		return
	}
	for i := range req.Changes {
		if ok, code := s.validateChange(&req.Changes[i]); !ok {
			s.fail(w, http.StatusBadRequest, code)
			return
		}
	}
	applied, skipped, cursor, err := s.store.Push(r.Context(), dev.FamilyID, dev.ID, req.Changes)
	if err != nil {
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	s.writeJSON(w, http.StatusOK, pushResp{Applied: applied, Skipped: skipped, Cursor: cursor})
}

// handlePull 按游标增量下行；limit 默认 200、上限 500。
func (s *Server) handlePull(w http.ResponseWriter, r *http.Request) {
	dev := deviceFromContext(r.Context())
	q := r.URL.Query()
	cursor := int64(0)
	if v := q.Get("cursor"); v != "" {
		n, err := strconv.ParseInt(v, 10, 64)
		if err != nil || n < 0 {
			s.fail(w, http.StatusBadRequest, "invalid_cursor")
			return
		}
		cursor = n
	}
	limit := 200
	if v := q.Get("limit"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil || n < 1 {
			s.fail(w, http.StatusBadRequest, "invalid_limit")
			return
		}
		limit = n
	}
	if limit > store.MaxChanges {
		limit = store.MaxChanges
	}
	recs, next, more, err := s.store.Pull(r.Context(), dev.FamilyID, cursor, limit)
	if err != nil {
		s.fail(w, http.StatusInternalServerError, "store_error")
		return
	}
	s.writeJSON(w, http.StatusOK, pullResp{Changes: recs, NextCursor: next, HasMore: more})
}
