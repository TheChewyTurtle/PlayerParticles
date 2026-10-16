# PlayerParticles — tested Folia 26.3 candidate

This source snapshot corresponds to `8.13-FOLIA-26.3-test2`, tested on October 15, 2026. It remains a test candidate; full plugin acceptance is incomplete.

- Runtime: Folia `26.3-3-ver/26.3.x@597e413`, API `26.3.build.3-alpha`, Java `25.0.4.1`.
- Compile API: `dev.folia:folia-api:26.3.build.3-alpha`; descriptor API version `26.3` and Folia support enabled.
- Tested jar SHA-256: `7f89794e176aa86a5b1453231877cb21a80f97b49ccd4f89531f4f25e7bcc98c`.
- Main-class bytecode major: `52`.
- Source base: `1a7e921b61d3048088ac88877b0f6dd0846ae378` from the existing `master` checkout.

The jar hash identifies the actual installed test artifact. Binaries, dependency caches, live configuration/data and build logs are not added by this commit. Rebuilt archives may differ in timestamps or packaging; this publication verifies source/build-file byte parity with the tested candidate, rather than claiming a reproducible binary rebuild. Existing historical tracked build artifacts are left untouched.

## Change and build

Keeps player-data lookup asynchronous and transfers permission checks, parsing and module execution to the owning player or target entity. Console, command-block and minecart dispatch use their available scheduler contexts. Adds command-context regressions and version-sensitive resource processing.

JDK 25 and the included Gradle 9.4.1 wrapper. The build retains upstream Java 8 main bytecode while compiling against Folia 26.3. Dependency resolution includes mavenLocal() and an optional ../../maven-repository cache before public repositories; that relative path was the isolated test cache and is not bundled. The validated build was offline against its prepared cache; cold external dependency resolution was not repeated. Maven publishing credentials are optional external project properties, not committed values.

```text
./gradlew clean test shadowJar
# Windows: .\gradlew.bat clean test shadowJar
```

## Validation and limits

12 project regression cases passed with zero failures, errors or skips. The overall 15-plugin testing effort passed 195 unique project checks; that total is not this project's test count and is not a count of in-game actions. The publishing copies were not rebuilt because their historical tracked build outputs must remain unchanged.

A real player reproduced the old fixed-looking asynchronous world-read failure. The corrected candidate passed fixed create/edit/render, console target-player commands, saved-group save/reset/load, restart persistence and removal. Final active particles, fixed effects and named saved groups were cleared; the temporary fixed permission was revoked.

Other effects/styles, real command-block/minecart execution, two-player visibility and distant-region interactions remain unverified. The scheduler tests use controlled boundaries and do not certify every Folia feature. All 15 full acceptance checkboxes in the test effort remain open. The original detailed player evidence is retained in the isolated test workspace; runtime player records are not included in this repository.
