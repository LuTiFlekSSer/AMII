# AMII Agent Guidance

## Project

- AMII is a JetBrains Platform plugin written in Kotlin and Java.
- Treat `gradle.properties` as the source of truth for the plugin version and IDE compatibility range.
- `origin` is the maintained fork. Treat `upstream` as read-only.
- Do not create pull requests unless explicitly requested.

## Build and test

- Never download or install JetBrains IDE distributions. Build against an IDE that is already installed by setting `AMII_IDE_PATH`.
- Keep agent-created Gradle state inside the repository so the global Gradle cache is not polluted.

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle\agent-home"
$env:AMII_IDE_PATH = "$env:LOCALAPPDATA\Programs\PyCharm Professional"
.\gradlew.bat clean test buildPlugin verifyPlugin
```

- The installable archive is written to `build/distributions/`.
- Prefer the newest installed PyCharm for the primary smoke test. Use an existing CLion 2026.2 installation as a secondary target when available.
- Prefer `.\gradlew.bat runIde` for an isolated IDE sandbox.
- Never use the user's live IDE config or system directories. Never kill an IDE process unless it was launched by the current test and its PID was recorded.

## Critical regressions

- Plugin startup must never block on network access, filesystem work, media decoding, cache warming, or waiting for background jobs.
- Create and mutate Swing components only on the event-dispatch thread.
- Give network operations explicit connection and read timeouts.
- Write downloaded cache content through atomic temporary files.
- Orphan cleanup must not remove assets that are currently being downloaded.
- Keep `intellij.platform.tasks` and `intellij.platform.smRunner` as `<module>` dependencies. Do not declare them as installable `<depends>` plugins.

## Completion criteria

- Run the tests, `buildPlugin`, and `verifyPlugin`.
- Perform a cold-start smoke test and confirm the IDE reaches the project without freezing before the welcome meme.
- Report every file or directory created outside the repository.
- Do not delete global caches, `%TEMP%`, or unrelated user data.

## Releases

- Use tags in the form `vX.Y.Z`.
- Attach `build/distributions/AMII-X.Y.Z.zip` to the matching GitHub Release.
- Publish a release only after its tag points to the tested commit.
