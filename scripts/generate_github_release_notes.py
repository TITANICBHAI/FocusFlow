#!/usr/bin/env python3
"""Generate user-facing GitHub release notes from FocusFlow's in-app changelog."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any
from urllib.parse import quote


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
    if not isinstance(latest["sections"], list) or not latest["sections"]:
        raise ValueError("The newest in-app changelog entry must have at least one section.")

    for section in latest["sections"]:
        if (
            not isinstance(section, dict)
            or not isinstance(section.get("heading"), str)
            or not isinstance(section.get("items"), list)
            or not section["items"]
            or not all(isinstance(item, str) and item.strip() for item in section["items"])
        ):
            raise ValueError("Each changelog section must have a heading and non-empty text items.")
    return latest


def section_emoji(heading: str) -> str:
    heading = heading.lower()
    if any(word in heading for word in ("protect", "defense", "security")):
        return "🛡️"
    if any(word in heading for word in ("fix", "reliab", "stability")):
        return "🐛"
    if any(word in heading for word in ("focus", "session", "task")):
        return "🎯"
    if any(word in heading for word in ("block", "allowance")):
        return "🚫"
    if any(word in heading for word in ("permission", "setup")):
        return "⚙️"
    return "✨"


def render_release_notes(
    latest: dict[str, Any],
    *,
    version_name: str,
    repository: str,
    release_tag: str,
    source_sha: str,
    apk_arm64_name: str,
    apk_arm32_name: str,
    apk_universal_name: str,
) -> str:
    if latest["version"] != version_name:
        raise ValueError(
            f"Gradle version {version_name!r} does not match the newest in-app changelog "
            f"version {latest['version']!r}."
        )

    repository_url = f"https://github.com/{repository}"
    release_url = f"{repository_url}/releases/download/{quote(release_tag, safe='')}"
    lines = [
        '<div align="center">',
        "",
        f"# 🎯 FocusFlow v{version_name}",
        "",
        "**More focus. Fewer distractions.**",
        "",
        f"_{latest['date']}_",
        "",
        "</div>",
        "",
        "---",
        "",
        "## ✨ What's New",
        "",
        "| | Update |",
        "|:---:|---|",
    ]

    for section in latest["sections"]:
        emoji = section_emoji(section["heading"])
        heading = section["heading"].replace("|", "\\|")
        for item in section["items"]:
            lines.append(f"| {emoji} | **{heading}** — {item.replace('|', '\\|')} |")

    lines.extend(
        [
            "",
            "---",
            "",
            "## 📥 Downloads",
            "",
            "| Device | Download |",
            "|---|---|",
            f"| **ARM64** · most newer Android phones | [Download APK]({release_url}/{quote(apk_arm64_name)}) |",
            f"| **ARM32** · older 32-bit devices | [Download APK]({release_url}/{quote(apk_arm32_name)}) |",
            f"| **Universal** · not sure which to choose? | [Download APK]({release_url}/{quote(apk_universal_name)}) |",
            "",
            "> Download and install **one** APK. Universal is the easiest choice if you are unsure.",
            "",
            "## 📲 Install",
            "",
            "1. Download the APK that matches your device.",
            "2. Open the downloaded file and tap **Install**.",
            "3. If Android asks, allow your browser or file manager to install apps, then try again.",
            "",
            "Requires Android 10 or later.",
            "",
            "---",
            "",
            f"<sub>Built from [{source_sha[:7]}]({repository_url}/commit/{source_sha}).</sub>",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--changelog", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--apk-arm64-name", required=True)
    parser.add_argument("--apk-arm32-name", required=True)
    parser.add_argument("--apk-universal-name", required=True)
    args = parser.parse_args()

    latest = read_latest_entry(args.changelog)
    notes = render_release_notes(
        latest,
        version_name=args.version_name,
        repository=args.repository,
        release_tag=args.release_tag,
        source_sha=args.source_sha,
        apk_arm64_name=args.apk_arm64_name,
        apk_arm32_name=args.apk_arm32_name,
        apk_universal_name=args.apk_universal_name,
    )
    args.output.write_text(notes, encoding="utf-8")


if __name__ == "__main__":
    main()
