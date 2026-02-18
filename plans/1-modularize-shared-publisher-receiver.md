# Plan: Modularize into shared / publisher / receiver

## Context

The project is currently a single flat module. The goal is to split it into three Gradle submodules so that shared domain code, the publishing side, and the receiving side each live in their own module with explicit dependency boundaries. The root project keeps the app entry point and orchestration.

## Target Module Structure

```
app-trade-position-engine/          ← root project (app orchestration)
├── settings.gradle
├── build.gradle
├── src/main/java/com/bofa/equity/
│   └── TradePositionEngineApp.java
└── src/main/resources/
    └── logback.xml

shared/                             ← submodule: common code
├── build.gradle
└── src/main/
    ├── java/com/bofa/equity/
    │   ├── cache/Cache.java, DefaultCache.java
    │   ├── trade/Trade.java
    │   └── sbe/[ALL generated SBE files]
    └── resources/
        ├── messages.xml
        └── sbe/sbe.xsd

publisher/                          ← submodule: send side
├── build.gradle
└── src/main/java/com/bofa/equity/
    ├── agents/SendAgent.java
    └── trade/TradeCodec.java, TradeEncoderHelper.java

receiver/                           ← submodule: receive side
├── build.gradle
└── src/
    ├── main/java/com/bofa/equity/
    │   ├── agents/ReceiveAgent.java
    │   ├── position/PositionAggregator.java, PositionData.java, DefaultPositionData.java
    │   └── trade/TradeHandler.java
    └── test/java/com/bofa/equity/
        ├── position/PositionAggregatorTest.java
        └── util/TradeTestCodecs.java
```

## File Moves (no package renames — all stay in com.bofa.equity.*)

| File | From | To |
|------|------|----|
| `Cache.java`, `DefaultCache.java` | `src/main/java/com/bofa/equity/cache/` | `shared/src/main/java/com/bofa/equity/cache/` |
| `Trade.java` | `src/main/java/com/bofa/equity/trade/` | `shared/src/main/java/com/bofa/equity/trade/` |
| `sbe/` (all generated) | `src/main/java/com/bofa/equity/sbe/` | `shared/src/main/java/com/bofa/equity/sbe/` |
| `messages.xml`, `sbe/sbe.xsd` | `src/main/resources/` | `shared/src/main/resources/` |
| `SendAgent.java` | `src/main/java/com/bofa/equity/agents/` | `publisher/src/main/java/com/bofa/equity/agents/` |
| `TradeCodec.java`, `TradeEncoderHelper.java` | `src/main/java/com/bofa/equity/trade/` | `publisher/src/main/java/com/bofa/equity/trade/` |
| `ReceiveAgent.java` | `src/main/java/com/bofa/equity/agents/` | `receiver/src/main/java/com/bofa/equity/agents/` |
| `TradeHandler.java` | `src/main/java/com/bofa/equity/trade/` | `receiver/src/main/java/com/bofa/equity/trade/` |
| `PositionAggregator.java`, `PositionData.java`, `DefaultPositionData.java` | `src/main/java/com/bofa/equity/position/` | `receiver/src/main/java/com/bofa/equity/position/` |
| `PositionAggregatorTest.java` | `src/test/java/com/bofa/equity/position/` | `receiver/src/test/java/com/bofa/equity/position/` |
| `TradeTestCodecs.java` | `src/test/java/com/bofa/equity/util/` | `receiver/src/test/java/com/bofa/equity/util/` |
| `logback.xml` | `src/main/resources/` | root `src/main/resources/` (stays) |
| `TradePositionEngineApp.java` | `src/main/java/com/bofa/equity/` | root `src/main/java/com/bofa/equity/` (stays) |

## Build Configuration Changes

### `settings.gradle` — add submodule declarations
```groovy
rootProject.name = 'app-trade-position-engine'
include(':shared')
include(':publisher')
include(':receiver')
```

### Root `build.gradle` — restructure
- Keep `allprojects {}` block to configure Java version, repositories, and compiler args for all modules
- Remove the `configurations { codecGeneration }`, `sourceSets { generated }`, and `generateCodecs` task from root — these move to `shared/build.gradle`
- Remove the `api files('build/classes/java/generated')` hack
- Root project retains `application` plugin, `mainClass`, and depends on `:publisher` and `:receiver`
- Root gets `implementation project(':publisher')` and `implementation project(':receiver')`
- slf4j + logback stay on root (runtime logging config for the app)

### `shared/build.gradle` — new file
- Plugin: `java-library`
- `configurations { codecGeneration }`
- `generateCodecs` JavaExec task (paths relative to `shared/` subproject dir)
- `generatedDir = layout.buildDirectory.dir("generated-src").get().asFile`
- Add `generatedDir` to `sourceSets.main.java.srcDirs` (no separate generated sourceSet — simpler)
- `compileJava.dependsOn('generateCodecs')`
- Dependencies: `api` aeron-all, agrona, decimal4j; `codecGeneration` sbe-tool

### `publisher/build.gradle` — new file
- Plugin: `java-library`
- Dependencies: `implementation project(':shared')`, aeron-all, agrona

### `receiver/build.gradle` — new file
- Plugin: `java-library`
- Dependencies: `implementation project(':shared')`, aeron-all, agrona, HdrHistogram
- Test dependencies: junit-jupiter-api, junit-jupiter-engine, junit-platform-launcher
- `test { useJUnitPlatform(); jvmArgs(...) }` block (same --add-opens as today)

## Dependency Graph

```
root (app)
├── depends on → :publisher
└── depends on → :receiver
                      └── depends on → :shared
:publisher
└── depends on → :shared
:shared
└── no internal deps
```

## Critical Files to Create/Modify

1. `settings.gradle` — add `include` declarations
2. `build.gradle` (root) — remove SBE generation, simplify to app orchestration
3. `shared/build.gradle` — new, owns SBE codec generation
4. `publisher/build.gradle` — new
5. `receiver/build.gradle` — new
6. Move all source files per table above (no code changes, only file system moves)

## Verification

```bash
# Clean build — all modules compile, all tests pass
./gradlew clean build

# Run the app — 1M messages, position table, HDR histogram
./gradlew run
```
