---
name: Android build and test environment
description: Environment constraints and local verification guidance for Android builds and tests.
---

The workspace may lack a usable Java command or `JAVA_HOME`; the Replit `java-graalvm22.3` module exposes GraalVM Java 19 on `PATH` without setting `JAVA_HOME`, not the project's required JDK 17. The checked-in Gradle wrapper downloads Gradle automatically. This workspace also lacks an Android SDK platform installation or `sdk.dir` configuration, so a Gradle build or test can stop before Kotlin source compilation even after Java is provisioned.

For local unit-test runs, use the repository's bootstrap-backed test path so it can provision JDK 17 and the Android SDK outside the Git repository; it runs JVM unit tests without assembling an APK.

**Why:** Earlier build attempts stopped first for missing Java and then for the Android SDK. The user asked to keep the dedicated local test route in project memory so future Android verification doesn't mistake missing tools for a source failure.

**How to apply:** Check `java`, `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `local.properties` before compiler diagnosis. When running local unit tests, use the bootstrap-backed test workflow; use JDK 17 and keep SDK files outside the repository. If prerequisites cannot be obtained, record the test as blocked rather than failed.

Kotlin Android unit-test compilation in this workspace did not resolve `java.nio.file.Files.readString`, even with JDK 17 provisioned; reading project source from a JVM test worked with `File.readText(Charsets.UTF_8)`.

**Why:** The first source-aware characterization test failed compilation against the Android unit-test classpath despite a working JDK and SDK.

**How to apply:** Prefer `File.readText(Charsets.UTF_8)` over `Files.readString` when a JVM test needs to read checked-in project source files.

On a fresh workspace, the first bootstrap-backed run can exceed a short shell timeout while it downloads and installs JDK, Android SDK packages, Gradle, and compiles Kotlin. A timed-out shell invocation is not evidence of a test failure; after setup, a background run can complete successfully with cached prerequisites.

**Why:** The first two test invocations timed out during environment setup and initial Kotlin compilation; a later run with the same code completed successfully.

**How to apply:** For a first-time or partially cached build, run `bash scripts/test-unit.sh` in the background and inspect its final Gradle result rather than retrying immediately or treating the timeout as a code failure.

The bootstrap-installed Android SDK can warn that its package metadata uses SDK XML v4 while the command-line tools only understand through v3. In the observed run, SDK installation completed and both lint and unit-test Gradle tasks passed.

**Why:** The warning appeared during successful API 35 SDK setup and could otherwise be mistaken for a blocking SDK failure.

**How to apply:** Check the Gradle exit code and task results before treating this SDK XML warning as a failure.