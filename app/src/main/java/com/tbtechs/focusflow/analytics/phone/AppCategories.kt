package com.tbtechs.focusflow.analytics.phone

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure package → [UsageCategory] mapping. Never touches PackageManager (see [AppCategoryResolver] for that).
 *
 * Precedence, first match wins: (1) package override table, (2) `ApplicationInfo.category`, (3) the game flag,
 * (4) keyword fallback on package segments, (5) [UsageCategory.OTHER].
 * Browsers are TOOLS on purpose: what is browsed cannot be known, and the app does not guess.
 */
object AppCategoryMap {

    /** Exact package names (case-sensitive). Kept grouped by category so it can be read and audited. */
    internal val EXACT_OVERRIDES_BY_CATEGORY: Map<UsageCategory, List<String>> = mapOf(
        UsageCategory.SOCIAL to listOf(
            "com.instagram.android", "com.instagram.lite", "com.instagram.barcelona", "com.facebook.katana",
            "com.facebook.lite", "com.twitter.android", "com.twitter.android.lite", "com.snapchat.android",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.reddit.frontpage", "com.linkedin.android",
            "com.pinterest", "com.tumblr", "in.mohalla.sharechat", "in.mohalla.video", "com.bereal.ft", "com.tinder",
            "com.bumble.app", "com.quora.android",
        ),
        UsageCategory.COMMUNICATION to listOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
            "org.thoughtcrime.securesms", "com.discord", "com.facebook.orca", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.android.mms", "com.google.android.gm",
            "com.microsoft.office.outlook", "com.skype.raider", "com.viber.voip", "jp.naver.line.android",
            "com.google.android.apps.tachyon",
        ),
        UsageCategory.ENTERTAINMENT to listOf(
            "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.google.android.apps.youtube.kids", "com.netflix.mediaclient", "com.spotify.music",
            "tv.twitch.android.app", "com.amazon.avod.thirdpartyclient", "in.startv.hotstar",
            "com.jio.media.ondemand", "com.jio.jioplay.tv", "com.disney.disneyplus", "com.hbo.hbonow",
            "com.wbd.stream", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro", "com.gaana",
            "com.jio.media.jiobeats", "com.graymatrix.did", "com.sonyliv", "org.videolan.vlc", "com.amazon.mp3",
            "com.soundcloud.android", "com.apple.android.music", "tv.pluto.android", "com.hulu.plus",
            "com.google.android.videos",
        ),
        UsageCategory.GAMES to listOf(
            "com.mojang.minecraftpe", "com.roblox.client", "com.supercell.clashofclans", "com.supercell.clashroyale",
            "com.supercell.brawlstars", "com.supercell.hayday", "com.tencent.ig", "com.pubg.imobile",
            "com.activision.callofduty.shooter", "com.dts.freefireth", "com.dts.freefiremax",
            "com.king.candycrushsaga", "com.kiloo.subwaysurf", "com.innersloth.spacemafia",
            "com.miHoYo.GenshinImpact",
        ),
        UsageCategory.EDUCATION to listOf(
            "com.duolingo", "org.khanacademy.android", "com.byjus.thelearningapp", "com.unacademyapp",
            "org.coursera.android", "com.udemy.android", "com.google.android.apps.classroom",
            "com.quizlet.quizletandroid", "com.brainly", "com.babbel.mobile.android.en",
            "com.memrise.android.memrisecompanion", "xyz.penpencil.physicswala", "com.microblink.photomath",
            "com.ichi2.anki",
        ),
        UsageCategory.PRODUCTIVITY to listOf(
            "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
            "com.google.android.keep", "com.google.android.calendar", "com.google.android.apps.tasks",
            "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
            "com.microsoft.teams", "com.slack", "us.zoom.videomeetings", "com.notion.id", "md.obsidian",
            "com.todoist", "com.evernote", "com.ticktick.task", "com.trello", "com.asana.app",
        ),
        UsageCategory.NEWS_READING to listOf(
            "com.google.android.apps.magazines", "flipboard.app", "com.medium.reader", "com.eterno", "com.nis.app",
            "com.amazon.kindle", "com.google.android.apps.books", "com.ideashower.readitlater.pro", "org.wikipedia",
        ),
        UsageCategory.TOOLS to listOf(
            "com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
            "com.opera.browser", "com.sec.android.app.sbrowser", "com.google.android.apps.maps",
            "com.google.android.apps.photos", "com.google.android.dialer", "com.android.dialer",
            "com.android.settings", "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm",
            "com.google.android.apps.walletnfcrel", "com.android.camera", "com.google.android.GoogleCamera",
            "com.sec.android.app.camera", "com.google.android.apps.nbu.files", "com.android.vending",
            "com.google.android.googlequicksearchbox",
        ),
    )

    private val exactOverrides: Map<String, UsageCategory> = buildMap {
        for ((category, packages) in EXACT_OVERRIDES_BY_CATEGORY) {
            for (packageName in packages) put(packageName, category)
        }
    }

    /** Checked with `startsWith`, after the exact table. */
    private val prefixOverrides: List<Pair<String, UsageCategory>> = listOf(
        "com.supercell." to UsageCategory.GAMES,
        "com.king." to UsageCategory.GAMES,
        "com.miniclip." to UsageCategory.GAMES,
        "com.gameloft." to UsageCategory.GAMES,
        "com.ea.game." to UsageCategory.GAMES,
        "com.microsoft.office." to UsageCategory.PRODUCTIVITY,
        "com.google.android.apps.docs" to UsageCategory.PRODUCTIVITY,
    )

    /**
     * Keyword groups, evaluated in this order. A keyword shorter than 5 letters must equal a whole package
     * segment; a keyword of 5+ letters matches a segment that starts with it.
     */
    private val keywordGroups: List<Pair<UsageCategory, List<String>>> = listOf(
        UsageCategory.GAMES to listOf("game", "games", "casino", "ludo", "puzzle", "racing"),
        UsageCategory.EDUCATION to listOf("learn", "edu", "study", "course", "school", "tutor"),
        UsageCategory.SOCIAL to listOf("social"),
        UsageCategory.COMMUNICATION to listOf("messenger", "chat", "sms"),
        UsageCategory.ENTERTAINMENT to listOf("music", "video", "player", "tv", "stream", "movie"),
        UsageCategory.NEWS_READING to listOf("news", "reader", "magazine"),
        UsageCategory.TOOLS to listOf("calculator", "clock", "camera", "gallery", "files", "browser", "vpn", "wallet"),
    )

    private const val MIN_PREFIX_KEYWORD_LENGTH = 5

    fun categorize(packageName: String, apiCategory: Int?, isGameFlag: Boolean): UsageCategory {
        exactOverrides[packageName]?.let { return it }
        for ((prefix, category) in prefixOverrides) {
            if (packageName.startsWith(prefix)) return category
        }
        fromApiCategory(apiCategory)?.let { return it }
        if (isGameFlag) return UsageCategory.GAMES
        return fromKeywords(packageName) ?: UsageCategory.OTHER
    }

    private fun fromApiCategory(apiCategory: Int?): UsageCategory? = when (apiCategory) {
        ApplicationInfo.CATEGORY_SOCIAL -> UsageCategory.SOCIAL
        ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> UsageCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_GAME -> UsageCategory.GAMES
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> UsageCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_NEWS -> UsageCategory.NEWS_READING
        ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_IMAGE -> UsageCategory.TOOLS
        else -> null
    }

    private fun fromKeywords(packageName: String): UsageCategory? {
        val segments = packageName.lowercase().split('.', '_').filter { it.isNotEmpty() }
        for ((category, keywords) in keywordGroups) {
            val hit = keywords.any { keyword ->
                segments.any { segment ->
                    if (keyword.length < MIN_PREFIX_KEYWORD_LENGTH) segment == keyword else segment.startsWith(keyword)
                }
            }
            if (hit) return category
        }
        return null
    }
}

/** Looks a package up through PackageManager once per process and maps it with [AppCategoryMap]. */
class AppCategoryResolver(private val context: Context) {
    private val cache = ConcurrentHashMap<String, UsageCategory>()

    fun resolve(packageName: String): UsageCategory =
        cache.getOrPut(packageName) { lookup(packageName) }

    @Suppress("DEPRECATION")
    private fun lookup(packageName: String): UsageCategory = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val isGame = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        AppCategoryMap.categorize(packageName, info.category, isGame)
    } catch (_: PackageManager.NameNotFoundException) {
        AppCategoryMap.categorize(packageName, null, false)
    }
}
