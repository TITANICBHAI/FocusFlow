package com.tbtechs.focusflow.ui.customrules

import com.tbtechs.focusflow.ui.support.FocusFlowGuideItem

/** The numbers the guide quotes. The screen fills them from `CustomScreenRules` so they cannot drift. */
internal data class RulesGuideLimits(
    val maxRules: Int,
    val maxMatchers: Int,
    val maxFieldLength: Int,
    val maxImportChars: Int,
    val blockedPackages: Set<String>,
)

internal const val RULES_GUIDE_BETA_NOTE =
    "Beta. Screen rules read the element tree an app exposes to Android's accessibility service. Apps change " +
        "their screens and some hide elements, so a rule can stop working after an app update. " +
        "FocusFlow never takes a screenshot; the Screen view is rebuilt from element positions and text."

internal val RULES_GUIDE_STEPS_YOU = listOf(
    FocusFlowGuideItem(
        "1. Capture",
        "Custom Screen Rules → Add rule from screen → Start. Switch to the app and open the exact screen you want to " +
            "block. If it is a tab or a mode (the Reels tab, the Shorts player), tap it first so it is the highlighted " +
            "one, and leave it showing until the countdown ends. FocusFlow scans what is on screen at that moment " +
            "and tells you when it is done.",
    ),
    FocusFlowGuideItem(
        "2. Choose elements",
        "Open Choose element. On the Screen view, tap the part of the screen that only appears on the screen you want " +
            "blocked, then Add this element. The Text list tab shows the same elements with a search box.",
    ),
    FocusFlowGuideItem(
        "3. Test, save and turn on",
        "Name the rule. Before saving, use Test this rule in the editor: tap Start, switch to the app, open the " +
            "screen with its tab selected, and wait. It says whether the rule would block that screen and what each " +
            "element found. Save it, then switch it on in My rules. A new rule starts switched off.",
    ),
)

internal const val RULES_GUIDE_SELECT_FIRST_TITLE = "Select it first, then capture"

internal const val RULES_GUIDE_SELECT_FIRST_BODY =
    "A rule works like the Block YouTube Shorts and Block Instagram Reels switches: it only blocks while that " +
        "screen is actually showing, not whenever the app is open. FocusFlow can only tell if you show it the screen " +
        "while it is selected. So open the screen you want blocked, tap its tab so it is highlighted, and capture " +
        "then. Elements that are hidden, off screen or on another page are ignored, and a tab you captured while " +
        "it was highlighted only counts while it is highlighted. If you capture from the home screen, the rule " +
        "cannot know which screen you meant."

internal val RULES_GUIDE_GOOD_ELEMENTS = listOf(
    FocusFlowGuideItem(RULES_GUIDE_SELECT_FIRST_TITLE, RULES_GUIDE_SELECT_FIRST_BODY),
    FocusFlowGuideItem(
        "Prefer elements with an id",
        "An id such as reel_recycler survives most changes. Elements drawn faintly on the Screen view have no id or text and are weaker.",
    ),
    FocusFlowGuideItem(
        "Be careful with text",
        "Text can change with the language or the content. A fixed label such as a tab name is safe; a video title is not.",
    ),
    FocusFlowGuideItem(
        "Use one to three elements",
        "Match all means every element must be present, which is stricter. Match any means one is enough, which is broader.",
    ),
    FocusFlowGuideItem(
        "Test it",
        "Use Test this rule on the target screen: it should say the rule would block it. Then test one of the app's " +
            "other screens (home, search, profile): it should say the rule would not block it. If it would, the rule " +
            "matches something that is on every screen: pick again from a capture taken with the target screen " +
            "selected. If a tab shows \"found, but not selected\", select that tab first or turn off Only when selected.",
    ),
)

internal val RULES_GUIDE_STEPS_AGENT = listOf(
    FocusFlowGuideItem(
        "1. Export the scan",
        "Capture the screen as above, with the screen or tab you want blocked already selected, open Choose element " +
            "and tap Export. This shares a text list of every visible element on that screen.",
    ),
    FocusFlowGuideItem(
        "2. Copy the prompt",
        "Tap Copy screen-rule prompt on this page.",
    ),
    FocusFlowGuideItem(
        "3. Give both to an AI assistant",
        "Paste the prompt, then the scan, then one sentence about what to block. Use whichever assistant you trust; " +
            "the scan contains text that was visible on that screen, so check it first.",
    ),
    FocusFlowGuideItem(
        "4. Paste the answer back",
        "Copy the JSON it returns. In Custom Screen Rules open Import / export, paste it, and review. " +
            "Imported rules start switched off until you turn them on.",
    ),
    FocusFlowGuideItem(
        "5. Test before turning on",
        "In My rules tap the imported rule and use Test this rule: once on the target screen (it should say it would " +
            "block) and once on another screen of the app (it should say it would not). If it gets either wrong, " +
            "tell the assistant what the test said for each element and ask it to adjust the rule.",
    ),
)

internal const val RULES_GUIDE_AGENT_NOTE =
    "You do not have to use an assistant. It is only a shortcut for writing the rule. FocusFlow checks every imported " +
        "rule and refuses ones that are invalid."

internal const val RULES_AGENT_EXAMPLE = """{
  "format": "focusflow.screenrules",
  "version": 1,
  "rules": [
    {
      "name": "Example: Reels tab",
      "packageName": "com.example.social",
      "matchMode": "ALL",
      "matchers": [
        { "viewIdSuffix": "reel_recycler" },
        { "descContains": "Reels", "className": "android.widget.ImageView", "selectedOnly": true }
      ]
    }
  ]
}"""

/** The prompt a person pastes into an AI assistant, together with a scan export. */
internal fun customRulesAgentPrompt(limits: RulesGuideLimits): String = """
# FocusFlow screen-rule assistant (beta)

FocusFlow is an Android focus app. A custom screen rule blocks ONE screen inside an app (for example a short-video
feed) instead of the whole app: when elements that match the rule are VISIBLE on screen, FocusFlow blocks it. It works like
FocusFlow's built-in "Block YouTube Shorts" and "Block Instagram Reels" switches: the rest of the app stays usable. Your job is to
write rules as JSON that I paste into FocusFlow (Custom Screen Rules → Import / export → Import).

This prompt is self-contained. Do not ask for project files or links.

## What I give you

1. A "FocusFlow screen scan" exported from FocusFlow. Each line is one element of the captured screen:
   `[index] ClassName id=<view id> text="…" desc="…" selected=true bounds=left,top,right,bottom`
   Indentation shows nesting. Only `id`, `text`, `desc`, the class name and `selected` can be matched. `bounds` is only
   for understanding where an element is on screen. `selected=true` marks the highlighted tab or chosen item. Only
   elements that were visible on screen are listed. The first lines name the app package.
2. One sentence about what I want blocked.

If the scan is missing, ask me for it and say how: Custom Screen Rules → Add rule from screen → capture → Choose element → Export.
Never invent ids, text or package names. Use only what is in the scan.

## Output format (exact)

Return ONE JSON object and nothing else inside the code block:

${RULES_AGENT_EXAMPLE}

Rule fields:
- `name`: short label for the rule, at most ${limits.maxFieldLength} characters.
- `packageName`: the app package from the scan header. Never one of these (FocusFlow refuses them): ${limits.blockedPackages.sorted().joinToString(", ")}, or FocusFlow itself.
- `matchMode`: `"ALL"` (every matcher must be found somewhere on screen) or `"ANY"` (one is enough). Default to `"ALL"`.
- `matchers`: 1 to ${limits.maxMatchers} objects. Each may have these fields, and EVERY field you include must match the same element:
  - `viewIdSuffix`: end of the view id, the part after `/` (e.g. `reel_recycler`). Compared with "ends with", ignoring case.
  - `className`: full class name, exact match, ignoring case.
  - `textContains`: text the element contains, ignoring case.
  - `descContains`: content description the element contains, ignoring case.
  - `selectedOnly`: `true` makes the matcher count only while that element is in its selected state. Use it for a tab or
    button that exists on every screen (for example the "Reels" tab) so the rule fires only while that tab is the
    highlighted one. Set it to `true` only when the scan shows `selected=true` on that element.
  A matcher with only `className` is refused as too broad. Always include an id, text or description with it.
- Do not add `id` or `enabled`; FocusFlow assigns a new id and switches imported rules off.
- Every field is at most ${limits.maxFieldLength} characters. At most ${limits.maxRules} rules per file and ${limits.maxImportChars} characters in total.

## How to choose matchers

- Pick elements that exist ONLY on the screen I want blocked, not on the app's other screens. A tab button that is always
  on screen (such as a bottom "Reels" tab) is only safe with `"selectedOnly": true`. If the scan alone cannot show that, say so and ask me to
  capture the app's other screens for comparison instead of guessing.
- The scan must have been captured WITH the target screen or tab selected. If the element for the screen I want shows no
  `selected=true`, or the scan looks like the app's home screen, do not guess: tell me to open that screen, tap its tab so
  it is highlighted, and capture again.
- Prefer `viewIdSuffix`. Skip ids that look machine-generated or very short (such as `a1b2` or `v`); they change with app updates.
- Use `textContains` or `descContains` only for fixed labels (tab names, buttons). Never for content that changes (titles, names, counts, times).
- Two or three matchers in `"ALL"` mode are safer than one. Use `"ANY"` only when several different elements each mean the same screen.
- Ignore elements that hold personal content such as messages, names or account details.

## Deliver

Give the JSON in one code block so I can copy it. After it, write at most four short lines: which screen each rule targets,
which elements you chose and why, and any risk (for example a text label that may change with the app language).
Do not claim the rule was tested in FocusFlow. Finish with one line telling me to open each imported rule in My rules and use
"Test this rule" twice: on the target screen with its tab selected (it should say the rule would block it) and on another screen
of the app (it should say it would not).

## If my test fails

If I come back saying Test this rule gave the wrong answer, ask what it said for each element. "found, but not selected" means
turn `selectedOnly` off, or I need to select that tab and test again. "found, but hidden" means that element is not on the visible
screen, so replace it. "not on this screen" means the text or id is wrong or changes. "would block" on a screen that should be
allowed means a matcher is on every screen, so swap or add one that exists only on the target screen. Then give me corrected JSON.

## My request

[Paste the FocusFlow screen scan here, then say what to block.]
""".trimIndent()
