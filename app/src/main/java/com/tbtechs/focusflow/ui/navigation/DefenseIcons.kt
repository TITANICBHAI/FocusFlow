package com.tbtechs.focusflow.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Shield-checkmark icons based on the supplied Ionicons geometry.
 */
internal object DefenseIcons {
    val Filled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheckmark",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M479.07 111.36a16 16 0 00-13.15-14.74c-86.5-15.52-122.61-26.74-203.33-63.2a16 16 0 00-13.18 0C168.69 69.88 132.58 81.1 46.08 96.62a16 16 0 00-13.15 14.74c-3.85 61.11 4.36 118.05 24.43 169.24A349.47 349.47 0 00129 393.11c53.47 56.73 110.24 81.37 121.07 85.73a16 16 0 0012 0c10.83-4.36 67.6-29 121.07-85.73a349.47 349.47 0 0071.5-112.51c20.07-51.19 28.28-108.13 24.43-169.24zm-131 75.11l-110.8 128A16 16 0 01225.86 320h-.66a16 16 0 01-11.2-4.57l-49.2-48.2a16 16 0 1122.4-22.86l37 36.29L323.9 165.53a16 16 0 0124.2 20.94z"
                ).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            )
        }.build()
    }

    val ActiveFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheckmarkActive",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(479.07f, 111.36f)
                curveTo(478.92f, 108.99f, 478.24f, 106.68f, 477.09f, 104.61f)
                curveTo(475.94f, 102.53f, 474.34f, 100.73f, 472.41f, 99.35f)
                curveTo(470.47f, 97.97f, 468.26f, 97.04f, 465.92f, 96.62f)
                curveTo(379.42f, 81.1f, 343.31f, 69.88f, 262.59f, 33.42f)
                curveTo(260.52f, 32.48f, 258.27f, 32f, 256f, 32f)
                curveTo(253.73f, 32f, 251.48f, 32.48f, 249.41f, 33.42f)
                curveTo(168.69f, 69.88f, 132.58f, 81.1f, 46.08f, 96.62f)
                curveTo(43.74f, 97.04f, 41.53f, 97.97f, 39.59f, 99.35f)
                curveTo(37.66f, 100.73f, 36.06f, 102.53f, 34.91f, 104.61f)
                curveTo(33.76f, 106.68f, 33.08f, 108.99f, 32.93f, 111.36f)
                curveTo(29.08f, 172.47f, 37.29f, 229.41f, 57.36f, 280.6f)
                curveTo(73.8f, 322.35f, 98.12f, 360.55f, 129f, 393.11f)
                curveTo(182.47f, 449.84f, 239.24f, 474.48f, 250.07f, 478.84f)
                curveTo(251.98f, 479.61f, 254.01f, 480.01f, 256.07f, 480.01f)
                curveTo(258.13f, 480.01f, 260.16f, 479.61f, 262.07f, 478.84f)
                curveTo(272.9f, 474.48f, 329.67f, 449.84f, 383.14f, 393.11f)
                curveTo(413.97f, 360.54f, 438.25f, 322.34f, 454.64f, 280.6f)
                curveTo(474.71f, 229.41f, 482.92f, 172.47f, 479.07f, 111.36f)
                close()
                moveTo(348.07f, 186.47f)
                lineTo(237.27f, 314.47f)
                curveTo(235.85f, 316.12f, 234.1f, 317.46f, 232.14f, 318.41f)
                curveTo(230.17f, 319.36f, 228.04f, 319.9f, 225.86f, 320f)
                lineTo(225.2f, 320f)
                curveTo(223.13f, 320f, 221.07f, 319.6f, 219.15f, 318.81f)
                curveTo(217.23f, 318.03f, 215.48f, 316.88f, 214f, 315.43f)
                lineTo(164.8f, 267.23f)
                curveTo(163.08f, 265.55f, 161.77f, 263.5f, 160.95f, 261.24f)
                curveTo(160.13f, 258.98f, 159.84f, 256.56f, 160.08f, 254.17f)
                curveTo(160.33f, 251.78f, 161.11f, 249.47f, 162.36f, 247.42f)
                curveTo(163.62f, 245.38f, 165.33f, 243.64f, 167.35f, 242.34f)
                curveTo(169.37f, 241.04f, 171.66f, 240.21f, 174.05f, 239.92f)
                curveTo(176.43f, 239.62f, 178.85f, 239.87f, 181.13f, 240.64f)
                curveTo(183.41f, 241.41f, 185.48f, 242.69f, 187.2f, 244.37f)
                lineTo(224.2f, 280.66f)
                lineTo(323.9f, 165.53f)
                curveTo(325.74f, 163.41f, 328.1f, 161.8f, 330.76f, 160.88f)
                curveTo(333.41f, 159.96f, 336.26f, 159.76f, 339.02f, 160.29f)
                curveTo(341.78f, 160.82f, 344.35f, 162.06f, 346.47f, 163.9f)
                curveTo(348.59f, 165.74f, 350.2f, 168.1f, 351.12f, 170.76f)
                curveTo(352.04f, 173.41f, 352.24f, 176.26f, 351.71f, 179.02f)
                curveTo(351.18f, 181.78f, 349.94f, 184.35f, 348.1f, 186.47f)
                close()
            }
        }.build()
    }

    val Outline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheckmarkOutline",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M336 176L225.2 304 176 255.8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 32f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M463.1 112.37C373.68 96.33 336.71 84.45 256 48c-80.71 36.45-117.68 48.33-207.1 64.37C32.7 369.13 240.58 457.79 256 464c15.42-6.21 223.3-94.87 207.1-351.63z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 32f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}