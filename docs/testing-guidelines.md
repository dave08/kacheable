# Testing Guidelines

These rules apply to new and changed Kacheable tests. Existing tests should be
improved incrementally when their behavior is already changing or when their
current shape obstructs the work. Test cleanup should not become a prerequisite
for an unrelated fix or release.

## Test Boundaries

- Keep one behavioral boundary per spec. Separate public cache semantics,
  storage behavior, serialization, resilience, snapshots, telemetry, and Redis
  integration instead of combining them in a catch-all suite.
- Give each leaf test one reason to fail.
- Give every data-driven case an independently named leaf test. Use
  parameterization only when every case has the same arrange/act/assert shape.
- Make leaf names understandable without their enclosing suite name because not
  every test reporter preserves TestBalloon's nested suite names.
- Test public behavior where possible. Test an internal seam directly only when
  that seam owns a meaningful contract that cannot be observed clearly through
  the public API.

## TestBalloon

- Use `testFixture { ... } asContextForEach { ... }` when tests need mutable
  scenario state. Each leaf must receive fresh state.
- Prefer small, behavior-specific fixtures over a universal cache fixture.
- Keep the operation under test visible in the leaf. Fixtures should arrange
  state, own resources, and provide domain assertions; they should not hide the
  central `cache(...)`, `invalidate(...)`, snapshot, or store call behind a
  generic `execute()` helper.
- Start fixture facades and assertion helpers as `private` declarations in the
  spec file.
- If mechanical store or codec fakes dominate a spec, move them to a colocated,
  subject-specific support file. Do not create a generic test utility dumping
  ground.
- Use a focused custom registrar for repeated infrastructure lifecycle. Mark it
  with TestBalloon's `@TestRegistering` and `@TestElementName`, preserve an
  explicit leaf name, and guarantee cleanup.

## Fixtures And Collaborators

- Use cache-domain language in fixture APIs and keep important inputs visible,
  such as cache names, expiry, resilience configuration, storage shape, and
  expected operation outcomes.
- Preserve traceability from setup to assertion. Bind meaningful arranged
  values to names rather than repeating unexplained keys or payloads on both
  sides of the test.
- Prefer real cache value objects and the real internal collaborator backed by a
  narrow fake at its external boundary.
- Use `InMemoryKacheableStore` for core behavior when its semantics are
  sufficient. Use a small recording, blocking, or failing `KacheableStore` fake
  when the boundary interaction is the behavior under test.
- Use mocks only when a fake would distort the contract. Avoid relaxed mocks:
  unexpected calls should fail.
- Dependencies intentionally outside the tested behavior may use small
  fail-fast fakes whose error identifies the crossed boundary.

## Core And Adapter Coverage

- Put backend-independent cache semantics in `kacheable-core`.
- Put Lettuce commands, Redis atomicity, distributed single-flight, connection
  behavior, and Redis-specific expiry or mutation semantics in
  `kacheable-lettuce`.
- Do not prove a Redis contract with only `InMemoryKacheableStore`. Use the
  repository's Redis test infrastructure when the behavior depends on Redis.
- Keep suspending and blocking behavior semantically aligned. When shared code
  does not guarantee parity, cover both APIs with the same named behavior or a
  focused contract registrar.
- Cover storage shapes independently when exact/string, indexed/hash, and set
  storage take different runtime paths.

## Assertions

- Prefer domain-scoped assertions such as
  `assertOperationCounts(...)`, `assertSingleFlight(...)`, or
  `assertSnapshotRestored(...)` when they make the behavioral contract clearer.
- Keep assertion helpers private until repeated use proves that they express a
  shared project contract.
- Extend the fixture when an assertion depends on arranged scenario state.
  Extend the returned value or telemetry snapshot when it depends only on that
  result.
- Keep important expected values visible as assertion parameters. Avoid opaque
  helpers such as `assertCorrectResult()`.
- Preserve useful failure diagnostics in every helper.
- Use `kotlin.test` directly for simple assertions. If another assertion library
  is helpful, keep its DSL behind focused helpers rather than spreading it
  throughout the suite.

## Coroutines, Time, And Concurrency

- Prefer `CompletableDeferred`, controlled dispatchers, or another deterministic
  barrier for loader, background refresh, limiter, and single-flight tests.
- Use `CoroutineStart.UNDISPATCHED` when a coroutine must deterministically
  reach a known suspension point before the test continues.
- Inject or control time for expiry, timeout, snapshot, and telemetry behavior
  where practical.
- Avoid arbitrary short `delay`, `Thread.sleep`, polling, and wall-clock
  tolerances. A real-time wait is acceptable only when elapsed time is the
  integration contract and no controllable clock or barrier can represent it;
  keep such coverage narrow and allow enough margin for CI.
- Own coroutine scopes, containers, Redis connections, and other resources in a
  fixture or custom registrar that guarantees cleanup.
- Never leave background jobs running after a leaf test completes.

## Regression And Migration Tests

- Reproduce a regression through the narrowest public behavior that failed.
- For payload or schema migration, distinguish the returned value, stored
  representation, migration decision, and write-back behavior. Do not make one
  assertion stand in for all four contracts.
- For telemetry, assert semantic stages and outcomes rather than exact
  nanosecond durations.
- Do not assert private implementation ordering unless that ordering is itself
  required, such as loader completion occurring before cache persistence.

## Adoption

- Apply these rules to new tests immediately.
- Improve nearby tests when changing the same behavior.
- Migrate older suites in small, independently validated slices.
- Run the narrow affected suite while iterating, then run
  `./gradlew :kacheable-core:test :kacheable-lettuce:test` before handing off a
  cross-module change.
