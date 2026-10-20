package store

import (
	"context"
	"encoding/json"
	"path/filepath"
	"testing"
)

// newTestStore 每个用例独立临时库，互不干扰。
func newTestStore(t *testing.T) *Store {
	t.Helper()
	st, err := Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("打开测试库: %v", err)
	}
	t.Cleanup(func() { _ = st.Close() })
	return st
}

// mkRec 构造一条上行记录。
func mkRec(uuid, payload string, updatedAt int64) Record {
	return Record{UUID: uuid, Type: "feed_formula", Payload: json.RawMessage(payload), UpdatedAt: updatedAt}
}

// pullAll 拉全量并断言条数。
func pullAll(t *testing.T, st *Store, familyID string) []Record {
	t.Helper()
	recs, next, more, err := st.Pull(context.Background(), familyID, 0, 500)
	if err != nil {
		t.Fatalf("pull: %v", err)
	}
	if more {
		t.Fatalf("未预期还有更多数据")
	}
	_ = next
	return recs
}

func TestPushLWW(t *testing.T) {
	cases := []struct {
		name        string
		first, then int64  // 同一 uuid 两次 push 的 updated_at
		wantApplied int    // 第二次的 applied
		wantSkipped int    // 第二次的 skipped
		wantFinal   int64  // 终态 updated_at
	}{
		{"更新的时间覆盖旧值", 100, 200, 1, 0, 200},
		{"更旧的时间被跳过", 200, 100, 0, 1, 200},
		{"相等视为旧跳过", 200, 200, 0, 1, 200},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			st := newTestStore(t)
			ctx := context.Background()

			if _, _, _, err := st.Push(ctx, "f1", "d1", []Record{mkRec("u1", `{"v":1}`, tc.first)}); err != nil {
				t.Fatalf("首次 push: %v", err)
			}
			applied, skipped, _, err := st.Push(ctx, "f1", "d1", []Record{mkRec("u1", `{"v":2}`, tc.then)})
			if err != nil {
				t.Fatalf("二次 push: %v", err)
			}
			if applied != tc.wantApplied || skipped != tc.wantSkipped {
				t.Fatalf("applied=%d skipped=%d, 期望 %d/%d", applied, skipped, tc.wantApplied, tc.wantSkipped)
			}
			recs := pullAll(t, st, "f1")
			if len(recs) != 1 || recs[0].UpdatedAt != tc.wantFinal {
				t.Fatalf("终态 %+v, 期望单条 updated_at=%d", recs, tc.wantFinal)
			}
		})
	}
}

// 更新必须产生新 seq：已按旧游标拉过的设备要能再拉到这次更新。
func TestUpdateBumpsSeq(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()

	if _, _, _, err := st.Push(ctx, "f1", "d1", []Record{mkRec("u1", `{"v":1}`, 100)}); err != nil {
		t.Fatalf("push: %v", err)
	}
	recs := pullAll(t, st, "f1")
	oldSeq := recs[0].Seq

	if _, _, _, err := st.Push(ctx, "f1", "d1", []Record{mkRec("u1", `{"v":2}`, 200)}); err != nil {
		t.Fatalf("push 更新: %v", err)
	}
	inc, next, more, err := st.Pull(ctx, "f1", oldSeq, 500)
	if err != nil {
		t.Fatalf("增量 pull: %v", err)
	}
	if len(inc) != 1 || inc[0].Seq <= oldSeq || more || next != inc[0].Seq {
		t.Fatalf("增量拉取未看到更新: %+v next=%d more=%v", inc, next, more)
	}
	if string(inc[0].Payload) != `{"v":2}` {
		t.Fatalf("payload 应为更新后的值, 实际 %s", inc[0].Payload)
	}
}

func TestPullCursorAndPaging(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()

	batch := []Record{
		mkRec("u1", `{"n":1}`, 100),
		mkRec("u2", `{"n":2}`, 101),
		mkRec("u3", `{"n":3}`, 102),
	}
	if _, _, _, err := st.Push(ctx, "f1", "d1", batch); err != nil {
		t.Fatalf("push: %v", err)
	}

	// 第一页 2 条，提示还有
	page1, next, more, err := st.Pull(ctx, "f1", 0, 2)
	if err != nil || len(page1) != 2 || !more {
		t.Fatalf("第一页: len=%d more=%v err=%v", len(page1), more, err)
	}
	// 第二页从 next 续拉，拿到剩余 1 条
	page2, next2, more2, err := st.Pull(ctx, "f1", next, 2)
	if err != nil || len(page2) != 1 || more2 || page2[0].UUID != "u3" {
		t.Fatalf("第二页: len=%d more=%v err=%v", len(page2), more2, err)
	}
	// 再拉为空且游标不动
	page3, next3, more3, err := st.Pull(ctx, "f1", next2, 2)
	if err != nil || len(page3) != 0 || more3 || next3 != next2 {
		t.Fatalf("空页: len=%d more=%v next=%d(期望 %d)", len(page3), more3, next3, next2)
	}
}

func TestFamilyIsolation(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()

	// 两个家庭用同一个 uuid，互不可见
	if _, _, _, err := st.Push(ctx, "f1", "d1", []Record{mkRec("u1", `{"fam":"A"}`, 100)}); err != nil {
		t.Fatalf("push f1: %v", err)
	}
	if _, _, _, err := st.Push(ctx, "f2", "d2", []Record{mkRec("u1", `{"fam":"B"}`, 100)}); err != nil {
		t.Fatalf("push f2: %v", err)
	}
	for _, fam := range []string{"f1", "f2"} {
		recs := pullAll(t, st, fam)
		if len(recs) != 1 {
			t.Fatalf("家庭 %s 应只见自己的 1 条, 实际 %d 条", fam, len(recs))
		}
	}
}

func TestDeletedTombstone(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()

	del := mkRec("u1", `{}`, 200)
	del.Deleted = true
	if _, _, _, err := st.Push(ctx, "f1", "d1", []Record{del}); err != nil {
		t.Fatalf("push 墓碑: %v", err)
	}
	recs := pullAll(t, st, "f1")
	if len(recs) != 1 || !recs[0].Deleted {
		t.Fatalf("墓碑未正确同步: %+v", recs)
	}
}

func TestPushEmptyBatch(t *testing.T) {
	st := newTestStore(t)
	applied, skipped, cursor, err := st.Push(context.Background(), "f1", "d1", nil)
	if err != nil || applied != 0 || skipped != 0 || cursor != 0 {
		t.Fatalf("空批: applied=%d skipped=%d cursor=%d err=%v", applied, skipped, cursor, err)
	}
}
