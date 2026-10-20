// Package store 封装 SQLite 存储层：家庭、设备与同步记录的读写。
// 设计口径：记录是「变更日志」，payload 由客户端定义，服务端只透传；
// 一切查询按 family_id 过滤，家庭间隔离在这一层保证。
package store

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"

	_ "modernc.org/sqlite" // 纯 Go 驱动，保证单二进制跨平台交叉编译
)

// 单批与单条的尺寸上限；handler 层校验，这里作为权威常量。
const (
	MaxPayloadBytes = 64 * 1024
	MaxChanges      = 500
)

// ErrNotFound 表示按条件查不到记录（与底层驱动错误区分开）。
var ErrNotFound = errors.New("store: not found")

// Record 一条同步记录。Push 时 Payload 为客户端 JSON 原文；
// Pull 时额外填充 Author 与 Seq。
type Record struct {
	UUID      string          `json:"uuid"`
	Type      string          `json:"type"`
	Payload   json.RawMessage `json:"payload"`
	UpdatedAt int64           `json:"updated_at"` // 客户端毫秒时间戳，LWW 依据
	Deleted   bool            `json:"deleted"`
	Author    string          `json:"author_device_id,omitempty"` // 下行时由服务端填充
	Seq       int64           `json:"seq,omitempty"`              // 下行时填充，服务端游标
}

// Family 家庭。
type Family struct {
	ID            string
	Name          string
	PairingCode   string
	CodeExpiresAt int64 // unix 秒
	CreatedAt     int64 // unix 秒
}

// Device 已配对的设备。
type Device struct {
	ID         string
	FamilyID   string
	Name       string
	CreatedAt  int64
	LastSeenAt int64
}

// Store SQLite 存储。
type Store struct {
	db *sql.DB
}

// Open 打开（或创建）数据库并完成建表。
func Open(path string) (*Store, error) {
	// WAL 提升并发读；SQLite 是单写者模型，MaxOpenConns(1) 从根上避开 database is locked
	db, err := sql.Open("sqlite", path+"?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)&_pragma=foreign_keys(1)")
	if err != nil {
		return nil, fmt.Errorf("打开数据库: %w", err)
	}
	db.SetMaxOpenConns(1)
	s := &Store{db: db}
	if err := s.migrate(context.Background()); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

// Close 关闭数据库。
func (s *Store) Close() error { return s.db.Close() }

func (s *Store) migrate(ctx context.Context) error {
	const ddl = `
CREATE TABLE IF NOT EXISTS families (
  id              TEXT PRIMARY KEY,
  name            TEXT NOT NULL DEFAULT '',
  pairing_code    TEXT NOT NULL,
  code_expires_at INTEGER NOT NULL,
  created_at      INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS devices (
  id           TEXT PRIMARY KEY,
  family_id    TEXT NOT NULL REFERENCES families(id),
  name         TEXT NOT NULL,
  token_hash   TEXT NOT NULL UNIQUE,
  created_at   INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_devices_family ON devices(family_id);
CREATE TABLE IF NOT EXISTS records (
  family_id        TEXT NOT NULL,
  uuid             TEXT NOT NULL,
  type             TEXT NOT NULL,
  payload          TEXT NOT NULL,
  updated_at       INTEGER NOT NULL,
  deleted          INTEGER NOT NULL DEFAULT 0,
  author_device_id TEXT NOT NULL,
  seq              INTEGER PRIMARY KEY AUTOINCREMENT,
  UNIQUE(family_id, uuid)
);
CREATE INDEX IF NOT EXISTS idx_records_pull ON records(family_id, seq);
`
	if _, err := s.db.ExecContext(ctx, ddl); err != nil {
		return fmt.Errorf("建表: %w", err)
	}
	return nil
}

// CreateFamily 新建家庭。
func (s *Store) CreateFamily(ctx context.Context, id, name, pairingCode string, codeExpiresAt, now int64) (*Family, error) {
	f := &Family{ID: id, Name: name, PairingCode: pairingCode, CodeExpiresAt: codeExpiresAt, CreatedAt: now}
	_, err := s.db.ExecContext(ctx,
		`INSERT INTO families (id, name, pairing_code, code_expires_at, created_at) VALUES (?,?,?,?,?)`,
		f.ID, f.Name, f.PairingCode, f.CodeExpiresAt, f.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("插入家庭: %w", err)
	}
	return f, nil
}

// RefreshPairingCode 重置家庭配对码（旧码随之作废）。
func (s *Store) RefreshPairingCode(ctx context.Context, familyID, code string, expiresAt int64) error {
	res, err := s.db.ExecContext(ctx,
		`UPDATE families SET pairing_code=?, code_expires_at=? WHERE id=?`, code, expiresAt, familyID)
	if err != nil {
		return fmt.Errorf("刷新配对码: %w", err)
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// FamilyByPairingCode 按配对码查家庭。
func (s *Store) FamilyByPairingCode(ctx context.Context, code string) (*Family, error) {
	row := s.db.QueryRowContext(ctx,
		`SELECT id, name, pairing_code, code_expires_at, created_at FROM families WHERE pairing_code=?`, code)
	var f Family
	if err := row.Scan(&f.ID, &f.Name, &f.PairingCode, &f.CodeExpiresAt, &f.CreatedAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("查询家庭: %w", err)
	}
	return &f, nil
}

// CreateDevice 注册设备，token 只传摘要。
func (s *Store) CreateDevice(ctx context.Context, id, familyID, name, tokenHash string, now int64) (*Device, error) {
	d := &Device{ID: id, FamilyID: familyID, Name: name, CreatedAt: now, LastSeenAt: now}
	_, err := s.db.ExecContext(ctx,
		`INSERT INTO devices (id, family_id, name, token_hash, created_at, last_seen_at) VALUES (?,?,?,?,?,?)`,
		d.ID, d.FamilyID, d.Name, tokenHash, d.CreatedAt, d.LastSeenAt)
	if err != nil {
		return nil, fmt.Errorf("插入设备: %w", err)
	}
	return d, nil
}

// DeviceByTokenHash 按令牌摘要查设备（登录态解析的唯一入口）。
func (s *Store) DeviceByTokenHash(ctx context.Context, tokenHash string) (*Device, error) {
	row := s.db.QueryRowContext(ctx,
		`SELECT id, family_id, name, created_at, last_seen_at FROM devices WHERE token_hash=?`, tokenHash)
	var d Device
	if err := row.Scan(&d.ID, &d.FamilyID, &d.Name, &d.CreatedAt, &d.LastSeenAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("查询设备: %w", err)
	}
	return &d, nil
}

// TouchDevice 更新设备活跃时间（尽力而为，失败不影响主流程）。
func (s *Store) TouchDevice(ctx context.Context, deviceID string, now int64) error {
	_, err := s.db.ExecContext(ctx, `UPDATE devices SET last_seen_at=? WHERE id=?`, now, deviceID)
	if err != nil {
		return fmt.Errorf("更新设备活跃时间: %w", err)
	}
	return nil
}

// ListDevices 家庭设备列表。
func (s *Store) ListDevices(ctx context.Context, familyID string) ([]Device, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, family_id, name, created_at, last_seen_at FROM devices WHERE family_id=? ORDER BY created_at`, familyID)
	if err != nil {
		return nil, fmt.Errorf("查询设备列表: %w", err)
	}
	defer rows.Close()
	var out []Device
	for rows.Next() {
		var d Device
		if err := rows.Scan(&d.ID, &d.FamilyID, &d.Name, &d.CreatedAt, &d.LastSeenAt); err != nil {
			return nil, fmt.Errorf("扫描设备: %w", err)
		}
		out = append(out, d)
	}
	return out, rows.Err()
}

// Push 落一批变更，LWW 语义：同 uuid 仅当 updated_at 严格大于库内值才覆盖。
// 覆盖采用 DELETE + INSERT（单事务内），让 AUTOINCREMENT 分配新 seq——
// 这样「更新」对其他设备是新的游标位置，增量拉取不会漏。
// 返回 applied/skipped 与本家庭当前最新 seq。
func (s *Store) Push(ctx context.Context, familyID, deviceID string, changes []Record) (applied, skipped int, cursor int64, err error) {
	if len(changes) == 0 {
		cursor, err = s.maxSeq(ctx, familyID)
		return 0, 0, cursor, err
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, 0, 0, fmt.Errorf("开启事务: %w", err)
	}
	defer tx.Rollback() // Commit 成功后 Rollback 是空操作

	for _, ch := range changes {
		var existing int64
		err := tx.QueryRowContext(ctx,
			`SELECT updated_at FROM records WHERE family_id=? AND uuid=?`, familyID, ch.UUID).Scan(&existing)
		switch {
		case errors.Is(err, sql.ErrNoRows):
			// 新记录，直接插入
		case err != nil:
			return 0, 0, 0, fmt.Errorf("查询既有记录: %w", err)
		case ch.UpdatedAt <= existing:
			// LWW 裁决为旧，跳过（含相等：先到者赢，保证确定性）
			skipped++
			continue
		}
		if _, err := tx.ExecContext(ctx,
			`DELETE FROM records WHERE family_id=? AND uuid=?`, familyID, ch.UUID); err != nil {
			return 0, 0, 0, fmt.Errorf("删除旧记录: %w", err)
		}
		if _, err := tx.ExecContext(ctx,
			`INSERT INTO records (family_id, uuid, type, payload, updated_at, deleted, author_device_id)
			 VALUES (?,?,?,?,?,?,?)`,
			familyID, ch.UUID, ch.Type, string(ch.Payload), ch.UpdatedAt, boolToInt(ch.Deleted), deviceID); err != nil {
			return 0, 0, 0, fmt.Errorf("写入记录: %w", err)
		}
		applied++
	}

	cursor, err = maxSeqTx(ctx, tx, familyID)
	if err != nil {
		return 0, 0, 0, err
	}
	if err := tx.Commit(); err != nil {
		return 0, 0, 0, fmt.Errorf("提交事务: %w", err)
	}
	return applied, skipped, cursor, nil
}

// Pull 按游标增量拉取（seq 严格大于 cursor），next 为本页最大 seq，more 表示可能还有。
func (s *Store) Pull(ctx context.Context, familyID string, cursor int64, limit int) ([]Record, int64, bool, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT uuid, type, payload, updated_at, deleted, author_device_id, seq
		 FROM records WHERE family_id=? AND seq>? ORDER BY seq LIMIT ?`,
		familyID, cursor, limit)
	if err != nil {
		return nil, 0, false, fmt.Errorf("查询记录: %w", err)
	}
	defer rows.Close()
	out := make([]Record, 0, limit)
	for rows.Next() {
		var r Record
		var payload string
		var deleted int
		if err := rows.Scan(&r.UUID, &r.Type, &payload, &r.UpdatedAt, &deleted, &r.Author, &r.Seq); err != nil {
			return nil, 0, false, fmt.Errorf("扫描记录: %w", err)
		}
		r.Payload = json.RawMessage(payload)
		r.Deleted = deleted != 0
		out = append(out, r)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, false, fmt.Errorf("遍历记录: %w", err)
	}
	next := cursor
	if len(out) > 0 {
		next = out[len(out)-1].Seq
	}
	return out, next, len(out) == limit, nil
}

func (s *Store) maxSeq(ctx context.Context, familyID string) (int64, error) {
	var v int64
	if err := s.db.QueryRowContext(ctx,
		`SELECT IFNULL(MAX(seq),0) FROM records WHERE family_id=?`, familyID).Scan(&v); err != nil {
		return 0, fmt.Errorf("查询最大游标: %w", err)
	}
	return v, nil
}

func maxSeqTx(ctx context.Context, tx *sql.Tx, familyID string) (int64, error) {
	var v int64
	if err := tx.QueryRowContext(ctx,
		`SELECT IFNULL(MAX(seq),0) FROM records WHERE family_id=?`, familyID).Scan(&v); err != nil {
		return 0, fmt.Errorf("查询最大游标: %w", err)
	}
	return v, nil
}

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}
