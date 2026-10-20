package com.babycare.app.ui.mine

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.app.data.backup.BackupCodec
import com.babycare.app.data.entity.RecordEntity
import com.babycare.app.data.model.BabyProfilePayload
import com.babycare.app.data.model.MetaKeys
import com.babycare.app.data.model.RecordTypes
import com.babycare.app.data.repo.BabyRepository
import com.babycare.app.domain.TimeKit
import com.babycare.app.sync.SyncCenter
import com.babycare.app.timer.TimerCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 我的页：档案编辑 / 深色开关（状态在 Application）/ 备份导出导入 / 清空。
 * 文件读写都走 SAF 返回的 URI，VM 不持有任何路径。
 */
class MineViewModel(
    private val repo: BabyRepository,
    private val timers: TimerCenter,
    private val resolver: ContentResolver,
    private val sync: SyncCenter,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    data class MineUi(val loading: Boolean = true, val baby: RecordEntity? = null)

    /** loading 区分「首值未到」与「确无宝宝」，避免误闪引导页（与 TodayPage 同思路） */
    val ui: StateFlow<MineUi> = repo.observeBaby()
        .map { MineUi(loading = false, baby = it.firstOrNull()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MineUi())

    /** 无宝宝时建档（与 TodayViewModel.createBaby 同口径：自指 babyId + meta active） */
    fun createBaby(nickname: String, birthday: LocalDate, gender: String) {
        viewModelScope.launch {
            val uuid = UUID.randomUUID().toString()
            repo.add(
                type = RecordTypes.BABY_PROFILE,
                payload = BabyProfilePayload(nickname = nickname, birthday = birthday.toString(), gender = gender),
                occurredAt = TimeKit.dayStart(birthday),
                babyId = uuid,
                uuid = uuid,
            )
            repo.metaPut(MetaKeys.ACTIVE_BABY_ID, uuid)
        }
    }

    /** 编辑档案：同 uuid，updatedAt 推进 → 第 N 天 / 疫苗推算自动联动 */
    fun saveProfile(
        entity: RecordEntity,
        nickname: String,
        birthday: LocalDate,
        gender: String,
        birthWeightKg: Double?,
        birthHeightCm: Double?,
    ) = launchIO {
        repo.update(
            entity,
            BabyProfilePayload(
                nickname = nickname,
                birthday = birthday.toString(),
                gender = gender,
                birthWeightKg = birthWeightKg,
                birthHeightCm = birthHeightCm,
            ),
            // 生日变了档案记录的 occurredAt 跟着走（与建档口径一致：生日当天 0 点）
            occurredAt = TimeKit.dayStart(birthday),
        )
    }

    fun backupFileName(): String =
        "babycare-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json"

    /** 导出全量（含软删墓碑）到 SAF 选定的位置 */
    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = BackupCodec.encode(repo.allOnce(), now())
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    /** 读备份并算好导入计划；null = 文件打不开或无法识别 */
    suspend fun readBackup(uri: Uri): BackupCodec.ImportPlan? = withContext(Dispatchers.IO) {
        runCatching {
            val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@withContext null
            val doc = BackupCodec.decode(text) ?: return@withContext null
            val local = repo.allOnce().associate { it.uuid to it.updatedAt }
            BackupCodec.planImport(doc.records, local)
        }.getOrNull()
    }

    /** 确认后落地导入 */
    suspend fun applyImport(plan: BackupCodec.ImportPlan): Boolean = withContext(Dispatchers.IO) {
        runCatching { repo.importAll(plan.toApply.map { it.toEntity() }) }.isSuccess
    }

    /** 清空：先按 TimerCenter 同路径取消进行中计时，再清库。
     *  0016 K8 分叉：同步激活时全表墓碑化走推送（对端一并消失）；未激活维持物理删。 */
    fun wipeAll() = launchIO {
        timers.cancelSleep()
        timers.cancelNursing()
        if (sync.syncActive) repo.tombstoneAll() else repo.wipeAll()
    }

    private fun launchIO(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
