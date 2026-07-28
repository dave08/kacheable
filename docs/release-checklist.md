# Release Checklist

Use this before tagging a new public version.

Release tags use the exact Gradle version without a `v` prefix. For example, project version
`0.3.0-alpha02` is tagged `0.3.0-alpha02`, and consumers use that same version. Older `v`-prefixed
tags remain valid historical releases. The `/v/` in JitPack's badge URL is only the version-badge
endpoint and is unrelated to tag naming.

1. Confirm the version in `build.gradle.kts` is new and does not already have a Git tag or JitPack
   build.
2. Run the library verification:

   ```bash
   ./gradlew :kacheable-core:test :kacheable-lettuce:test publishToMavenLocal
   ```

3. Compile at least one real consumer against the local artifact version.
4. Read the README quick-start examples against the current API.
5. Check `CHANGELOG.md` for the release notes.
6. Push the release branch to GitHub.
7. Tag the exact version from `build.gradle.kts`, without adding a prefix:

   ```bash
   git tag 0.3.0-alpha02
   git push origin 0.3.0-alpha02
   ```

8. Confirm JitPack builds the tag and resolves both modules at the exact tag version:

   ```kotlin
   implementation("com.github.dave08.kacheable:kacheable-core:0.3.0-alpha02")
   implementation("com.github.dave08.kacheable:kacheable-lettuce:0.3.0-alpha02")
   ```

9. Do not move or reuse a published tag. Create a new patch or prerelease version for corrections.
