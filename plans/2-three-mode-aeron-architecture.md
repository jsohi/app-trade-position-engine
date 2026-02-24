# Plan: Three-Mode Aeron Architecture

## Context

Currently `TradePositionEngineApp` runs everything in a single JVM: an embedded MediaDriver,
a SendAgent thread, and a ReceiveAgent thread. The goal is to split this into three independently
runnable modes so the MediaDriver, publisher, and consumer can each run as a separate OS process,
communicating over Aeron IPC (or UDP). Integration tests must also be updated to spawn and
coordinate three real JVM subprocesses.

---

## Module Changes

### New module: `media-driver`
Responsible solely for launching a standalone Aeron MediaDriver.

```
media-driver/
  build.gradle
  src/main/java/com/bofa/equity/MediaDriverApp.java
```

`settings.gradle` — add:
```groovy
include ':media-driver'
```

### Updated: `publisher/build.gradle`
Add `application` plugin + `mainClass` so `./gradlew :publisher:run` works.

### Updated: `receiver/build.gradle`
Add `application` plugin + `mainClass` so `./gradlew :receiver:run` works.

---

## New Main Classes

### 1. `media-driver/src/main/java/com/bofa/equity/MediaDriverApp.java`

- Launch `MediaDriver` with `ThreadingMode.DEDICATED` (3 internal threads: receiver,
  sender, conductor — conventional for a standalone driver process).
- Print the aeron directory path to stdout so other processes and tests can discover it:
  `System.out.println("AERON_DIR=" + driver.aeronDirectoryName());`
- Also write it to a well-known file (`/tmp/aeron-trade-engine.dir`) for scripted use.
- Block on `ShutdownSignalBarrier.await()` until SIGTERM/SIGINT.
- System property: `-Daeron.dir=<path>` overrides the default directory.

### 2. `publisher/src/main/java/com/bofa/equity/PublisherApp.java`

- Read config from system properties (no CLI parsing library needed):
  - `aeron.dir`      — required; aeron directory of the running MediaDriver
  - `send.count`     — default 1,000,000
  - `aeron.channel`  — default `"aeron:ipc"`
  - `aeron.stream.id` — default 10
  - `audit.stream.id` — default 11
- Connect `Aeron` to the external MediaDriver (no `MediaDriver.launch` here).
- Create `Publication` on stream 10; optionally stream 11 for audit.
- Create `AffinityThreadFactory` + `AgentRunner` + `SendAgent` exactly as in
  `TradePositionEngineApp` — no changes to `SendAgent` itself.
- Block until `ShutdownSignalBarrier` signals (ReceiveAgent in the driver signals when done —
  here the publisher signals itself after `SendAgent` finishes all `sendCount` messages).
- Close resources and exit normally (exit code 0).

### 3. `receiver/src/main/java/com/bofa/equity/ReceiverApp.java`

- Read config from system properties:
  - `aeron.dir`        — required
  - `send.count`       — must match publisher so ReceiveAgent knows when to stop
  - `aeron.channel`    — default `"aeron:ipc"`
  - `aeron.stream.id`  — default 10
- Connect `Aeron` to the external MediaDriver.
- Create `Subscription` on stream 10.
- Build `Cache`, `PositionAggregator`, `TradeHandler`, `ShutdownSignalBarrier`.
- Create `AffinityThreadFactory` + `AgentRunner` + `ReceiveAgent` — no changes to existing classes.
- Block on `barrier.await()`.
- Call `positionAggregator.stats()` (prints HDR histogram to stdout).
- Close resources and exit (exit code 0).

---

## Aeron Directory Handoff

MediaDriver prints `AERON_DIR=<path>` to stdout on startup. Publisher and Consumer receive it via:
- **Manual**: `-Daeron.dir=<path>` system property on the command line.
- **Integration tests**: test reads MediaDriver stdout, extracts the path, passes it to
  Publisher and Consumer subprocesses via `-Daeron.dir`.
- **Scripts**: source the `/tmp/aeron-trade-engine.dir` file.

---

## Gradle Run Tasks

`media-driver/build.gradle`:
```groovy
plugins { id 'java'; id 'application' }
application {
    mainClass = 'com.bofa.equity.MediaDriverApp'
    applicationDefaultJvmArgs = [ '--add-opens java.base/sun.nio.ch=ALL-UNNAMED', ... ]
}
dependencies { implementation project(':shared') }
```

`publisher/build.gradle` — add:
```groovy
apply plugin: 'application'
application {
    mainClass = 'com.bofa.equity.PublisherApp'
    applicationDefaultJvmArgs = [ ... ]  // same as root
}
```

`receiver/build.gradle` — add:
```groovy
apply plugin: 'application'
application {
    mainClass = 'com.bofa.equity.ReceiverApp'
    applicationDefaultJvmArgs = [ ... ]
}
```

Usage:
```bash
./gradlew :media-driver:run                                    # Mode 1
./gradlew :publisher:run  -Daeron.dir=/tmp/aeron-1234         # Mode 2
./gradlew :receiver:run   -Daeron.dir=/tmp/aeron-1234 -Dsend.count=1000000  # Mode 3
```

---

## Integration Test: Multi-Process (`MultiProcessPipelineIT.java`)

Location: `src/test/java/com/bofa/equity/MultiProcessPipelineIT.java`

Uses `ProcessBuilder` with the **test's own classpath** (`System.getProperty("java.class.path")`).
The root module's test classpath already includes all submodule classes and all runtime deps.

### Test flow:

```
1. Launch MediaDriverApp subprocess
   - Wait until stdout contains "AERON_DIR=" line → extract path
   - Timeout: 10 seconds

2. Launch ReceiverApp subprocess
   - Pass -Daeron.dir=<extracted path> -Dsend.count=<N>
   - Capture stdout/stderr via background reader thread

3. Launch PublisherApp subprocess
   - Pass -Daeron.dir=<extracted path> -Dsend.count=<N>
   - Wait for process to exit (publisher exits after sending all trades)

4. Wait for ReceiverApp subprocess to exit
   - Timeout: 30 seconds

5. Assert ReceiverApp exit code == 0
6. Assert ReceiverApp stdout contains "END-TO-END LATENCY" (stats printed)

7. Destroy MediaDriver subprocess (SIGTERM)
```

Helper: `SubProcess` inner class wraps `ProcessBuilder`, starts a background thread to drain
stdout/stderr into a `StringBuilder` (prevents subprocess blocking on full pipe buffer).

### Existing tests unchanged
`TradePositionEnginePipelineIT`, `PositionAggregatorPipelineIT`, and `CucumberRunner` all remain
as-is — they run in-process with embedded MediaDriver.

---

## Files to Create / Modify

| File | Action |
|------|--------|
| `settings.gradle` | Add `include ':media-driver'` |
| `media-driver/build.gradle` | **Create** — application plugin, depends on `:shared` |
| `media-driver/src/main/java/com/bofa/equity/MediaDriverApp.java` | **Create** |
| `publisher/src/main/java/com/bofa/equity/PublisherApp.java` | **Create** |
| `publisher/build.gradle` | Add application plugin + mainClass |
| `receiver/src/main/java/com/bofa/equity/ReceiverApp.java` | **Create** |
| `receiver/build.gradle` | Add application plugin + mainClass |
| `src/test/java/com/bofa/equity/MultiProcessPipelineIT.java` | **Create** |

**No changes to**: `SendAgent`, `ReceiveAgent`, `TradeHandler`, `PositionAggregator`,
`TradePositionEngineApp`, existing tests, SBE schema, shared module.

---

## Verification

1. `./gradlew :media-driver:run` — MediaDriver starts, prints `AERON_DIR=...`, stays alive.
2. In a second terminal: `./gradlew :publisher:run -Daeron.dir=<path>` — completes after 1M trades.
3. In a third terminal: `./gradlew :receiver:run -Daeron.dir=<path> -Dsend.count=1000000`
   — prints HDR histogram and exits.
4. `./gradlew test --tests "*.MultiProcessPipelineIT"` — all 3 processes spawned, test passes.
5. `./gradlew test` — all existing tests still pass.
