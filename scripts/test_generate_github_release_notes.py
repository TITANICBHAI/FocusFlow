import json
import unittest
from pathlib import Path

from generate_github_release_notes import read_latest_entry, render_release_notes


ROOT = Path(__file__).resolve().parents[1]
CHANGELOG_PATH = ROOT / "app/src/main/assets/changelog.json"
CHECKSUM = "a" * 64


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.latest = read_latest_entry(CHANGELOG_PATH)

    def render(self, *, version_name: str = "1.1.4") -> str:
        return render_release_notes(
            self.latest,
            version_name=version_name,
            version_code="15",
            build_run_number="42",
            build_run_url="https://github.com/TITANICBHAI/FocusFlow/actions/runs/42",
            source_sha="1234567890abcdef",
            apk_arm64_name="FocusFlow-tbtechs-arm64.apk",
            apk_arm64_sha256=CHECKSUM,
            apk_arm32_name="FocusFlow-tbtechs-arm32.apk",
            apk_arm32_sha256=CHECKSUM,
            apk_universal_name="FocusFlow-tbtechs-universal.apk",
            apk_universal_sha256=CHECKSUM,
        )

    def test_app_changelog_is_valid_and_latest_entry_matches_gradle(self) -> None:
        with CHANGELOG_PATH.open(encoding="utf-8") as source:
            changelog = json.load(source)
        self.assertEqual("1.1.4", changelog[0]["version"])
        self.assertEqual("1.1.4", self.latest["version"])

    def test_release_notes_include_app_context_latest_changes_and_install_guidance(self) -> None:
        notes = self.render()
        self.assertIn("Android productivity app from TBTechs", notes)
        self.assertIn("Improved app-list loading", notes)
        self.assertIn("FocusFlow-tbtechs-universal.apk", notes)
        self.assertIn("Keep Play Protect enabled", notes)
        self.assertIn("Install unknown apps", notes)
        self.assertNotIn("SAI", notes)

    def test_release_fails_when_gradle_and_changelog_versions_differ(self) -> None:
        with self.assertRaisesRegex(ValueError, "does not match"):
            self.render(version_name="1.1.3")

    def test_release_fails_for_invalid_checksum(self) -> None:
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            render_release_notes(
                self.latest,
                version_name="1.1.4",
                version_code="15",
                build_run_number="42",
                build_run_url="https://example.com/run",
                source_sha="1234567890abcdef",
                apk_arm64_name="arm64.apk",
                apk_arm64_sha256="bad",
                apk_arm32_name="arm32.apk",
                apk_arm32_sha256=CHECKSUM,
                apk_universal_name="universal.apk",
                apk_universal_sha256=CHECKSUM,
            )


if __name__ == "__main__":
    unittest.main()
