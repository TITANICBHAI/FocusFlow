package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.scaledSp
import com.tbtechs.focusflow.ui.common.FocusFlowSwitch

internal val RefBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
internal val RefHeader: Color
    @Composable get() = MaterialTheme.colorScheme.surface
internal val RefCard: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
internal val RefBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline
internal val RefMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
internal val RefSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val RefText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
internal val RefGreen = Color(0xFF34D399)
internal val RefBlue = Color(0xFF3B82F6)
internal val RefAmber = Color(0xFFF59E0B)
internal val RefRed = Color(0xFFEF4444)

@Composable
internal fun FocusFlowInternalHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RefHeader)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = RefText,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.scaledSp,
                fontWeight = FontWeight.Bold,
                color = RefText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.scaledSp,
                    color = RefSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
internal fun FocusFlowInternalCard(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 16.dp,
    contentPadding: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(RefCard)
            .border(1.dp, RefBorder, RoundedCornerShape(radius))
            .padding(contentPadding),
        content = content,
    )
}

@Composable
internal fun FocusFlowModalCard(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 20.dp,
    contentPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(RefCard)
            .border(1.dp, RefBorder, RoundedCornerShape(radius))
            .padding(contentPadding),
        content = content,
    )
}

@Composable
internal fun FocusFlowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = BrandPrimary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else RefMuted.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.scaledSp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun FocusFlowSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(RefCard)
            .border(1.dp, RefBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = RefSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (enabled) RefSecondary else RefMuted,
            fontSize = 12.5.scaledSp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun FocusFlowModalField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    secure: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
) {
    var passwordVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
            color = RefText,
            fontSize = 14.scaledSp,
        ),
        visualTransformation = if (secure && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RefCard)
                    .border(1.dp, RefBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
             Text(placeholder, color = RefMuted, fontSize = 14.scaledSp)
                    }
                    innerTextField()
                }
                if (secure && onToggleVisibility != null) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = RefSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                passwordVisible = !passwordVisible
                                onToggleVisibility()
                            },
                    )
                }
            }
        },
    )
}

@Composable
internal fun ReferencePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color = BrandPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 10.dp,
    onClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) selectedColor else RefCard)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else RefBorder,
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else RefText,
             fontSize = fontSize ?: 14.scaledSp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun ReferenceSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.scaledSp,
        fontWeight = FontWeight.SemiBold,
        color = RefSecondary,
    )
}

@Composable
internal fun ReferenceField(
    value: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
            fontSize = 14.scaledSp,
            color = RefText,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight ?: 44.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight ?: 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RefCard)
                    .border(1.dp, RefBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                         Text(placeholder, color = RefMuted, fontSize = 14.scaledSp)
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
internal fun ReferenceToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RefCard)
            .border(1.dp, RefBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.scaledSp, fontWeight = FontWeight.SemiBold, color = RefText)
            Spacer(Modifier.size(2.dp))
            Text(description, fontSize = 12.scaledSp, color = RefSecondary)
        }
        FocusFlowSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
internal fun ReferenceTaskAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(4.dp))
         Text(label, fontSize = 11.scaledSp, fontWeight = FontWeight.SemiBold, color = color)
    }
}