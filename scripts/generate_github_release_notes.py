#!/usr/bin/env python3
"""Generate GitHub release notes from the changelog shipped in the Android app."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


def read_latest_entry(changelog_path: Path) -> dict[str, Any]:
    with changelog_path.open(encoding="utf-8") as source:
        changelog = json.load(source)
    if not isinstance(changelog, list) or not changelog:
        raise ValueError("The changelog must be a non-empty JSON array.")

    latest = changelog[0]
    if not isinstance(latest, dict):
        raise ValueError("The newest changelog entry must be an object.")
    for key in ("version", "date", "sections"):
        if key not in latest:
            raise ValueError(f"The newest changelog entry is missing {key!r}.")
    if not isinstance(latest["sections"], list) or not latest["sections"]:
        raise ValueError("The newest changelog entry must have at least one section.")

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


def render_release_notes(
    latest: dict[str, Any],
    *,
    version_name: str,
    version_code: str,
    build_run_number: str,
    build_run_url: str,
    source_sha: str,
    apk_arm64_name: str,
    apk_arm64_sha256: str,
    apk_arm32_name: str,
    apk_arm32_sha256: str,
    apk_universal_name: str,
    apk_universal_sha256: str,
) -> str:
    if latest["version"] != version_name:
        raise ValueError(
            f"Gradle version {version_name!r} does not match the newest changelog "
            f"version {latest['version']!r}."
        )

    for checksum in (apk_arm64_sha256, apk_arm32_sha256, apk_universal_sha256):
        if not re.fullmatch(r"[0-9a-fA-F]{64}", checksum):
            raise ValueError("Every APK must have a valid 64-character SHA-256 checksum.")

    lines = [
        f"# FocusFlow v{version_name}",
        "",
        "FocusFlow is an Android productivity app from TBTechs that helps you protect time "
        "for focused work. It combines timed focus sessions, scheduled and standalone app "
        "blocking, daily allowances, keyword blocking, optional VPN-based network protection, "
        "and device safeguards.",
        "",
        "## Release details",
        f"- **Version:** {version_name} (version code {version_code})",
        f"- **Build:** [GitHub Actions run #{build_run_number}]({build_run_url})",
        f"- **Source commit:** `{source_sha}`",
        "",
        f"## What's new in v{version_name}",
        f"_From the in-app changelog — {latest['date']}._",
        "",
    ]

    for section in latest["sections"]:
        lines.append(f"### {section['heading']}")
        lines.extend(f"- {item}" for item in section["items"])
        lines.append("")

    lines.extend(
        [
            "## Download",
            "These are complete, signed APKs—not split APK parts. Install **one** APK only.",
            "The ARM-specific downloads are smaller for compatible devices; choose Universal "
            "if you are unsure which architecture your device uses.",
            "",
            f"- **ARM64:** `{apk_arm64_name}` — SHA-256 `{apk_arm64_sha256}`",
            f"- **ARM32:** `{apk_arm32_name}` — SHA-256 `{apk_arm32_sha256}`",
            f"- **Universal:** `{apk_universal_name}` — SHA-256 `{apk_universal_sha256}`",
            "",
            "## Install on Android",
            "1. Download one APK from the assets attached to this release. Universal is the "
            "simplest choice for most users; select ARM64 or ARM32 only if you know it matches "
            "your device.",
            "2. Open the downloaded file from Downloads or your file manager and tap **Install**.",
            "3. If Android blocks the install, allow **Install unknown apps** for the browser or "
            "file manager you used, return to the APK, and retry. You can turn that allowance "
            "off afterward.",
            "4. Keep Play Protect enabled. Check that the file came from this official release "
            "and compare its SHA-256 value above if you want to verify the download.",
            "",
            "The app requires Android 10 or later. Review FocusFlow's in-app permission guidance "
            "when enabling its focus and protection features.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--changelog", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--build-run-number", required=True)
    parser.add_argument("--build-run-url", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--apk-arm64-name", required=True)
    parser.add_argument("--apk-arm64-sha256", required=True)
    parser.add_argument("--apk-arm32-name", required=True)
    parser.add_argument("--apk-arm32-sha256", required=True)
    parser.add_argument("--apk-universal-name", required=True)
    parser.add_argument("--apk-universal-sha256", required=True)
    args = parser.parse_args()

    latest = read_latest_entry(args.changelog)
    notes = render_release_notes(
        latest,
        version_name=args.version_name,
        version_code=args.version_code,
        build_run_number=args.build_run_number,
        build_run_url=args.build_run_url,
        source_sha=args.source_sha,
        apk_arm64_name=args.apk_arm64_name,
        apk_arm64_sha256=args.apk_arm64_sha256,
        apk_arm32_name=args.apk_arm32_name,
        apk_arm32_sha256=args.apk_arm32_sha256,
        apk_universal_name=args.apk_universal_name,
        apk_universal_sha256=args.apk_universal_sha256,
    )
    args.output.write_text(notes, encoding="utf-8")


if __name__ == "__main__":
    main()
