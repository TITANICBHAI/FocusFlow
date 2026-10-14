import json
import re
import tempfile
import unittest
from pathlib import Path
from typing import Any

from generate_github_release_notes import read_latest_entry, render_release_notes


ROOT = Path(__file__).resolve().parents[1]
CHANGELOG_PATH = ROOT / "app/src/main/assets/changelog.json"
GRADLE_PATH = ROOT / "app/build.gradle.kts"
SOURCE_SHA = "1234567890abcdef1234567890abcdef12345678"


def gradle_version() -> tuple[str, str]:
    gradle = GRADLE_PATH.read_text(encoding="utf-8")
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', gradle, re.MULTILINE)
    code = re.search(r"^\s*versionCode\s*=\s*([0-9]+)", gradle, re.MULTILINE)
    if name is None or code is None:
        raise AssertionError("Could not find versionName and versionCode in app/build.gradle.kts.")
    return name.group(1), code.group(1)


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.latest = read_latest_entry(CHANGELOG_PATH)
        self.version_name, self.version_code = gradle_version()

    def render(
        self,
        *,
        latest: dict[str, Any] | None = None,
        version_name: str | None = None,
    ) -> str:
        version = version_name or self.version_name
        tag = f"v{version}-vc{self.version_code}"
        return render_release_notes(
            latest or self.latest,
            version_name=version,
            repository="TITANICBHAI/FocusFlow",
            release_tag=tag,
            source_sha=SOURCE_SHA,
            apk_arm64_name=f"FocusFlow-tbtechs-arm64-v8a-{tag}.apk",
            apk_arm32_name=f"FocusFlow-tbtechs-armeabi-v7a-{tag}.apk",
            apk_universal_name=f"FocusFlow-tbtechs-universal-{tag}.apk",
        )

    def test_latest_app_changelog_version_matches_gradle(self) -> None:
        self.assertEqual(self.version_name, self.latest["version"])

    def test_notes_are_app_facing_and_include_downloads_install_and_commit(self) -> None:
        notes = self.render()
        tag = f"v{self.version_name}-vc{self.version_code}"
        apk_arm64 = f"FocusFlow-tbtechs-arm64-v8a-{tag}.apk"
        apk_arm32 = f"FocusFlow-tbtechs-armeabi-v7a-{tag}.apk"
        apk_universal = f"FocusFlow-tbtechs-universal-{tag}.apk"

        self.assertIn(f"# 🎯 FocusFlow v{self.version_name}", notes)
        self.assertIn("## ✨ What's New", notes)
        self.assertIn("Improved app-list loading", notes)
        self.assertIn("| 🛡️ | **Protection controls**", notes)
        self.assertIn(f"/releases/download/{tag}/{apk_arm64}", notes)
        self.assertIn(f"/releases/download/{tag}/{apk_arm32}", notes)
        self.assertIn(f"/releases/download/{tag}/{apk_universal}", notes)
        self.assertIn("Download and install **one** APK", notes)
        self.assertIn("Requires Android 10 or later.", notes)
        self.assertIn(f"/commit/{SOURCE_SHA}", notes)
        self.assertNotIn("SHA-256", notes)
        self.assertNotIn("workflow run", notes.lower())
        self.assertNotIn("changelog.json", notes)

    def test_release_fails_when_gradle_and_changelog_versions_differ(self) -> None:
        with self.assertRaisesRegex(ValueError, "does not match"):
            self.render(version_name="0.0.0")

    def test_release_notes_escape_table_pipes_in_changelog_copy(self) -> None:
        changed_entry = json.loads(json.dumps(self.latest))
        changed_entry["sections"][0]["heading"] = "Focus | protection"
        changed_entry["sections"][0]["items"][0] = "First improvement | second improvement"

        notes = self.render(latest=changed_entry)

        self.assertIn("**Focus \\| protection**", notes)
        self.assertIn("First improvement \\| second improvement", notes)

    def test_changelog_rejects_missing_or_empty_sections(self) -> None:
        for entry in (
            [{"version": self.version_name, "date": "Today"}],
            [{"version": self.version_name, "date": "Today", "sections": []}],
        ):
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as directory:
                changelog_path = Path(directory) / "changelog.json"
                changelog_path.write_text(json.dumps(entry), encoding="utf-8")
                with self.assertRaises(ValueError):
                    read_latest_entry(changelog_path)


if __name__ == "__main__":
    unittest.main()
