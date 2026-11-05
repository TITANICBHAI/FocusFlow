"""Tests for scripts/release_notes.py.

Run with:  python3 scripts/test_release_notes.py
(set RELEASE_SOURCE_DIR to check a different checkout, as the publish workflow does)

Nothing here may depend on the wording or version of a real changelog entry, so the
suite keeps passing release after release. The two RealFiles tests read the repo's
actual files but only assert things that must always hold (the newest changelog entry
is valid, matches Gradle, and is rendered completely).
"""

import contextlib
import io
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import release_notes as rn
from release_notes import Apk

# The publish workflow points RELEASE_SOURCE_DIR at the commit being released; locally the
# default is the repository this script lives in.
ROOT = Path(os.environ.get("RELEASE_SOURCE_DIR") or Path(__file__).resolve().parents[1]).resolve()
CHANGELOG_PATH = ROOT / "app/src/main/assets/changelog.json"
GRADLE_PATH = ROOT / "app/build.gradle.kts"

REPO = "TITANICBHAI/FocusFlow"
SHA = "1234567890abcdef1234567890abcdef12345678"
BASE = f"https://github.com/{REPO}/releases/download/v9.8.7-vc99"

SAMPLE_GRADLE = """
android {
    defaultConfig {
        minSdk = 29
        targetSdk = 35
        versionCode = 99
        versionName = "9.8.7"
    }
}
"""


def sample_entry():
    return {
        "version": "9.8.7",
        "date": "March 2031",
        "sections": [
            {"heading": "Focus sessions", "items": ["First change.", "Second change."]},
            {"heading": "Photo slots", "items": ["Third change."]},
        ],
    }


def sample_apks():
    return [
        Apk("ARM64", "most newer Android phones", "FocusFlow-tbtechs-arm64-v8a-v9.8.7-vc99.apk", 6_437_248),
        Apk("ARM32", "older 32-bit devices", "FocusFlow-tbtechs-armeabi-v7a-v9.8.7-vc99.apk", 6_434_406),
        Apk("Universal", "not sure which to choose?", "FocusFlow-tbtechs-universal-v9.8.7-vc99.apk", 6_487_321),
    ]


def render(**overrides):
    options = dict(
        entry=sample_entry(),
        version_name="9.8.7",
        min_sdk=29,
        repository=REPO,
        release_tag="v9.8.7-vc99",
        source_sha=SHA,
        apks=sample_apks(),
        previous_tag="v9.8.6-vc98",
    )
    options.update(overrides)
    entry = options.pop("entry")
    return rn.render_release_notes(entry, **options)


def git(repo, *args):
    result = subprocess.run(
        [
            "git", "-C", str(repo),
            "-c", "user.name=Test", "-c", "user.email=test@example.com",
            "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false",
            *args,
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def commit(repo, message="x"):
    git(repo, "commit", "--allow-empty", "-q", "-m", message)
    return git(repo, "rev-parse", "HEAD")


def make_repo(directory):
    repo = Path(directory) / "repo"
    repo.mkdir()
    subprocess.run(["git", "init", "-q", str(repo)], check=True, capture_output=True)
    return repo


class RealFilesTest(unittest.TestCase):
    """The release gate: these fail when the repo is not ready to be published."""

    def test_newest_changelog_entry_is_valid_and_matches_gradle(self):
        latest = rn.read_latest_entry(CHANGELOG_PATH)
        gradle = rn.read_gradle_metadata(GRADLE_PATH)
        self.assertEqual(
            gradle.version_name,
            latest["version"],
            "Add a changelog.json entry for the Gradle versionName before releasing.",
        )

    def test_real_changelog_is_rendered_completely(self):
        latest = rn.read_latest_entry(CHANGELOG_PATH)
        gradle = rn.read_gradle_metadata(GRADLE_PATH)
        notes = rn.render_release_notes(
            latest,
            version_name=gradle.version_name,
            min_sdk=gradle.min_sdk,
            repository=REPO,
            release_tag=rn.expected_release_tag(gradle),
            source_sha=SHA,
            apks=sample_apks(),
        )
        self.assertIn(f"# 🎯 FocusFlow v{gradle.version_name}", notes)
        for section in latest["sections"]:
            self.assertIn(rn.clean(section["heading"]), notes)
            for item in section["items"]:
                self.assertIn(rn.clean(item), notes)


class RenderTest(unittest.TestCase):
    def test_notes_cover_everything_a_release_page_needs(self):
        notes = render()
        for expected in (
            "# 🎯 FocusFlow v9.8.7",
            rn.TAGLINE,
            "_March 2031_",
            "🎯 Focus sessions · 🖼️ Photo slots",
            "## ✨ What’s New",
            "### 🎯 Focus sessions",
            "- First change.",
            "- Second change.",
            "### 🖼️ Photo slots",
            "- Third change.",
            "## 📥 Downloads",
            "**ARM64** · most newer Android phones | 6.1 MB",
            "**ARM32** · older 32-bit devices | 6.1 MB",
            "**Universal** · not sure which to choose? | 6.2 MB",
            f"{BASE}/FocusFlow-tbtechs-arm64-v8a-v9.8.7-vc99.apk",
            f"{BASE}/FocusFlow-tbtechs-armeabi-v7a-v9.8.7-vc99.apk",
            f"{BASE}/FocusFlow-tbtechs-universal-v9.8.7-vc99.apk",
            "Download and install **one** APK",
            "## 📲 Install",
            "### 1. Allow APK installation",
            "### 2. Install with Android or SAI",
            "### 3. If Play Protect warns",
            "### 4. Allow restricted settings (Android 13+)",
            "Requires Android 10 or later.",
            f"/compare/v9.8.6-vc98...v9.8.7-vc99",
            f"/commit/{SHA}",
            f"{rn.SAI_URL}",
            f"https://github.com/{REPO}/issues/new/choose",
            f"https://github.com/{REPO}/discussions",
        ):
            self.assertIn(expected, notes)

    def test_notes_stay_app_facing(self):
        notes = render()
        for jargon in ("SHA-256", "workflow run", "changelog.json"):
            self.assertNotIn(jargon, notes)

    def test_no_previous_release_means_no_compare_link(self):
        notes = render(previous_tag=None)
        self.assertNotIn("/compare/", notes)
        self.assertNotIn("Full changelog", notes)
        self.assertIn(f"/commit/{SHA}", notes)

    def test_single_section_has_no_summary_line(self):
        entry = sample_entry()
        entry["sections"] = entry["sections"][:1]
        self.assertNotIn("🎯 Focus sessions ·", render(entry=entry))

    def test_changelog_copy_is_escaped_and_flattened(self):
        entry = sample_entry()
        entry["sections"][0]["heading"] = "Q&A <b>"
        entry["sections"][0]["items"][0] = "  spread\nover   lines | with a pipe "
        notes = render(entry=entry)
        self.assertIn("Q&amp;A &lt;b&gt;", notes)
        self.assertIn("- spread over lines | with a pipe", notes)
        self.assertNotIn("<b>", notes)

    def test_minimum_android_version_comes_from_gradle(self):
        self.assertIn("Requires Android 12 or later.", render(min_sdk=31))
        self.assertEqual(rn.android_label(29), "Android 10")
        self.assertEqual(rn.android_label(99), "Android (API 99)")

    def test_size_labels(self):
        self.assertEqual(rn.format_size(6_437_248), "6.1 MB")
        self.assertEqual(rn.format_size(2048), "2 KB")
        self.assertEqual(rn.format_size(1), "1 KB")

    def test_release_is_refused_when_changelog_was_not_updated(self):
        with self.assertRaisesRegex(ValueError, "does not match"):
            render(version_name="0.0.1")

    def test_section_emoji(self):
        self.assertEqual(rn.section_emoji("Protect System Controls"), "🛡️")
        self.assertEqual(rn.section_emoji("Subtasks and rich imports"), "🎯")
        self.assertEqual(rn.section_emoji("Photo slots"), "🖼️")
        self.assertEqual(rn.section_emoji("Something new"), rn.DEFAULT_EMOJI)


class ChangelogAndGradleTest(unittest.TestCase):
    def read_changelog(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "changelog.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            return rn.read_latest_entry(path)

    def test_valid_entry_is_returned(self):
        self.assertEqual(self.read_changelog([sample_entry(), {"version": "0.0.1"}]), sample_entry())

    def test_malformed_changelogs_are_rejected(self):
        good = sample_entry()
        bad_inputs = {
            "not a list": {"version": "1"},
            "empty list": [],
            "entry not an object": ["text"],
            "no sections": [{"version": "1", "date": "Today"}],
            "empty sections": [{**good, "sections": []}],
            "section without items": [{**good, "sections": [{"heading": "A", "items": []}]}],
            "blank item": [{**good, "sections": [{"heading": "A", "items": ["  "]}]}],
            "non-text item": [{**good, "sections": [{"heading": "A", "items": [3]}]}],
            "section without heading": [{**good, "sections": [{"items": ["x"]}]}],
            "blank date": [{**good, "date": " "}],
        }
        for name, data in bad_inputs.items():
            with self.subTest(name), self.assertRaises(ValueError):
                self.read_changelog(data)

    def test_gradle_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "build.gradle.kts"
            path.write_text(SAMPLE_GRADLE, encoding="utf-8")
            gradle = rn.read_gradle_metadata(path)
            self.assertEqual((gradle.version_name, gradle.version_code, gradle.min_sdk), ("9.8.7", 99, 29))
            self.assertEqual(rn.expected_release_tag(gradle), "v9.8.7-vc99")

            path.write_text(SAMPLE_GRADLE.replace("minSdk = 29", ""), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "minSdk"):
                rn.read_gradle_metadata(path)


@unittest.skipUnless(shutil.which("git"), "git is not installed")
class PreviousReleaseTagTest(unittest.TestCase):
    def test_picks_newest_older_release_tag_and_ignores_the_rest(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(directory)
            first = commit(repo, "first")
            main_branch = git(repo, "rev-parse", "--abbrev-ref", "HEAD")
            for tag in ("v1.0.9", "v1.1.3", "indus-v1.1.2", "nodespy-24646641226", "v1.1.x"):
                git(repo, "tag", tag)
            commit(repo, "second")
            git(repo, "tag", "v1.1.4-vc15")

            git(repo, "checkout", "-q", "--detach", first)
            commit(repo, "side branch")
            git(repo, "tag", "v1.1.9-vc99")
            git(repo, "checkout", "-q", main_branch)

            head = commit(repo, "release")
            self.assertEqual(rn.previous_release_tag(repo, head, "v1.1.5-vc16"), "v1.1.4-vc15")
            self.assertEqual(rn.previous_release_tag(repo, head, "v1.1.4-vc15"), "v1.1.3")

    def test_none_when_there_is_no_earlier_release(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = make_repo(directory)
            head = commit(repo)
            self.assertIsNone(rn.previous_release_tag(repo, head, "v1.0.0-vc1"))
            git(repo, "tag", "v1.0.0-vc1")
            self.assertIsNone(rn.previous_release_tag(repo, head, "v1.0.0-vc1"))

    def test_none_outside_a_git_repo_or_for_an_unknown_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertIsNone(rn.previous_release_tag(Path(directory), SHA, "v1.0.0"))
            repo = make_repo(directory)
            commit(repo)
            self.assertIsNone(rn.previous_release_tag(repo, SHA, "v1.0.0"))


@unittest.skipUnless(shutil.which("git"), "git is not installed")
class CommandLineTest(unittest.TestCase):
    def setUp(self):
        self._directory = tempfile.TemporaryDirectory()
        self.addCleanup(self._directory.cleanup)
        self.dir = Path(self._directory.name)

        self.changelog = self.dir / "changelog.json"
        self.changelog.write_text(json.dumps([sample_entry()]), encoding="utf-8")
        self.gradle = self.dir / "build.gradle.kts"
        self.gradle.write_text(SAMPLE_GRADLE, encoding="utf-8")
        self.apks = {}
        for apk in sample_apks():
            path = self.dir / apk.name
            path.write_bytes(b"x" * 3_000_000)
            self.apks[apk.label] = path

        self.repo = make_repo(self.dir)
        commit(self.repo, "older release")
        git(self.repo, "tag", "v9.8.6-vc98")
        self.head = commit(self.repo, "this release")
        self.output = self.dir / "release-notes.md"

    def run_cli(self, **overrides):
        values = {
            "--changelog": self.changelog,
            "--gradle": self.gradle,
            "--repository": REPO,
            "--release-tag": "v9.8.7-vc99",
            "--source-sha": self.head,
            "--apk-arm64": self.apks["ARM64"],
            "--apk-arm32": self.apks["ARM32"],
            "--apk-universal": self.apks["Universal"],
            "--repo-dir": self.repo,
            "--output": self.output,
        }
        values.update(overrides)
        argv = [item for pair in values.items() for item in (pair[0], str(pair[1]))]
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = rn.main(argv)
        return code, stdout.getvalue(), stderr.getvalue()

    def test_writes_complete_notes(self):
        code, _, errors = self.run_cli()
        self.assertEqual(code, 0, errors)
        notes = self.output.read_text(encoding="utf-8")
        self.assertIn("# 🎯 FocusFlow v9.8.7", notes)
        self.assertIn(f"{BASE}/FocusFlow-tbtechs-universal-v9.8.7-vc99.apk", notes)
        self.assertIn("2.9 MB", notes)
        self.assertIn("/compare/v9.8.6-vc98...v9.8.7-vc99", notes)

    def test_wrong_tag_is_refused_and_nothing_is_written(self):
        code, _, errors = self.run_cli(**{"--release-tag": "v9.8.7-vc98"})
        self.assertEqual(code, 1)
        self.assertIn("does not match the Gradle version", errors)
        self.assertFalse(self.output.exists())

    def test_missing_or_empty_apk_is_refused(self):
        for value in (self.dir / "missing.apk", ""):
            with self.subTest(value=str(value)):
                code, _, errors = self.run_cli(**{"--apk-arm32": value})
                self.assertEqual(code, 1)
                self.assertIn("ARM32 APK is missing or empty", errors)
                self.assertFalse(self.output.exists())

    def test_stale_changelog_is_refused(self):
        self.gradle.write_text(SAMPLE_GRADLE.replace("9.8.7", "9.9.0"), encoding="utf-8")
        code, _, errors = self.run_cli(**{"--release-tag": "v9.9.0-vc99"})
        self.assertEqual(code, 1)
        self.assertIn("does not match the newest in-app changelog", errors)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
