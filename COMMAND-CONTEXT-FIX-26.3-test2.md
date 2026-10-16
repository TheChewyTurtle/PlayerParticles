# PlayerParticles command context repair: 26.3 test2

Candidate: `build/libs/PlayerParticles-8.13-FOLIA-26.3-test2.jar`

SHA-256: `7f89794e176aa86a5b1453231877cb21a80f97b49ccd4f89531f4f25e7bcc98c`

The actual player session reproduced `/pp fixed create looking flame normal` failing at18:32:55: the command ran on Folia Async Scheduler Thread11 and tried to read world blocks through getTargetBlock. Evidence is `evidence/feature-pass2-oaths-test3.log` lines630–652 in the Turtle26.3 root. CommandManager moved every `/pp` module to an async task; DataManager's cached callback then executed inline on that task. Its uncached callback can also arrive on a global scheduler, which does not own the player.

CommandManager now schedules only the player-data lookup asynchronously and then explicitly schedules every player module's permission checks, parsing and execution at the captured player entity. Both cached and delayed results use this path. This also preserves the database boundary when DataManager would otherwise perform a cold lookup inline on a tick thread. Queued command work is discarded if the player disconnected. `/ppo` retains sender-side initial dispatch and separately transfers its asynchronously loaded target module to the target player's entity scheduler. There is no special case in the looking/ray parser.

Non-player command execution uses the sender's available context: console/RCON globally, command blocks at their block location, and command minecarts at their entity. The existing console permission/module rules remain in place. Fixed-effect parsing, storage, particle style loops and tab completion were not changed. Explicit remote locations and other existing Folia paths are not certified by this narrow command repair.

## Validation

Gradle9.4.1 offline build passed on Java25 using the existing workspace dependency cache. The restricted shell initially blocked Gradle's local daemon loopback connection; the normal isolated offline build completed after automatic escalation approval. No remote dependency downloads were used in the successful build.

Twelve regression cases pass with no failures/errors/skips. A real CommandManager and OtherCommandModule run against controlled data/scheduler boundaries. Cases cover asynchronous lookup before delayed callbacks for fixed/add/gui/reset/group modules, inline cached callbacks, permission rejection on the player context, disconnect during lookup, console/block/minecart scheduler routing, and `/ppo` target dispatch. These tests validate scheduling boundaries and do not substitute for an actual Folia ray-trace/player retest.

Evidence: `build-26.3-test2-context.log`, `build/test-results/test/TEST-dev.esophose.playerparticles.manager.CommandContextTest.xml`, and `build/reports/tests/test/index.html`. The packaged jar declares version8.13-FOLIA-26.3-test2, API26.3 and Folia support. It preserves upstream Java8 main bytecode (major52) while compiling against the official Folia26.3 API on Java25. The new dispatch helper is packaged; Bukkit/Folia server API and JUnit/Mockito test classes are not bundled.

Changed files: `build.gradle`, CommandManager, OtherCommandModule, new CommandDispatch and CommandContextTest. Resource processing now declares the plugin version as an input so an incremental build cannot reuse the previous filtered descriptor. Existing test1 jar remains untouched. No installed jars, live data or server process were changed by the repair agent. Parent acceptance should repeat real looking-create, list, edit and remove, then ordinary particle/group commands and console `/ppo` targeting the player while checking for fresh thread errors.
