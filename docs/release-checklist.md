# Release Checklist

Use this before tagging a new public version.

Release tags use the exact Gradle version without a `v` prefix. For example, project version
`0.3.0-alpha03` is tagged `0.3.0-alpha03`, and consumers use that same version. Older `v`-prefixed
tags remain valid historical releases. The `/v/` in JitPack's badge URL is only the version-badge
endpoint and is unrelated to tag naming.

1. Confirm the version in `build.gradle.kts` is new and does not already have a Git tag or JitPack
   build.
2. Run the library verification:

   ```bash
   ./gradlew :kacheable-core:test :kacheable-lettuce:test publishToMavenLocal
   ```

3. Verify the documented single-dependency installation against the published artifacts:

   ```bash
   ./gradlew -p smoke-tests/publication -PkacheableVersion=0.3.0-alpha03 run
   ```

   This standalone consumer compiles the public suspend/blocking APIs and runs ordinary-cache
   and enumerable-partition checks. It depends only on the Lettuce artifact, so missing API
   dependency exports fail here even when tests within the library pass.

4. Compile at least one real consumer against the local artifact version.
5. Read the README quick-start examples against the current API.
6. Check `CHANGELOG.md` for the release notes.
7. Push the release branch to GitHub.
8. Tag the exact version from `build.gradle.kts`, without adding a prefix:

   ```bash
   git tag 0.3.0-alpha03
   git push origin 0.3.0-alpha03
   ```

9. Confirm JitPack builds the tag and resolves both modules at the exact tag version:

   ```kotlin
   implementation("com.github.dave08.kacheable:kacheable-core:0.3.0-alpha03")
   implementation("com.github.dave08.kacheable:kacheable-lettuce:0.3.0-alpha03")
   ```

10. Do not move or reuse a published tag. Create a new patch or prerelease version for corrections.
