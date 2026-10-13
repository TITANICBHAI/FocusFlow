package com.tbtechs.focusflow.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Focus tab icons, built from the Ionicons "timer" / "timer-outline" glyphs the
 * React Native tab bar renders.
 *
 * Active uses a filled disc with the ring and needle cut out.
 * Inactive uses one open stroked ring and one filled needle.
 */
internal object FocusTabIcons {
    private const val RING_STROKE = 42.67f

    val Active: ImageVector by lazy {
        ImageVector.Builder(
            name = "FocusTimerActive",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(256f, 48f)
                curveTo(141.12f, 48f, 48f, 141.12f, 48f, 256f)
                curveTo(48f, 370.88f, 141.12f, 464f, 256f, 464f)
                curveTo(370.88f, 464f, 464f, 370.88f, 464f, 256f)
                curveTo(464f, 141.12f, 370.88f, 48f, 256f, 48f)
                moveTo(173.67f, 162.34f)
                lineTo(278.67f, 233.34f)
                curveTo(283.26f, 236.64f, 286.9f, 241.09f, 289.25f, 246.23f)
                curveTo(291.59f, 251.38f, 292.56f, 257.05f, 292.04f, 262.68f)
                curveTo(291.53f, 268.31f, 289.55f, 273.71f, 286.31f, 278.34f)
                curveTo(283.07f, 282.97f, 278.67f, 286.68f, 273.56f, 289.1f)
                curveTo(268.45f, 291.51f, 262.8f, 292.55f, 257.16f, 292.11f)
                curveTo(251.52f, 291.68f, 246.1f, 289.78f, 241.42f, 286.6f)
                curveTo(238.32f, 284.41f, 235.61f, 281.7f, 233.42f, 278.6f)
                lineTo(162.42f, 173.6f)
                curveTo(161.7f, 172.56f, 161.22f, 171.37f, 161.04f, 170.12f)
                curveTo(160.86f, 168.86f, 160.97f, 167.59f, 161.37f, 166.39f)
                curveTo(161.77f, 165.18f, 162.44f, 164.09f, 163.34f, 163.2f)
                curveTo(164.23f, 162.3f, 165.32f, 161.63f, 166.53f, 161.23f)
                curveTo(167.73f, 160.83f, 169f, 160.72f, 170.26f, 160.9f)
                curveTo(171.51f, 161.08f, 172.7f, 161.56f, 173.74f, 162.28f)
                close()
                moveTo(256f, 432f)
                curveTo(159f, 432f, 80f, 353.05f, 80f, 256f)
                curveTo(79.92f, 232.33f, 84.65f, 208.89f, 93.91f, 187.1f)
                curveTo(103.17f, 165.32f, 116.77f, 145.65f, 133.87f, 129.28f)
                curveTo(135.39f, 127.76f, 137.24f, 126.61f, 139.27f, 125.92f)
                curveTo(141.3f, 125.22f, 143.47f, 124.99f, 145.6f, 125.26f)
                curveTo(147.73f, 125.52f, 149.78f, 126.27f, 151.58f, 127.44f)
                curveTo(153.38f, 128.61f, 154.89f, 130.18f, 155.99f, 132.02f)
                curveTo(157.1f, 133.86f, 157.77f, 135.93f, 157.96f, 138.07f)
                curveTo(158.14f, 140.21f, 157.84f, 142.37f, 157.07f, 144.38f)
                curveTo(156.3f, 146.38f, 155.08f, 148.18f, 153.51f, 149.65f)
                curveTo(139.16f, 163.39f, 127.75f, 179.9f, 119.98f, 198.18f)
                curveTo(112.21f, 216.46f, 108.23f, 236.13f, 108.3f, 256f)
                curveTo(108.3f, 337.44f, 174.56f, 403.7f, 256f, 403.7f)
                curveTo(337.44f, 403.7f, 403.7f, 337.44f, 403.7f, 256f)
                curveTo(403.7f, 179.33f, 344.98f, 116.12f, 270.15f, 109f)
                lineTo(270.15f, 164f)
                curveTo(270.15f, 166.13f, 269.67f, 168.22f, 268.75f, 170.14f)
                curveTo(267.83f, 172.05f, 266.48f, 173.74f, 264.82f, 175.06f)
                curveTo(263.16f, 176.39f, 261.22f, 177.32f, 259.15f, 177.8f)
                curveTo(257.08f, 178.27f, 254.92f, 178.27f, 252.85f, 177.8f)
                curveTo(250.78f, 177.32f, 248.84f, 176.39f, 247.18f, 175.06f)
                curveTo(245.52f, 173.74f, 244.17f, 172.05f, 243.25f, 170.14f)
                curveTo(242.33f, 168.22f, 241.85f, 166.13f, 241.85f, 164f)
                lineTo(241.85f, 94.15f)
                curveTo(241.85f, 92.29f, 242.22f, 90.45f, 242.93f, 88.74f)
                curveTo(243.64f, 87.02f, 244.68f, 85.46f, 245.99f, 84.14f)
                curveTo(247.31f, 82.83f, 248.87f, 81.79f, 250.59f, 81.08f)
                curveTo(252.3f, 80.37f, 254.14f, 80f, 256f, 80f)
                curveTo(353.05f, 80f, 432f, 159f, 432f, 256f)
                curveTo(432f, 353f, 353.05f, 432f, 256f, 432f)
                close()
            }
        }.build()
    }

    val Inactive: ImageVector by lazy {
        ImageVector.Builder(
            name = "FocusTimerInactive",
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = RING_STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(112.91f, 128f)
                curveTo(97.51f, 145.2f, 85.34f, 165.03f, 76.99f, 186.55f)
                curveTo(68.64f, 208.07f, 64.24f, 230.92f, 64f, 254f)
                curveTo(62.82f, 360.35f, 149.65f, 447.8f, 256f, 448f)
                curveTo(362.2f, 448.2f, 448f, 362.17f, 448f, 256f)
                curveTo(448f, 151.46f, 364.45f, 66.39f, 260.5f, 64f)
                curveTo(259.91f, 63.98f, 259.33f, 64.08f, 258.79f, 64.29f)
                curveTo(258.24f, 64.5f, 257.74f, 64.82f, 257.32f, 65.23f)
                curveTo(256.9f, 65.64f, 256.57f, 66.13f, 256.34f, 66.67f)
                curveTo(256.11f, 67.2f, 256f, 67.78f, 256f, 68.37f)
                lineTo(256f, 152f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(233.38f, 278.63f)
                lineTo(154.38f, 165.63f)
                curveTo(153.66f, 164.59f, 153.18f, 163.4f, 153f, 162.15f)
                curveTo(152.82f, 160.89f, 152.93f, 159.62f, 153.33f, 158.42f)
                curveTo(153.73f, 157.21f, 154.4f, 156.12f, 155.3f, 155.23f)
                curveTo(156.19f, 154.33f, 157.28f, 153.66f, 158.49f, 153.26f)
                curveTo(159.69f, 152.86f, 160.96f, 152.75f, 162.22f, 152.93f)
                curveTo(163.47f, 153.11f, 164.66f, 153.59f, 165.7f, 154.31f)
                lineTo(278.7f, 233.31f)
                curveTo(283.29f, 236.61f, 286.93f, 241.06f, 289.28f, 246.2f)
                curveTo(291.62f, 251.35f, 292.59f, 257.02f, 292.07f, 262.65f)
                curveTo(291.56f, 268.28f, 289.58f, 273.68f, 286.34f, 278.31f)
                curveTo(283.1f, 282.94f, 278.7f, 286.65f, 273.59f, 289.07f)
                curveTo(268.48f, 291.48f, 262.83f, 292.52f, 257.19f, 292.08f)
                curveTo(251.55f, 291.65f, 246.13f, 289.75f, 241.45f, 286.57f)
                curveTo(238.33f, 284.4f, 235.6f, 281.72f, 233.38f, 278.63f)
                close()
            }
        }.build()
    }
}