package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"babycare/backend/internal/store"
)

// testEnv 一个跑在 httptest 上的完整服务 + 临时库。
type testEnv struct {
	ts *httptest.Server
}

func newTestEnvWithClock(t *testing.T, now func() time.Time) *testEnv {
	t.Helper()
	st, err := store.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("打开测试库: %v", err)
	}
	t.Cleanup(func() { _ = st.Close() })
	ts := httptest.NewServer(New(st, 15*time.Minute, now).Handler())
	t.Cleanup(ts.Close)
	return &testEnv{ts: ts}
}

func newTestEnv(t *testing.T) *testEnv {
	return newTestEnvWithClock(t, nil)
}

// do 发请求；body 为 any 自动 JSON 序列化，rawBody 非空则原样发送。
func (e *testEnv) do(t *testing.T, method, path, token string, body any, rawBody string) (int, map[string]any) {
	t.Helper()
	var rd io.Reader
	if rawBody != "" {
		rd = strings.NewReader(rawBody)
	} else if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			t.Fatalf("序列化请求: %v", err)
		}
		rd = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, e.ts.URL+path, rd)
	if err != nil {
		t.Fatalf("构造请求: %v", err)
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("请求 %s %s: %v", method, path, err)
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	out := map[string]any{}
	_ = json.Unmarshal(data, &out) // ping 等非对象响应容错
	return resp.StatusCode, out
}

// createFamily 建家庭返回 令牌 + 配对码 + 设备ID。
func (e *testEnv) createFamily(t *testing.T, deviceName string) (token, code, familyID, deviceID string) {
	t.Helper()
	status, resp := e.do(t, "POST", "/v1/family", "", map[string]string{
		"family_name": "小汤圆家", "device_name": deviceName,
	}, "")
	if status != http.StatusCreated {
		t.Fatalf("创建家庭: status=%d resp=%v", status, resp)
	}
	return resp["token"].(string), resp["pairing_code"].(string), resp["family_id"].(string), resp["device_id"].(string)
}

// pair 凭配对码加入，返回新令牌。
func (e *testEnv) pair(t *testing.T, code, deviceName string) string {
	t.Helper()
	status, resp := e.do(t, "POST", "/v1/pair", "", map[string]string{
		"pairing_code": code, "device_name": deviceName,
	}, "")
	if status != http.StatusOK {
		t.Fatalf("配对: status=%d resp=%v", status, resp)
	}
	return resp["token"].(string)
}

// change 构造一条上行变更。
func change(uuid, typ string, payload string, updatedAt int64) map[string]any {
	return map[string]any{
		"uuid": uuid, "type": typ,
		"payload": json.RawMessage(payload),
		"updated_at": updatedAt, "deleted": false,
	}
}

func TestPing(t *testing.T) {
	e := newTestEnv(t)
	status, resp := e.do(t, "GET", "/v1/ping", "", nil, "")
	if status != http.StatusOK || resp["status"] != "ok" {
		t.Fatalf("ping: status=%d resp=%v", status, resp)
	}
}

func TestCreateFamilyPairAndDevices(t *testing.T) {
	e := newTestEnv(t)
	tokenA, code, _, _ := e.createFamily(t, "妈妈的手机")
	tokenB := e.pair(t, code, "爸爸的手机")

	status, resp := e.do(t, "GET", "/v1/devices", tokenA, nil, "")
	if status != http.StatusOK {
		t.Fatalf("设备列表: status=%d", status)
	}
	devices := resp["devices"].([]any)
	if len(devices) != 2 {
		t.Fatalf("应有 2 台设备, 实际 %d", len(devices))
	}
	// 令牌 B 也能看到同样的列表
	status, resp = e.do(t, "GET", "/v1/devices", tokenB, nil, "")
	if status != http.StatusOK || len(resp["devices"].([]any)) != 2 {
		t.Fatalf("设备B视角列表异常: status=%d", status)
	}
}

// 端到端主流程：A 记 → B 拉；A 改 → B 增量拉；A 旧版本重推被 LWW 跳过。
func TestSyncEndToEnd(t *testing.T) {
	e := newTestEnv(t)
	tokenA, code, _, deviceA := e.createFamily(t, "妈妈的手机")
	tokenB := e.pair(t, code, "爸爸的手机")

	// A 上行两条
	status, resp := e.do(t, "POST", "/v1/sync/push", tokenA, map[string]any{"changes": []any{
		change("u1", "feed_formula", `{"ml":150}`, 1000),
		change("u2", "diaper", `{"type":"both"}`, 1100),
	}}, "")
	if status != http.StatusOK || resp["applied"].(float64) != 2 {
		t.Fatalf("A 首次 push: status=%d resp=%v", status, resp)
	}

	// B 全量拉取
	status, resp = e.do(t, "GET", "/v1/sync/pull?cursor=0", tokenB, nil, "")
	if status != http.StatusOK {
		t.Fatalf("B pull: status=%d", status)
	}
	changes := resp["changes"].([]any)
	if len(changes) != 2 {
		t.Fatalf("B 应拉到 2 条, 实际 %d", len(changes))
	}
	first := changes[0].(map[string]any)
	if first["author_device_id"] != deviceA {
		t.Fatalf("author 应为服务端判定的设备A, 实际 %v", first["author_device_id"])
	}
	next := int64(resp["next_cursor"].(float64))

	// A 修改 u1（更新时间更大）
	status, resp = e.do(t, "POST", "/v1/sync/push", tokenA, map[string]any{"changes": []any{
		change("u1", "feed_formula", `{"ml":120}`, 2000),
	}}, "")
	if status != http.StatusOK || resp["applied"].(float64) != 1 {
		t.Fatalf("A 修改 push: resp=%v", resp)
	}

	// B 增量拉取只看到这条更新
	status, resp = e.do(t, "GET", fmt.Sprintf("/v1/sync/pull?cursor=%d", next), tokenB, nil, "")
	changes = resp["changes"].([]any)
	if len(changes) != 1 {
		t.Fatalf("B 增量应只有 1 条, 实际 %d: %v", len(changes), resp)
	}
	payload := changes[0].(map[string]any)["payload"].(map[string]any)
	if payload["ml"].(float64) != 120 {
		t.Fatalf("增量内容应为更新后的 120ml, 实际 %v", payload)
	}

	// A 用旧时间重推 → skipped；B 无新数据
	status, resp = e.do(t, "POST", "/v1/sync/push", tokenA, map[string]any{"changes": []any{
		change("u1", "feed_formula", `{"ml":999}`, 1500),
	}}, "")
	if resp["applied"].(float64) != 0 || resp["skipped"].(float64) != 1 {
		t.Fatalf("旧时间应 skipped: resp=%v", resp)
	}
	next2 := int64(resp["cursor"].(float64))
	status, resp = e.do(t, "GET", fmt.Sprintf("/v1/sync/pull?cursor=%d", next2), tokenB, nil, "")
	if len(resp["changes"].([]any)) != 0 {
		t.Fatalf("skipped 后不应有新数据: %v", resp)
	}
}

func TestPushIdempotent(t *testing.T) {
	e := newTestEnv(t)
	token, _, _, _ := e.createFamily(t, "妈妈的手机")
	body := map[string]any{"changes": []any{change("u1", "sleep", `{"end":"23:10"}`, 1000)}}

	e.do(t, "POST", "/v1/sync/push", token, body, "")
	status, resp := e.do(t, "POST", "/v1/sync/push", token, body, "")
	if status != http.StatusOK || resp["applied"].(float64) != 0 || resp["skipped"].(float64) != 1 {
		t.Fatalf("重发应全部 skipped: resp=%v", resp)
	}
	_, resp = e.do(t, "GET", "/v1/sync/pull?cursor=0", token, nil, "")
	if len(resp["changes"].([]any)) != 1 {
		t.Fatalf("重发不应产生重复记录: %v", resp)
	}
}

func TestAuthFailures(t *testing.T) {
	e := newTestEnv(t)
	for _, tc := range []struct{ name, token string }{
		{"无令牌", ""},
		{"错误令牌", "deadbeef"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			status, resp := e.do(t, "GET", "/v1/sync/pull", tc.token, nil, "")
			if status != http.StatusUnauthorized {
				t.Fatalf("应 401, 实际 %d resp=%v", status, resp)
			}
		})
	}
}

func TestPairCodeExpiry(t *testing.T) {
	clock := time.Unix(1761000000, 0)
	e := newTestEnvWithClock(t, func() time.Time { return clock })

	_, code, _, _ := e.createFamily(t, "妈妈的手机")
	clock = clock.Add(20 * time.Minute) // 超过 15 分钟有效期

	status, resp := e.do(t, "POST", "/v1/pair", "", map[string]string{
		"pairing_code": code, "device_name": "爸爸的手机",
	}, "")
	if status != http.StatusBadRequest || resp["error"] != "pairing_code_expired" {
		t.Fatalf("过期配对码应 400 pairing_code_expired: status=%d resp=%v", status, resp)
	}
}

func TestRefreshPairingCode(t *testing.T) {
	e := newTestEnv(t)
	token, oldCode, _, _ := e.createFamily(t, "妈妈的手机")

	status, resp := e.do(t, "POST", "/v1/pairing-code", token, nil, "")
	if status != http.StatusOK {
		t.Fatalf("刷新配对码: status=%d", status)
	}
	newCode := resp["pairing_code"].(string)

	// 旧码作废
	status, _ = e.do(t, "POST", "/v1/pair", "", map[string]string{
		"pairing_code": oldCode, "device_name": "爷爷的手机",
	}, "")
	if status != http.StatusBadRequest {
		t.Fatalf("旧码应失效, status=%d", status)
	}
	// 新码可用
	e.pair(t, newCode, "爷爷的手机")
}

func TestValidationFailures(t *testing.T) {
	e := newTestEnv(t)
	token, _, _, _ := e.createFamily(t, "妈妈的手机")

	bigPayload := `{"data":"` + strings.Repeat("x", 70*1024) + `"}`
	cases := []struct {
		name   string
		body   map[string]any
		raw    string
		wantErr string
	}{
		{"坏JSON", nil, `not-json`, "bad_json"},
		{"非法type大写", map[string]any{"changes": []any{change("u1", "Feed", `{}`, 1000)}}, "", "invalid_type"},
		{"payload非对象", map[string]any{"changes": []any{change("u1", "sleep", `123`, 1000)}}, "", "invalid_payload"},
		{"payload超限", map[string]any{"changes": []any{change("u1", "sleep", bigPayload, 1000)}}, "", "invalid_payload"},
		{"空uuid", map[string]any{"changes": []any{change("", "sleep", `{}`, 1000)}}, "", "invalid_uuid"},
		{"updated_at为零", map[string]any{"changes": []any{change("u1", "sleep", `{}`, 0)}}, "", "invalid_updated_at"},
		{"超批上限", map[string]any{"changes": makeBatch(store.MaxChanges + 1)}, "", "too_many_changes"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			status, resp := e.do(t, "POST", "/v1/sync/push", token, tc.body, tc.raw)
			if status != http.StatusBadRequest || resp["error"] != tc.wantErr {
				t.Fatalf("status=%d resp=%v, 期望 400 %s", status, resp, tc.wantErr)
			}
		})
	}

	// 非法输入不落库
	_, resp := e.do(t, "GET", "/v1/sync/pull?cursor=0", token, nil, "")
	if len(resp["changes"].([]any)) != 0 {
		t.Fatalf("非法输入不应落库: %v", resp)
	}

	// pull 参数错误
	status, _ := e.do(t, "GET", "/v1/sync/pull?cursor=abc", token, nil, "")
	if status != http.StatusBadRequest {
		t.Fatalf("cursor 非法应 400, status=%d", status)
	}
}

// makeBatch 生成 n 条合法变更（内容相同无妨，只测数量上限）。
func makeBatch(n int) []any {
	out := make([]any, n)
	for i := range out {
		out[i] = change(fmt.Sprintf("u%d", i), "sleep", `{}`, 1000)
	}
	return out
}

func TestFamilyIsolationEndToEnd(t *testing.T) {
	e := newTestEnv(t)
	tokenA, _, _, _ := e.createFamily(t, "妈妈A的手机")
	tokenB, _, _, _ := e.createFamily(t, "妈妈B的手机")

	e.do(t, "POST", "/v1/sync/push", tokenA, map[string]any{"changes": []any{
		change("u1", "feed_formula", `{"ml":150}`, 1000),
	}}, "")

	status, resp := e.do(t, "GET", "/v1/sync/pull?cursor=0", tokenB, nil, "")
	if status != http.StatusOK || len(resp["changes"].([]any)) != 0 {
		t.Fatalf("家庭B不应看到家庭A的记录: %v", resp)
	}
}
