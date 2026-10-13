package com.tbtechs.focusflow.ui.legal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.launch
import java.util.Locale

private const val PRIVACY_URL = "https://focusflowapp.pages.dev/privacy-policy/"
private const val TERMS_URL = "https://focusflowapp.pages.dev/terms-of-service/"

private data class PolicyItem(val title: String, val body: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    settingsRepository: SettingsRepository,
    isRevisit: Boolean = false,
    onBack: () -> Unit,
    onAccepted: () -> Unit,
    onDeclineExit: (() -> Unit)? = null,
) {
    val dimensions = LocalFocusFlowDimensions.current
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
                PolicyItem("关于本政策", "本隐私政策适用于由 TBTechs 开发的 FocusFlow，规范您在 Android 设备上使用 FocusFlow 的相关行为。", Icons.Outlined.Info),
                PolicyItem("本地优先存储", "任务、日程、屏蔽列表、使用限额和设置，仅存储于本设备。只有您主动提交诊断报告时，技术日志才会离开设备。", Icons.Outlined.Smartphone),
                PolicyItem("Android 权限", "无障碍服务、使用情况统计和悬浮窗权限仅用于检测前台应用、显示屏蔽界面并保持专注会话运行，绝不用于数据收集。", Icons.Outlined.Security),
                PolicyItem("不读取消息或密码", "无障碍服务仅读取前台应用包名以触发屏蔽。FocusFlow 不会捕获密码、消息、表单、剪贴板或屏幕录像。", Icons.Outlined.VisibilityOff),
                PolicyItem("照片保持私密", "自定义屏蔽壁纸会复制到应用私有存储区，不会上传、共享或允许其他应用访问。", Icons.Outlined.Image),
                PolicyItem("您的控制权", "您可随时在 Android 设置中撤销权限。清除应用数据会永久删除设备上的 FocusFlow 数据，不存在云端备份。", Icons.Outlined.Settings),
                PolicyItem("儿童隐私", "FocusFlow 不收集个人信息，适合所有年龄段使用，不涉及账户、数据分析或广告。", Icons.Outlined.People),
                PolicyItem("政策变更", "政策可能随应用功能变化而更新。继续使用 FocusFlow 即表示接受更新后的政策。", Icons.Outlined.Refresh),
            )
        } else {
            listOf(
                PolicyItem("About this policy", "This Privacy Policy applies to FocusFlow, developed by TBTechs. It governs your use of the FocusFlow application on Android devices.", Icons.Outlined.Info),
                PolicyItem("Local-first data", "Tasks, schedules, block lists, allowances, and settings are stored exclusively in FocusFlow's on-device SQLite database and Android SharedPreferences. Technical diagnostics leave the device only when you intentionally submit an issue report.", Icons.Outlined.Smartphone),
                PolicyItem("Android permissions", "FocusFlow requests special Android access (Accessibility Service, Usage Stats, Draw over Other Apps) strictly to detect the foreground app, show blocking overlays, and keep focus sessions running. These are never used for data collection.", Icons.Outlined.Security),
                PolicyItem("No message or password collection", "The Accessibility Service reads only the foreground package name to trigger app blocking. FocusFlow does not capture passwords, messages, form entries, clipboard contents, or screen recordings — ever.", Icons.Outlined.VisibilityOff),
                PolicyItem("Photos stay private", "If you set a custom block-screen wallpaper, FocusFlow copies the image into app-private storage. It is never uploaded, shared, or accessible to other apps.", Icons.Outlined.Image),
                PolicyItem("Your control", "You can revoke any permission in Android Settings at any time. Clearing app data permanently removes all FocusFlow data from the device. No cloud backup exists.", Icons.Outlined.Settings),
                PolicyItem("Children's privacy", "FocusFlow does not collect personal information and is safe for all ages. No accounts, analytics, or advertising are involved.", Icons.Outlined.People),
                PolicyItem("Policy changes", "This Privacy Policy may be updated at any time without prior notice. Continued use of FocusFlow after any change constitutes your acceptance of the revised policy. The current version is always the one in effect.", Icons.Outlined.Refresh),
            )
        }
    }
    val termsCards = remember(chinese) {
        if (chinese) {
            listOf(
                PolicyItem("接受条款", "使用 FocusFlow 即表示您接受这些条款。若不同意，请停止使用并卸载应用。", Icons.Outlined.Assignment),
                PolicyItem("无保证", "FocusFlow 按现状提供，不保证始终可用或能够阻止所有应用。", Icons.Outlined.Warning),
                PolicyItem("紧急访问", "请始终保留电话、紧急联系人和其他必要系统功能的访问权限。FocusFlow 不是紧急安全工具。", Icons.Outlined.Phone),
                PolicyItem("责任限制", "在法律允许的最大范围内，TBTechs 不对因使用应用产生的损失承担责任。", Icons.Outlined.Gavel),
                PolicyItem("无障碍服务披露", "无障碍服务只用于前台应用检测和屏蔽执行，不读取消息、密码或屏幕内容。", Icons.Outlined.Accessibility),
                PolicyItem("联系我们", "如有疑问，请发送邮件至 tbtechsdev@gmail.com 或访问 focusflowapp.pages.dev。", Icons.Outlined.Email),
            )
        } else {
            listOf(
                PolicyItem("Acceptance of terms", "By using FocusFlow you agree to these Terms of Service. These Terms may be changed at any time without prior notice. Continued use constitutes immediate, irrevocable acceptance of the current Terms.", Icons.Outlined.Assignment),
                PolicyItem("No warranty", "FocusFlow is provided \"AS IS\" without any warranty of any kind. We make no guarantee it will function on your device, Android version, or OEM configuration. Blocking relies on Android permissions the OS may revoke at any time, for any reason.", Icons.Outlined.Warning),
                PolicyItem("Emergency access", "FocusFlow attempts to permit emergency calls, but makes NO guarantee whatsoever that emergency services (112, 911, 999, or any equivalent) will be reachable during an active session. Device or OS restrictions may override any permission. Do not rely on FocusFlow in any safety-critical or emergency situation.", Icons.Outlined.Phone),
                PolicyItem("Limitation of liability", "To the maximum extent permitted by law, TBTechs shall not be liable for any damages — including missed emergencies, personal injury, or data loss — arising from use of or inability to use FocusFlow, regardless of cause or legal theory.", Icons.Outlined.Gavel),
                PolicyItem("Accessibility Service disclosure", "FocusFlow's Accessibility Service is used solely to detect foreground apps and enforce blocking rules you configure. It is not used for any other purpose. This declaration is required by Google Play policy.", Icons.Outlined.Accessibility),
                PolicyItem("Contact", "For questions, email tbtechsdev@gmail.com or visit focusflowapp.pages.dev.", Icons.Outlined.Email),
            )
        }
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    fun accept() {
        if (!accepted || accepting) return
        scope.launch {
            accepting = true
            runCatching {
                settingsRepository.putString("privacy_accepted", "true")
                onAccepted()
            }
            accepting = false
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            if (isRevisit) {
                TopAppBar(
                    title = { Text("Privacy & Terms", color = DarkTextPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimensions.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Section with Lock Circle Icon matching 0a.jpg
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BrandPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = if (chinese) "隐私与条款" else "Privacy & Terms",
                    fontSize = 24.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Text(
                    text = if (chinese) "您的数据绝不会离开此设备。" else "Your data never leaves this device.",
                    fontSize = 14.scaledSp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            // Tab Segment Control matching 0a.jpg
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val isPrivacy = activeTab == "privacy"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPrivacy) Color.White else Color.Transparent)
                        .clickable { activeTab = "privacy" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Policy,
                            contentDescription = null,
                            tint = if (isPrivacy) BrandPrimary else DarkTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (chinese) "隐私政策" else "Privacy Policy",
                            fontSize = 14.scaledSp,
                            fontWeight = if (isPrivacy) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPrivacy) BrandPrimary else DarkTextSecondary,
                        )
                    }
                }

                val isTerms = activeTab == "terms"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isTerms) Color.White else Color.Transparent)
                        .clickable { activeTab = "terms" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Gavel,
                            contentDescription = null,
                            tint = if (isTerms) BrandPrimary else DarkTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (chinese) "服务条款" else "Terms of Service",
                            fontSize = 14.scaledSp,
                            fontWeight = if (isTerms) FontWeight.Bold else FontWeight.Medium,
                            color = if (isTerms) BrandPrimary else DarkTextSecondary,
                        )
                    }
                }
            }

            // Cards Content
            if (activeTab == "privacy") {
                privacyCards.forEach { item ->
                    PolicyCardRow(item.title, item.body, item.icon)
                }
                ExternalLinkPill(
                    label = if (chinese) "在线阅读完整隐私政策" else "Read the full Privacy Policy online",
                    onClick = { openUrl(PRIVACY_URL) },
                )
            } else {
                termsCards.forEach { item ->
                    PolicyCardRow(item.title, item.body, item.icon)
                }
                ExternalLinkPill(
                    label = if (chinese) "在线阅读完整服务条款" else "Read the full Terms of Service online",
                    onClick = { openUrl(TERMS_URL) },
                )
            }

            if (!isRevisit) {
                // Agreement Box matching 0b.jpg
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = accepted,
                            onCheckedChange = { accepted = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BrandPrimary,
                                uncheckedColor = DarkTextMuted,
                                checkmarkColor = Color.White,
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (chinese) "我已阅读并同意隐私政策和服务条款。" else "I have read and agree to the Privacy Policy and Terms of Service.",
                            color = DarkTextPrimary,
                            fontSize = 14.scaledSp,
                            lineHeight = 20.scaledSp,
                            modifier = Modifier.clickable { accepted = !accepted },
                        )
                    }
                }

                // Continue Button matching 0b.jpg
                Button(
                    onClick = ::accept,
                    enabled = accepted && !accepting,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = DarkTextMuted,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (accepting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (chinese) "我理解并继续" else "I Understand and Continue",
                                fontSize = 16.scaledSp,
                                fontWeight = FontWeight.Bold,
                                color = if (accepted) Color.White else DarkTextMuted,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = if (accepted) Color.White else DarkTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Decline & Exit Button matching 0b.jpg
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .clickable { declineDialog = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ExitToApp,
                            contentDescription = null,
                            tint = DarkTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (chinese) "拒绝并退出" else "Decline & Exit",
                            fontSize = 14.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextSecondary,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = DarkTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Back to Settings", color = DarkTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (declineDialog) {
        AlertDialog(
            onDismissRequest = { declineDialog = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text(if (chinese) "退出 FocusFlow？" else "Leave FocusFlow?") },
            text = { Text(if (chinese) "需要同意这些条款才能继续使用 FocusFlow。" else "You need to accept these terms to continue using FocusFlow.") },
            confirmButton = {
                Button(
                    onClick = {
                        declineDialog = false
                        (onDeclineExit ?: { (context as? Activity)?.finishAndRemoveTask() })()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(if (chinese) "拒绝并退出" else "Decline & Exit", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { declineDialog = false }) {
                    Text("Go Back", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun PolicyCardRow(title: String, body: String, icon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    fontSize = 16.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
            }
            Text(
                text = body,
                fontSize = 14.scaledSp,
                lineHeight = 20.scaledSp,
                color = DarkTextSecondary,
            )
        }
    }
}

@Composable
private fun ExternalLinkPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.OpenInNew,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 14.scaledSp,
                fontWeight = FontWeight.SemiBold,
                color = BrandPrimary,
            )
        }
    }
}
