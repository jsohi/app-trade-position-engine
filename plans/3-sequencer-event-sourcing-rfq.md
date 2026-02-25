# Plan: Sequencer + Event Sourcing with FIX RFQ

## Context

The trade position engine currently has a direct pipeline: encode trade -> Aeron IPC -> decode -> aggregate position. There is no command/event separation, no sequencing, and no event sourcing. We want to introduce a **Sequencer** (single-threaded command processor that assigns monotonic sequence numbers and emits events) and **Event Sourcing** (commands in, events out, events update read-side caches). The first use case is **FIX-inspired RFQ (Request for Quote)** lifecycle.

The existing trade pipeline remains **completely untouched** — RFQ is a parallel pipeline alongside it.

## Architecture

```
RFQ Client/Gateway
    |  (encodes RequestQuoteCmd / ProposeQuoteCmd / AcceptQuoteCmd / ...)
    v
Aeron IPC (stream 20: commands inbound)
    |
    v
SequencerAgent  [single-threaded — the single writer]
    |-- RfqCommandHandler decodes SBE command by templateId
    |-- RfqAggregate.validate(command) — state machine check
    |-- sequenceNumber++ (monotonic)
    |-- RfqAggregate.apply(event) — mutate in-memory state
    |-- RfqEventPublisher.publish(event) → stream 21
    v
Aeron IPC (stream 21: events outbound)
    |
    v
RfqReceiveAgent → RfqEventHandler → RfqProjection
    (updates read-side caches: rfqCache, quoteCache)
```

**Stream layout:** 10=trades (existing), 11=audit (existing), **20=RFQ commands**, **21=RFQ events**

## SBE Schema Additions (`shared/src/main/resources/messages.xml`)

New types:
- `QuoteReqIdType` — char[32] ASCII (correlates request to quotes)
- `QuoteIdType` — char[32] ASCII (unique quote identifier)
- `PartyIdType` — char[32] ASCII (client/dealer identifier)
- `SymbolType` — char[8] ASCII (security symbol, e.g. "AAPL")
- `RfqSideType` enum — BUY=0, SELL=1, BOTH=2
- `RfqStateType` enum — REQUESTED=0, QUOTED=1, ACCEPTED=2, REJECTED=3, CANCELLED=4, EXPIRED=5
- `SequenceNumberType` — uint64

**Command messages (template IDs 10-14):**
| ID | Message | Key fields |
|----|---------|------------|
| 10 | `RequestQuoteCmd` | quoteReqId, symbol, side, quantity, partyId, timestampNanos |
| 11 | `ProposeQuoteCmd` | quoteReqId, quoteId, symbol, bidPx, offerPx, bidSize, offerSize, validUntilTime, partyId, timestampNanos |
| 12 | `AcceptQuoteCmd` | quoteReqId, quoteId, partyId, timestampNanos |
| 13 | `RejectQuoteCmd` | quoteReqId, quoteId, partyId, timestampNanos, reason (var-length) |
| 14 | `CancelQuoteCmd` | quoteReqId, partyId, timestampNanos |

**Event messages (template IDs 20-24):**
| ID | Message | Extra fields vs command |
|----|---------|------------------------|
| 20 | `QuoteRequestedEvt` | + sequenceNumber, state |
| 21 | `QuoteProposedEvt` | + sequenceNumber, state |
| 22 | `QuoteAcceptedEvt` | + sequenceNumber, state |
| 23 | `QuoteRejectedEvt` | + sequenceNumber, state, reason |
| 24 | `QuoteCancelledEvt` | + sequenceNumber, state |

Events mirror their commands but add `sequenceNumber` (monotonic, assigned by sequencer) and `state` (the resulting RFQ state after applying).

## RFQ State Machine

```
         RequestQuote
              |
              v
         REQUESTED
              |
         ProposeQuote
              |
              v
           QUOTED
         /    |    \
  Accept   Reject  Cancel
     |        |       |
     v        v       v
 ACCEPTED REJECTED CANCELLED
```
Terminal states: ACCEPTED, REJECTED, CANCELLED, EXPIRED. No further commands accepted.

## Implementation Phases

### Phase 1: SBE Schema + Shared Constants
- **Modify** `shared/src/main/resources/messages.xml` — add all new types, 5 command messages, 5 event messages
- **Create** `shared/src/main/java/com/bofa/equity/rfq/RfqConstants.java` — stream IDs (20, 21)
- Run `./gradlew :shared:generateCodecs` to generate encoder/decoder flyweights
- Verify: `./gradlew build` — existing tests still pass

### Phase 2: Sequencer Module — Core Logic
- **Modify** `settings.gradle` — add `include(':sequencer')`
- **Create** `sequencer/build.gradle` — follows `receiver/build.gradle` pattern (application plugin, JVM args, system properties passthrough)
- **Create** `sequencer/src/main/java/com/bofa/equity/`:
  - `SequencerApp.java` — main entry point (connects to Aeron, subscribes stream 20, publishes stream 21, runs SequencerAgent)
  - `agents/SequencerAgent.java` — implements Agrona `Agent`; polls commands, validates via aggregate, assigns sequence number, emits events
  - `rfq/RfqAggregate.java` — state machine + validation (`HashMap<String, RfqState>`); validate + apply methods per command type
  - `rfq/RfqState.java` — mutable per-RFQ state (currentState, quoteReqId, quoteId, symbol, quantity, bidPx, offerPx, bidSize, offerSize, requestorPartyId, dealerPartyId)
  - `command/RfqCommandHandler.java` — SBE header decode + templateId dispatch (follows `TradeHandler` pattern)
  - `event/RfqEventPublisher.java` — SBE event encoding + Aeron publication (follows `TradeCodec` pattern with flyweight reuse)
- **Create** `sequencer/src/main/resources/log4j2.xml` — copy existing pattern

### Phase 3: Sequencer Unit Tests
- **Create** `sequencer/src/test/java/com/bofa/equity/`:
  - `rfq/RfqAggregateTest.java` — valid transitions (REQUESTED→QUOTED→ACCEPTED), invalid transitions (propose on non-existent, accept on REQUESTED), duplicate quoteReqId rejection
  - `agents/SequencerAgentTest.java` — command→event flow with correct sequence numbers
  - `util/RfqTestCodecs.java` — encode/decode utility for commands and events (follows `TradeTestCodecs` pattern)

### Phase 4: Receiver-Side Event Projection
- **Create** `receiver/src/main/java/com/bofa/equity/rfq/`:
  - `RfqCacheEntry.java` — mutable read-side state per RFQ (implements `StringBuilderFormattable` for GC-free logging)
  - `RfqProjection.java` — updates HashMap caches from events; `rfqCache` (by quoteReqId) + `quoteCache` (by quoteId)
  - `RfqEventHandler.java` — SBE event templateId dispatch → projection
- **Create** `receiver/src/main/java/com/bofa/equity/agents/RfqReceiveAgent.java` — polls stream 21, delegates to RfqEventHandler
- **Create** unit tests for projection and event handler

### Phase 5: Integration Test
- **Modify** root `build.gradle` — add `testImplementation project(':sequencer')`
- **Create** `src/test/java/com/bofa/equity/RfqPipelineIT.java` — embedded MediaDriver; publish commands → sequencer → verify events arrive and projection has correct state (ACCEPTED with all fields)

## Files Summary

| Action | File |
|--------|------|
| Modify | `settings.gradle` — add `:sequencer` |
| Modify | `build.gradle` (root) — add `testImplementation project(':sequencer')` |
| Modify | `shared/src/main/resources/messages.xml` — RFQ types + 10 messages |
| Create | `shared/.../rfq/RfqConstants.java` |
| Create | `sequencer/build.gradle` |
| Create | `sequencer/.../SequencerApp.java` |
| Create | `sequencer/.../agents/SequencerAgent.java` |
| Create | `sequencer/.../rfq/RfqAggregate.java` |
| Create | `sequencer/.../rfq/RfqState.java` |
| Create | `sequencer/.../command/RfqCommandHandler.java` |
| Create | `sequencer/.../event/RfqEventPublisher.java` |
| Create | `sequencer/.../rfq/RfqAggregateTest.java` |
| Create | `sequencer/.../agents/SequencerAgentTest.java` |
| Create | `sequencer/.../util/RfqTestCodecs.java` |
| Create | `receiver/.../rfq/RfqCacheEntry.java` |
| Create | `receiver/.../rfq/RfqProjection.java` |
| Create | `receiver/.../rfq/RfqEventHandler.java` |
| Create | `receiver/.../agents/RfqReceiveAgent.java` |
| Create | `receiver/.../rfq/RfqProjectionTest.java` |
| Create | `src/test/.../RfqPipelineIT.java` |

**No changes to**: SendAgent, ReceiveAgent, TradeHandler, PositionAggregator, TradeCodec, PublisherApp, ReceiverApp, MediaDriverApp, or any existing tests.

## Key Design Decisions

1. **Separate SBE template IDs per command/event** — idiomatic SBE dispatch via `templateId`, no envelope overhead
2. **Dedicated Aeron streams** (20/21) — clean separation from existing trade pipeline, independent back-pressure
3. **In-memory event journal for Phase 1** — Aeron IPC log provides natural replay within retention window; Aeron Archive is a future phase
4. **String-keyed aggregate map** — RFQ rate is orders of magnitude lower than trade rate; String keys acceptable here
5. **Back-pressure handling** — sequencer busy-spins on event publication retry, never drops events (events are facts)

## Verification

After all phases:
```bash
./gradlew build              # all modules compile, all tests pass
./gradlew :sequencer:test    # sequencer unit tests pass
./gradlew :receiver:test     # projection unit tests pass
./gradlew :test --tests "com.bofa.equity.RfqPipelineIT"  # integration test passes
```
