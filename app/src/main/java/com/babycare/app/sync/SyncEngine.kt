package com.babycare.app.sync

/**
 * 同步引擎（0016）：一轮 = push 全部脏记录（≤500/批）→ pull 到追平（含分页续拉）。
 * 纯逻辑不碰 Android；失败抛 [SyncException]，重试/退避由 SyncCenter 负责。
 * 幂等性由后端保证（push 重发全部 skipped），半批失败重推无害。
 */
class SyncEngine(
    private val transport: SyncTransport,
    private val store: SyncStore,
) {

    suspend fun round() {
        pushAll()
        pullAll()
    }

    /** 推完为止：每批取脏→组信封→上行→双条件清脏→游标抬高到服务端最新 seq */
    private suspend fun pushAll() {
        while (true) {
            val dirty = store.dirtyOnce(BATCH)
            if (dirty.isEmpty()) return
            val changes = dirty.map { e ->
                PushChange(
                    uuid = e.uuid,
                    type = e.type,
                    payload = SyncEnvelope.wrap(e.payload, e.occurredAt, e.babyId),
                    updatedAt = e.updatedAt,
                    deleted = e.deleted,
                )
            }
            val resp = transport.push(changes)
            dirty.forEach { store.markPushed(it.uuid, it.updatedAt) }
            store.raiseCursorTo(resp.cursor)
        }
    }

    /** 拉到追平：游标在批次事务内推进，hasMore 续拉 */
    private suspend fun pullAll() {
        while (true) {
            val resp = transport.pull(store.cursor(), BATCH)
            store.applyPulled(resp.changes, resp.nextCursor)
            if (!resp.hasMore) return
        }
    }

    companion object {
        /** 契约上限：单批 ≤500 条（0001 §3） */
        const val BATCH = 500
    }
}
