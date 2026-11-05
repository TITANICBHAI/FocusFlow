#!/usr/bin/env python3
"""Build the GitHub release notes for a FocusFlow (tbtechs) APK release.

Everything on the release page is generated from files that already live in the
repository, so there is nothing to copy by hand when you ship:

  app/src/main/assets/changelog.json   "What's New" (the same text users see in the app)
  app/build.gradle.kts                 version, build number, minimum Android version
  the staged APK files                 download links and file sizes
  git tags                             "Full changelog" link to the previous release

The only fixed copy (tagline and install guide) lives in the constants below.
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence
from urllib.parse import quote

TAGLINE = "More focus. Fewer distractions."

SAI_URL = "https://play.google.com/store/apps/details?id=com.mtv.sai"

# (heading, body) pairs shown under "Install", in order.
INSTALL_STEPS = (
    (
        "1. Allow APK installation",
        "If Android blocks the install, tap **Settings** in the prompt and allow "
        "**Install unknown apps** for the browser or file manager you used. For SAI, allow it "
        "for SAI instead. The setting is also under **Settings → Apps → Special app access → "
        "Install unknown apps** on many devices.",
    ),
    (
        "2. Install with Android or SAI",
        "Download **one** APK above and open it with Android’s installer, or install "
        f"[SAI from Google Play]({SAI_URL}) and choose the downloaded APK. SAI can install these "
        "standalone APKs while leaving Play Protect on; Play Protect may still scan the APK and "
        "show a warning.",
    ),
    (
        "3. If Play Protect warns",
        "Only continue with an APK downloaded from this official FocusFlow release. If Play "
        "Protect offers **More details → Install anyway**, use it only if you trust the file. "
        "SAI is an alternate installer, not a way to disable Play Protect. If Android still "
        "blocks the official APK and you choose to proceed, temporarily turn off **Scan apps "
        "with Play Protect**: open **Google Play Store → profile picture → Play Protect → "
        "Settings (gear)**, turn off the scan, install the APK, then turn the scan back on "
        "immediately. Google recommends keeping Play Protect on.",
    ),
    (
        "4. Allow restricted settings (Android 13+)",
        "If FocusFlow’s **Accessibility** or **Device Admin** toggle is greyed out, open "
        "**Settings → Apps → FocusFlow → ⋮ → Allow restricted settings**, confirm, then return "
        "to FocusFlow and enable the permission. This is Android’s protection for sideloaded "
        "apps, not an app error.",
    ),
)

# API level -> marketing name, for the "Requires Android N or later" line.
ANDROID_VERSIONS = {
    21: "5.0", 22: "5.1", 23: "6", 24: "7", 25: "7.1", 26: "8", 27: "8.1", 28: "9",
    29: "10", 30: "11", 31: "12", 32: "12L", 33: "13", 34: "14", 35: "15", 36: "16",
}

# Emoji for each changelog section, picked from its heading. First matching rule wins.
SECTION_EMOJI = (
    (("protect", "defense", "security"), "🛡️"),
    (("fix", "reliab", "stability"), "🐛"),
    (("focus", "session", "task"), "🎯"),
    (("block", "allowance"), "🚫"),
    (("permission", "setup"), "⚙️"),
    (("launcher", "home"), "🏠"),
    (("photo", "image", "picture"), "🖼️"),
    (("import", "export", "backup"), "📦"),
    (("stats", "usage"), "📊"),
    (("theme", "design"), "🎨"),
)
DEFAULT_EMOJI = "✨"

_RELEASE_TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)(?:-vc(\d+))?$")


@dataclass(frozen=True)
class GradleMetadata:
    version_name: str
    version_code: int
    min_sdk: int


@dataclass(frozen=True)
class Apk:
    label: str
    hint: str
    name: str
    size_bytes: int

    @classmethod
    def from_file(cls, label: str, hint: str, path: Path) -> "Apk":
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"The {label} APK is missing or empty: {path}")
        return cls(label, hint, path.name, path.stat().st_size)


def clean(text: str) -> str:
    """Make changelog copy safe for Markdown: one line, HTML characters escaped."""
    return html.escape(" ".join(text.split()), quote=False)


def format_size(size_bytes: int) -> str:
    if size_bytes >= 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.1f} MB"
    return f"{max(1, round(size_bytes / 1024))} KB"


def android_label(api_level: int) -> str:
    name = ANDROID_VERSIONS.get(api_level)
    return f"Android {name}" if name else f"Android (API {api_level})"


def section_emoji(heading: str) -> str:
    lowered = heading.lower()
    for keywords, emoji in SECTION_EMOJI:
        if any(keyword in lowered for keyword in keywords):
            return emoji
    return DEFAULT_EMOJI


def read_latest_entry(changelog_path: Path) -> dict[str, Any]:
    with changelog_path.open(encoding="utf-8") as source:
        changelog = json.load(source)
    if not isinstance(changelog, list) or not changelog:
        raise ValueError("The in-app changelog must be a non-empty JSON array.")

    latest = changelog[0]
    if not isinstance(latest, dict):
        raise ValueError("The newest in-app changelog entry must be an object.")
    for key in ("version", "date", "sections"):
        if key not in latest:
            raise ValueError(f"The newest in-app changelog entry is missing {key!r}.")
    if not all(isinstance(latest[key], str) and latest[key].strip() for key in ("version", "date")):
        raise ValueError("The newest in-app changelog entry needs a text version and date.")
    if not isinstance(latest["sections"], list) or not latest["sections"]:
        raise ValueError("The newest in-app changelog entry must have at least one section.")

    for section in latest["sections"]:
        if (
            not isinstance(section, dict)
            or not isinstance(section.get("heading"), str)
            or not section["heading"].strip()
            or not isinstance(section.get("items"), list)
            or not section["items"]
            or not all(isinstance(item, str) and item.strip() for item in section["items"])
        ):
            raise ValueError("Each changelog section must have a heading and non-empty text items.")
    return latest


def read_gradle_metadata(gradle_path: Path) -> GradleMetadata:
    text = gradle_path.read_text(encoding="utf-8")

    def find(pattern: str, label: str) -> str:
        match = re.search(pattern, text, re.MULTILINE)
        if match is None:
            raise ValueError(f"Could not find {label} in {gradle_path}.")
        return match.group(1)

    return GradleMetadata(
        version_name=find(r'^\s*versionName\s*=\s*"([^"]+)"', "versionName"),
        version_code=int(find(r"^\s*versionCode\s*=\s*([0-9]+)", "versionCode")),
        min_sdk=int(find(r"^\s*minSdk\s*=\s*([0-9]+)", "minSdk")),
    )


def expected_release_tag(gradle: GradleMetadata) -> str:
    return f"v{gradle.version_name}-vc{gradle.version_code}"


def _tag_key(tag: str) -> tuple[int, int, int, int] | None:
    match = _RELEASE_TAG.match(tag)
    if match is None:
        return None
    major, minor, patch, build = match.groups()
    return int(major), int(minor), int(patch), int(build or 0)


def previous_release_tag(repo_dir: Path, source_sha: str, release_tag: str) -> str | None:
    """Newest release tag (vX.Y.Z or vX.Y.Z-vcN) older than this one and reachable from it."""
    try:
        result = subprocess.run(
            ["git", "-C", str(repo_dir), "tag", "--merged", source_sha, "--list", "v*"],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError:
        return None
    if result.returncode != 0:
        return None

    current = _tag_key(release_tag)
    best: tuple[tuple[int, int, int, int], str] | None = None
    for tag in result.stdout.split():
        key = _tag_key(tag)
        if key is None or tag == release_tag or (current is not None and key >= current):
            continue
        if best is None or key > best[0]:
            best = (key, tag)
    return best[1] if best else None


def render_release_notes(
    entry: dict[str, Any],
    *,
    version_name: str,
    min_sdk: int,
    repository: str,
    release_tag: str,
    source_sha: str,
    apks: Sequence[Apk],
    previous_tag: str | None = None,
) -> str:
    if entry["version"] != version_name:
        raise ValueError(
            f"Gradle version {version_name!r} does not match the newest in-app changelog "
            f"version {entry['version']!r}. Add a changelog entry for this release first."
        )

    repo_url = f"https://github.com/{repository}"
    download_url = f"{repo_url}/releases/download/{quote(release_tag, safe='')}"
    sections = [
        (section_emoji(section["heading"]), clean(section["heading"]), section["items"])
        for section in entry["sections"]
    ]

    lines = [
        '<div align="center">',
        "",
        f"# 🎯 FocusFlow v{version_name}",
        "",
        f"**{TAGLINE}**",
        "",
        f"_{clean(entry['date'])}_",
        "",
    ]
    if len(sections) > 1:
        lines += [" · ".join(f"{emoji} {heading}" for emoji, heading, _ in sections), ""]
    lines += ["</div>", "", "---", "", "## ✨ What’s New", ""]

    for emoji, heading, items in sections:
        lines += [f"### {emoji} {heading}", ""]
        lines += [f"- {clean(item)}" for item in items]
        lines.append("")

    lines += [
        "---",
        "",
        "## 📥 Downloads",
        "",
        "| Device | Size | Download |",
        "|:---|---:|:---:|",
    ]
    for apk in apks:
        link = f"[Download APK]({download_url}/{quote(apk.name)})"
        lines.append(f"| **{apk.label}** · {apk.hint} | {format_size(apk.size_bytes)} | {link} |")
    lines += [
        "",
        "> Download and install **one** APK. Universal is the easiest choice if you are unsure.",
        "",
        "## 📲 Install",
        "",
    ]
    for heading, body in INSTALL_STEPS:
        lines += [f"### {heading}", "", body, ""]
    lines += [
        f"Requires {android_label(min_sdk)} or later. "
        "Settings labels can vary by device manufacturer.",
        "",
        "---",
        "",
    ]

    footer = []
    if previous_tag:
        compare = f"{quote(previous_tag, safe='')}...{quote(release_tag, safe='')}"
        footer.append(f"[Full changelog since {previous_tag}]({repo_url}/compare/{compare})")
    footer += [
        f"Built from [{source_sha[:7]}]({repo_url}/commit/{source_sha})",
        f"[Report a problem]({repo_url}/issues/new/choose)",
        f"[Ask a question]({repo_url}/discussions)",
    ]
    lines += ["<sub>" + " · ".join(footer) + "</sub>", ""]
    return "\n".join(lines)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate GitHub release notes for FocusFlow.")
    parser.add_argument("--changelog", type=Path, required=True)
    parser.add_argument("--gradle", type=Path, required=True)
    parser.add_argument("--repository", required=True, help="owner/name, e.g. TITANICBHAI/FocusFlow")
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--apk-arm64", type=Path, required=True)
    parser.add_argument("--apk-arm32", type=Path, required=True)
    parser.add_argument("--apk-universal", type=Path, required=True)
    parser.add_argument("--repo-dir", type=Path, default=Path("."), help="git checkout used to find the previous release")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)

    try:
        entry = read_latest_entry(args.changelog)
        gradle = read_gradle_metadata(args.gradle)
        expected_tag = expected_release_tag(gradle)
        if args.release_tag != expected_tag:
            raise ValueError(
                f"Release tag {args.release_tag!r} does not match the Gradle version "
                f"(expected {expected_tag!r})."
            )
        apks = [
            Apk.from_file("ARM64", "most newer Android phones", args.apk_arm64),
            Apk.from_file("ARM32", "older 32-bit devices", args.apk_arm32),
            Apk.from_file("Universal", "not sure which to choose?", args.apk_universal),
        ]
        notes = render_release_notes(
            entry,
            version_name=gradle.version_name,
            min_sdk=gradle.min_sdk,
            repository=args.repository,
            release_tag=args.release_tag,
            source_sha=args.source_sha,
            apks=apks,
            previous_tag=previous_release_tag(args.repo_dir, args.source_sha, args.release_tag),
        )
    except (OSError, ValueError) as error:
        prefix = "::error::" if os.environ.get("GITHUB_ACTIONS") else "error: "
        print(f"{prefix}{error}", file=sys.stderr)
        return 1

    args.output.write_text(notes, encoding="utf-8")
    print(f"Wrote {args.output} ({len(notes.splitlines())} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
