# Release Checklist

Use this before tagging a new public version.

1. Confirm the version in `build.gradle.kts`.
2. Run the library verification:

   ```bash
   ./gradlew :kacheable-core:test :kacheable-lettuce:test publishToMavenLocal
   ```

3. Compile at least one real consumer against the local artifact version.
4. Read the README quick-start examples against the current API.
5. Check `CHANGELOG.md` for the release notes.
6. Push the release branch to GitHub.
7. Tag the release, for example:

   ```bash
   git tag v0.2.0-alpha03
   git push origin v0.2.0-alpha03
   ```

8. Confirm JitPack resolves the new tag.
