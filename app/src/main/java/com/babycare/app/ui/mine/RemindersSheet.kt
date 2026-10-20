@file:OptIn(ExperimentalMaterial3Api::class)

package com.babycare.app.ui.mine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.babycare.app.BabycareApplication
import com.babycare.app.domain.ReminderSettings
import com.babycare.app.ui.theme.BabyTheme
import com.babycare.app.ui.today.SheetColumn

/** 提醒设置弹层（0014 · FR-13）：总开关 + 5 分项；首次打开请求通知权限（API 33+） */
@Composable
internal fun RemindersSheet(
    settings: ReminderSettings,
    onChange: (ReminderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = BabyTheme.colors
    val context = LocalContext.current

    // 拒绝不阻断：开关照常可设，系统层面不弹而已
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetColumn("提醒设置") {
            ToggleRow(
                title = "提醒总开关",
                subtitle = "关闭后所有提醒都不发",
                checked = settings.master,
                onChecked = { onChange(settings.copy(master = it)) },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "分项提醒",
                style = MaterialTheme.typography.labelMedium,
                color = c.text3,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            ToggleRow(
                title = "💉 疫苗",
                subtitle = "到期前 3 天 / 1 天 / 当天 9:00",
                checked = settings.vaccine,
                enabled = settings.master,
                onChecked = { onChange(settings.copy(vaccine = it)) },
            )
            ToggleRow(
                title = "💊 补剂",
                subtitle = "每个设定时间点",
                checked = settings.supplement,
                enabled = settings.master,
                onChecked = { onChange(settings.copy(supplement = it)) },
            )
            ToggleRow(
                title = "🗓️ 行程",
                subtitle = "体检与自定义行程，按提前量",
                checked = settings.agenda,
                enabled = settings.master,
                onChecked = { onChange(settings.copy(agenda = it)) },
            )
            ToggleRow(
                title = "📷 关键节点",
                subtitle = "满月 / 百天 / 半岁 / 周岁前 1 天",
                checked = settings.milestone,
                enabled = settings.master,
                onChecked = { onChange(settings.copy(milestone = it)) },
            )
            ToggleRow(
                title = "🍼 喂奶间隔",
                subtitle = "距下次喂奶归零时（间隔在上方设置）",
                checked = settings.feedInterval,
                enabled = settings.master,
                onChecked = { onChange(settings.copy(feedInterval = it)) },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "提醒本地生成不上传；点击通知直达对应页面",
                style = MaterialTheme.typography.labelSmall,
                color = c.text3,
            )
        }
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val c = BabyTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = c.surfaceAlt,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) c.text1 else c.text3,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.text3)
                }
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChecked)
        }
    }
}
