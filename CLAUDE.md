# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Trade position engine that publishes and consumes SBE-encoded trade messages over Aeron IPC, aggregating positions by account and security. Designed for low-latency using busy-spin I/O and non-blocking data structures.

## Build & Run Commands

```bash
# Build and run all tests
./gradlew build

# Run tests only
./gradlew test

# Run a single test class
./gradlew test --tests "com.bofa.equity.position.PositionAggregatorTest"

# Run the application (main entry point)
./gradlew run
```

**JDK 14+ requirement:** Add these VM arguments when running directly (not via Gradle):
```
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
```

**SBE codec generation** runs automatically before compilation via the `generateCodecs` Gradle task. Do not manually edit files under `src/main/java/com/bofa/equity/sbe/` — they are generated from `src/main/resources/messages.xml`.

## Architecture

### Data Flow

```
SendAgent (SBE-encodes trades)
  → Aeron Publication (aeron:ipc, stream 10)
  → MediaDriver (SHARED thread mode, BusySpinIdleStrategy)
  → Aeron Subscription
  → ReceiveAgent (polls up to 100 fragments/cycle)
  → TradeHandler (decodes SBE, delegates)
  → PositionAggregator (updates positions in-memory)
  → Console output (position table + HDR latency histogram)
```

### Key Packages

- **`agents/`** — `SendAgent` (publisher duty loop) and `ReceiveAgent` (subscriber duty loop). Both run on dedicated threads with `AgentRunner`.
- **`trade/`** — `Trade` (Java record), `TradeCodec` (SBE encoding wrapper), `TradeEncoderHelper` (static utilities), `TradeHandler` (decodes and dispatches to aggregator).
- **`position/`** — `PositionAggregator` aggregates trades into positions keyed by `(accountId, securityId)` using Agrona's `BiInt2ObjectMap` to avoid boxing. `PositionData` / `DefaultPositionData` hold buy/sell quantity and cumulative price.
- **`cache/`** — `Cache` / `DefaultCache` provide bidirectional mappings between string names (e.g., `"Acc1"`, `"0001.AX"`) and integer IDs. 10 accounts (1–10), 2000 securities (1–2000).
- **`sbe/`** — Generated SBE encoder/decoder flyweights. Wrap `DirectBuffer` / `MutableDirectBuffer` — never allocate on hot path.

### Performance Design Decisions

- **Busy-spin idle strategy** on both sender and receiver — lowest latency, high CPU cost.
- **SBE flyweight pattern** — encoders/decoders are reused instances wrapping buffers, not allocated per message.
- **Agrona `BiInt2ObjectMap`** — primitive key map avoids autoboxing for position lookups.
- **Single consumer thread** — `ReceiveAgent` is the sole writer to `PositionAggregator`, eliminating synchronization needs.
- **No autoboxing on hot path** — `Decimal4j` for garbage-free decimal ops; Agrona collections throughout.

### SBE Message Schema

`Trade` message (schema ID 100, template ID 1):
| Field | Type | Notes |
|---|---|---|
| referenceId | 32-char ASCII | Fixed-length string |
| accountId | uint8 | 1–10 |
| securityId | uint8 | 1–2000 |
| side | SideType enum | B=Buy, S=Sell |
| quantity | uint64 | |
| price | double | |
| timestampMillis | uint64 | |
| description | var-length UTF-8 | Audit text |

### Configuration

Message count is hardcoded in `TradePositionEngineApp.java`:
```java
final int sendCount = 1_000_000;
```

Aeron channel defaults to `aeron:ipc`. To switch to UDP:
```java
// Change channel to: "aeron:udp?endpoint=127.0.0.1:2000"
```

## Testing

Tests use JUnit 5. `PositionAggregatorTest` validates position aggregation logic. `TradeTestCodecs` in `test/util/` provides codec helpers for tests.

Latency output (HDR Histogram format) can be visualized at http://hdrhistogram.github.io/HdrHistogram/plotFiles.html.
