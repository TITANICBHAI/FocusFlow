package com.tbtechs.focusflow.ui.legal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import java.util.Locale

private const val PRIVACY_URL = "https://focusflowapp.pages.dev/privacy-policy/"
private const val TERMS_URL = "https://focusflowapp.pages.dev/terms-of-service/"

private data class PolicyCard(val title: String, val body: String)

@Composable
fun PrivacyPolicyScreen(
    settingsRepository: SettingsRepository,
    isRevisit: Boolean = false,
    onBack: () -> Unit,
    onAccepted: () -> Unit,
    onDeclineExit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chinese = remember { Locale.getDefault().language.startsWith("zh") }
    var activeTab by remember { mutableStateOf("privacy") }
    var accepted by remember { mutableStateOf(false) }
    var accepting by remember { mutableStateOf(false) }
    var declineDialog by remember { mutableStateOf(false) }

    val privacyCards = remember(chinese) {
        if (chinese) {
            listOf(
                PolicyCard("本地优先存储", "任务、日程、屏蔽列表、使用限额和设置，仅存储于本设备。只有您主动提交诊断报告时，技术日志才会离开设备。"),
                PolicyCard("Android 权限", "无障碍服务、使用情况统计和悬浮窗权限仅用于检测前台应用、显示屏蔽界面并保持专注会话运行，绝不用于数据收集。"),
                PolicyCard("不读取消息或密码", "无障碍服务仅读取前台应用包名以触发屏蔽。FocusFlow 不会捕获密码、消息、表单、剪贴板或屏幕录像。"),
                PolicyCard("照片保持私密", "自定义屏蔽壁纸会复制到应用私有存储区，不会上传、共享或允许其他应用访问。"),
                PolicyCard("您的控制权", "您可随时在 Android 设置中撤销权限。清除应用数据会永久删除设备上的 FocusFlow 数据，不存在云端备份。"),
                PolicyCard("儿童隐私", "FocusFlow 不收集个人信息，适合所有年龄段使用，不涉及账户、数据分析或广告。"),
                PolicyCard("政策变更", "政策可能随应用功能变化而更新。继续使用 FocusFlow 即表示接受更新后的政策。"),
            )
        } else {
            listOf(
                PolicyCard("Local-first data", "Tasks, schedules, block lists, allowances, and settings stay on this device in SQLite and Android SharedPreferences. Technical logs leave the device only when you intentionally submit an issue report."),
                PolicyCard("Android permissions", "Accessibility Service, Usage Stats, and Draw over Other Apps access are used strictly to detect the foreground app, show blocking overlays, and keep focus sessions running. They are never used for data collection."),
                PolicyCard("No message or password collection", "The Accessibility Service reads only the foreground package name to trigger blocking. FocusFlow does not capture passwords, messages, form entries, clipboard contents, or screen recordings."),
                PolicyCard("Photos stay private", "A custom block-screen wallpaper is copied into app-private storage. It is never uploaded, shared, or accessible to other apps."),
                PolicyCard("Your control", "You can revoke permissions in Android Settings at any time. Clearing app data permanently removes all FocusFlow data from this device. No cloud backup exists."),
                PolicyCard("Children's privacy", "FocusFlow does not collect personal information and is safe for all ages. There are no accounts, analytics, or ads."),
                PolicyCard("Policy changes", "This policy may be updated as FocusFlow changes. Continued use after an update means you accept the revised policy."),
            )
        }
    }
    val termsCards = if (chinese) {
        listOf(
            PolicyCard("接受条款", "使用 FocusFlow 即表示您接受这些条款。若不同意，请停止使用并卸载应用。"),
            PolicyCard("无保证", "FocusFlow 按现状提供，不保证始终可用或能够阻止所有应用。"),
            PolicyCard("紧急访问", "请始终保留电话、紧急联系人和其他必要系统功能的访问权限。FocusFlow 不是紧急安全工具。"),
            PolicyCard("责任限制", "在法律允许的最大范围内，TBTechs 不对因使用应用产生的损失承担责任。"),
            PolicyCard("无障碍服务披露", "无障碍服务只用于前台应用检测和屏蔽执行，不读取消息、密码或屏幕内容。"),
        )
    } else {
        listOf(
            PolicyCard("Acceptance of terms", "Using FocusFlow means you accept these Terms. If you do not agree, stop using the app and uninstall it."),
            PolicyCard("No warranty", "FocusFlow is provided as-is. We do not guarantee uninterrupted availability or that every app can always be blocked."),
            PolicyCard("Emergency access", "Always keep phone, emergency contacts, and other essential system functions available. FocusFlow is not an emergency-safety tool."),
            PolicyCard("Limitation of liability", "To the fullest extent permitted by law, TBTechs is not liable for losses arising from your use of the app."),
            PolicyCard("Accessibility Service disclosure", "The Accessibility Service is used only for foreground-app detection and blocking. It does not read messages, passwords, or screen content."),
        )
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun accept() {
        if (!accepted || accepting) return
        scope.launch {
            accepting = true
            runCatching {
                // The current Kotlin settings model has no privacyAccepted field.
                // Preserve the existing SharedPreferences contract until it is added.
                settingsRepository.putString("privacy_accepted", "true")
                onAccepted()
            }
            accepting = false
        }
    }

    Scaffold(
        topBar = {
            if (isRevisit) {
                TopAppBar(
                    title = { Text("Privacy & Terms") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("FocusFlow", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (chinese) "隐私政策与服务条款" else "Privacy Policy & Terms of Service",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == "privacy",
                    onClick = { activeTab = "privacy" },
                    label = { Text(if (chinese) "隐私政策" else "Privacy Policy") },
                )
                FilterChip(
                    selected = activeTab == "terms",
                    onClick = { activeTab = "terms" },
                    label = { Text(if (chinese) "服务条款" else "Terms") },
                )
            }

            if (activeTab == "privacy") {
                PolicyCardView(
                    title = if (chinese) "关于本政策" else "About this policy",
                    body = if (chinese) {
                        "本隐私政策适用于由 TBTechs 开发的 FocusFlow，规范您在 Android 设备上使用 FocusFlow 的相关行为。"
                    } else {
                        "This Privacy Policy applies to FocusFlow, developed by TBTechs. It governs your use of the FocusFlow application on Android devices."
                    },
                )
                privacyCards.forEach { PolicyCardView(it.title, it.body) }
                ExternalLinkButton("Read the full Privacy Policy online") { openUrl(PRIVACY_URL) }
            } else {
                termsCards.forEach { PolicyCardView(it.title, it.body) }
                PolicyCardView(
                    "Contact",
                    "For questions, email tbtechsdev@gmail.com or visit focusflowapp.pages.dev.",
                )
                ExternalLinkButton("Read the full Terms of Service online") { openUrl(TERMS_URL) }
            }

            if (!isRevisit) {
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                            Text(if (chinese) "我已阅读并同意隐私政策和服务条款" else "I have read and agree to the Privacy Policy and Terms of Service.")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openUrl(PRIVACY_URL) }) { Text("Privacy Policy") }
                            TextButton(onClick = { openUrl(TERMS_URL) }) { Text("Terms") }
                        }
                    }
                }
                Button(
                    onClick = ::accept,
                    enabled = accepted && !accepting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (accepting) CircularProgressIndicator() else Text(if (chinese) "继续" else "Continue")
                }
                OutlinedButton(onClick = { declineDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ExitToApp, contentDescription = null)
                    Text(if (chinese) "拒绝并退出" else "Decline & Exit")
                }
            } else {
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    Text("Back to Settings")
                }
            }
        }
    }

    if (declineDialog) {
        AlertDialog(
            onDismissRequest = { declineDialog = false },
            title = { Text(if (chinese) "退出 FocusFlow？" else "Leave FocusFlow?") },
            text = { Text(if (chinese) "需要同意这些条款才能继续使用 FocusFlow。" else "You need to accept these terms to continue using FocusFlow.") },
            confirmButton = {
                Button(onClick = {
                    declineDialog = false
                    (onDeclineExit ?: { (context as? Activity)?.finishAndRemoveTask() })()
                }) { Text(if (chinese) "拒绝并退出" else "Decline & Exit") }
            },
            dismissButton = { TextButton(onClick = { declineDialog = false }) { Text("Go Back") } },
        )
    }
}

@Composable
private fun PolicyCardView(title: String, body: String) {
    Card {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExternalLinkButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.OpenInNew, contentDescription = null)
        Text(label)
    }
}