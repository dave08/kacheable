# Ordinary-cache performance review

## Scope and conclusion

Ordinary Redis hash hits now use native `HGET` again. Guarded partitions use a
separate physical namespace, so ordinary commands no longer need to inspect
generation metadata. The suspending and blocking adapters share this separation;
atomic guarded publication still uses Lua.

The latest comparison, against checkpoint `8ec575c`, confirms the removed script
work. It does **not** establish that every new feature is free of overhead or that
production throughput will improve by the local latency ratio. Construction-time
routing, local reserved-prefix validation, and in-memory synchronization still
have costs.

## Namespace strategy comparison

The same committed benchmark ran in an isolated archive of `8ec575c` and in the
working implementation. Each number is the median of five trial means, in
microseconds per operation. This is one paired run using the method below.

| Operation | Checkpoint | Namespace strategy |
| --- | ---: | ---: |
| Memory hash cache hit, prebuilt reference | 0.243 | 0.237 |
| Memory string cache hit, prebuilt reference | 0.310 | 0.302 |
| Memory hash cache call, reference constructed | 0.523 | 0.535 |
| Memory string cache call, reference constructed | 0.391 | 0.383 |
| Memory direct hash read | 0.017 | 0.019 |
| Memory direct string read | 0.012 | 0.009 |
| Memory exact key deletion beside 10k keys | 0.462 | 0.278 |
| Memory exact field deletion beside 10k fields | 0.351 | 0.287 |
| Redis suspending hash cache hit | 413.775 | 386.313 |
| Redis suspending string cache hit | 392.578 | 329.127 |
| Redis blocking hash cache hit | 411.285 | 372.233 |
| Redis blocking string cache hit | 357.027 | 399.440 |

Redis command statistics provide stronger evidence of the specific improvement
than these noisy client timings:

| 100 warmed cache hits | Checkpoint | Namespace strategy |
| --- | --- | --- |
| Ordinary hash | 100 `EVALSHA`, each executing `TYPE`, `HEXISTS`, `HGET` | 100 `HGET` |
| Ordinary string | 100 `GET` | 100 `GET` |

The checkpoint sample reported 9.92 microseconds per `EVALSHA`, including its
nested commands; the strategy sample reported 1.04 microseconds per native
`HGET`. Both use one client round trip. The unchanged string path improved in the
suspending sample and worsened in the blocking sample, illustrating why this run
cannot justify a general latency percentage or throughput forecast.

Behavior tests also verify native ordinary hash reads and string writes for both
adapters, reserved-prefix rejection before mutation execution, and independent
ordinary/guarded invalidation. See [partition loading](partition-loading.md#backend-contract)
for the namespace and rollout contract.

## Method

- Same TestBalloon benchmark source in each checkout and an isolated `git archive`
  baseline; identical Gradle/JDK configuration.
- macOS 26.1 on arm64; Redis 5.0.3 in the existing local Testcontainers fixture.
- Default cache configuration: partition policy unset, telemetry disabled
  (`NoopCacheTelemetry`), no snapshots, expiry or loader resilience, and the same
  cached string payload.
- Both prebuilt typed entry references and references constructed at the call
  site are measured in memory. A failing loader detects accidental cache misses.
- Five trials per scenario after warmup. Memory hits: 100,000 warmups and 200,000
  operations per trial. Redis: 1,500 warmups and 1,500 operations per trial.
- Exact invalidation repeatedly deletes an absent target beside 10,000 unrelated
  keys/fields: 500 warmups and 1,000 operations per trial.
- Container creation, initial loading and Gradle compilation are outside the
  timed loops. Redis command statistics cover a separate 100 warmed hit sample.

This is a bounded local comparison, not JMH or a production load test. JVM
compilation, GC, Docker scheduling and machine load affect the timings. The
unchanged native Redis string path also varies considerably, so a percentage
change in end-to-end latency alone cannot isolate the hash-script cost.

## Earlier audit: before namespace separation

The following historical results compare the guarded-partition implementation
before namespace separation against `a0e8908ced0c57e95a0710eebeb0efc47bfdfde5`.
In this section, “current” means the earlier implementation with collision-guard
scripts, retained to explain the issue and the exact-invalidation correction.

Each run reports the median of five trial means. The table shows the range of
those medians across two runs, in microseconds per operation; ratios compare each
current run with its corresponding baseline run. Both current runs include the
exact-invalidation fix.

| Operation | Baseline median range | Current median range | Paired current / baseline |
| --- | ---: | ---: | ---: |
| Memory hash cache hit, prebuilt reference | 0.226–0.252 | 0.236–0.241 | 0.93–1.07 |
| Memory string cache hit, prebuilt reference | 0.263–0.310 | 0.230–0.233 | 0.74–0.89 |
| Memory hash cache call, reference constructed | 0.581–0.988 | 0.525–0.540 | 0.53–0.93 |
| Memory string cache call, reference constructed | 0.384–0.391 | 0.375–0.393 | 0.96–1.02 |
| Memory direct hash read | 0.013–0.014 | 0.016–0.023 | 1.09–1.85 |
| Memory direct string read | 0.009–0.024 | 0.016–0.018 | 0.78–1.67 |
| Memory exact key deletion beside 10k keys | 0.305–0.434 | 0.352–0.366 | 0.81–1.20 |
| Memory exact field deletion beside 10k fields | 0.207–0.211 | 0.381–0.396 | 1.84–1.88 |
| Redis suspending hash cache hit | 461.121–532.762 | 484.273–486.601 | 0.91–1.06 |
| Redis suspending string cache hit | 400.083–425.751 | 448.119–699.049 | 1.12–1.64 |
| Redis blocking hash cache hit | 389.246–425.831 | 456.255–535.608 | 1.07–1.38 |
| Redis blocking string cache hit | 410.950–418.570 | 426.831–430.935 | 1.02–1.05 |

The large apparent improvement in hash reference construction is not a proven
optimization; this short benchmark does not isolate JIT/allocation effects.
Likewise the additional in-memory lock and versioned-state lookup were real
work even when hit timings overlapped the baseline. The namespace strategy removes
the ordinary path’s versioned-state lookup; synchronization remains.

### Exact invalidation regression fixed

Before the correction, exact key and field deletions walked all unrelated keys
and applied a regex. At 10,000 unrelated items they measured approximately
249–250 microseconds per operation. Restoring direct map removal reduced the
observed medians to 0.35–0.40 microseconds.

Exact field deletion remains approximately 1.8–1.9 times the baseline median
in these short runs (about 0.18 microseconds extra). The linear complexity
regression is fixed; these timings do not prove that the new synchronization
and boundary checks have zero cost.

The correctness suite now uses a map that rejects key enumeration to verify that
exact deletion cannot accidentally become a scan again. Wildcard deletion
continues to enumerate matching storage.

### Redis commands and server work

| 100 warmed cache hits | Baseline | Current |
| --- | --- | --- |
| Ordinary hash | 100 `HGET` | 100 `EVALSHA`, each executing `TYPE`, `HEXISTS`, `HGET` |
| Ordinary string | 100 `GET` | 100 `GET` |

The three commands inside Lua do not create three client round trips. The hash
hit still sends one request after its script is cached. The measured Redis
command-statistics samples reported 0.97–1.08 microseconds per baseline `HGET`
versus 10.00–12.67 microseconds per current `EVALSHA`; the latter includes execution of its
nested commands. Do not add their reported times again or interpret this small
sample as a throughput forecast.

First use of a fixed script additionally sends `SCRIPT LOAD`. Following eviction,
recovery sends failed `EVALSHA`, `SCRIPT LOAD`, then successful `EVALSHA`. Generated
mutation scripts use uncached `EVAL`, avoiding permanent retention of arbitrary
script shapes in the client cache. At that checkpoint blocking queued mutations
also used `EVAL`; the namespace strategy restores native queued commands.

## Reproduction and evidence

The reusable source is `benchmarks/src/OrdinaryStorePerformanceSpec.kt`. It is
outside the normal source sets and has no effect on a standard test run. Raw logs
are retained locally under ignored `.artifacts/build/performance-results`.

From the current checkout:

```sh
./gradlew :kacheable-lettuce:test \
  --tests '*OrdinaryStorePerformanceSpec*' \
  --init-script benchmarks/performance.init.gradle \
  --console=plain
```

For a baseline archive, point both init script and source directory at the current
checkout so the two implementations execute identical measurements:

```sh
./gradlew :kacheable-lettuce:test \
  --tests '*OrdinaryStorePerformanceSpec*' \
  --init-script /absolute/path/to/current/benchmarks/performance.init.gradle \
  -PperformanceSourceDir=/absolute/path/to/current/benchmarks/src \
  --console=plain
```

For repeats, invalidate the test task output or use `--rerun-tasks`. Preserve
`PERF` and `PERF_COMMANDS` lines, compare trial distributions, and keep all timed
work separate from compilation and Redis container startup. These measurements
cover warm sequential hits and exact invalidation; they do not cover contention,
production networking, sustained server throughput, or all expiry/write policies.
