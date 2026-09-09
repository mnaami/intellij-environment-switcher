# Implementation Plan: Env Switcher (generic IntelliJ plugin)

Spec: intellij-env-switcher/SPEC.md (approved 2026-09-09). No git commits by Claude.

## Overview
Generic Kotlin IntelliJ plugin (Marketplace-ready, self-contained folder):
toolbar env dropdown + settings page + PasswordSafe secrets + injection into
enabled Java-based run-config types + import of a folder of `.env` files.
SnapSIM is only the first dataset.
Built vertically: first slice proves injection end-to-end with hard-wired
state, then UI, then import, then phase-out.

## Architecture Decisions
- Injection via `com.intellij.java.programPatcher` (`JavaProgramPatcher`):
  runs for every Java-based config; filter on `targetConfigTypeIds` from
  settings (defaults: `Application`, `SpringBootApplicationConfigurationType`,
  `JarApplication`). Spring Boot type id is only a string, so no hard
  dependency; `envswitcher-springboot.xml` optional descriptor reserved for
  future Spring-specific extras. Fallback if patcher runs too early:
  `RunConfigurationExtension.updateJavaParameters`.
- State: `EnvironmentsService` project-level, `@State(name="EnvironmentSwitcher",
  storages=[Storage("environmentSwitcher.xml")])` → `.idea/environmentSwitcher.xml`.
  Selected env: `SelectedEnvironmentService` with `StoragePathMacros.WORKSPACE_FILE`.
- Secrets: `PasswordSafe.instance` with `CredentialAttributes(generateServiceName("Env Switcher", "$projectHash/$env/$key"))`.
- Toolbar: `ComboBoxAction`; register in `RunToolbarMainActionGroup` (new UI) and
  `ToolbarRunGroup` (classic), `anchor="before" relative-to-action="RunConfiguration"`.
- Build: Gradle wrapper 8.10, `org.jetbrains.intellij.platform` 2.x,
  `intellijIdeaCommunity("2024.2")` + `pluginVerifier` IDEs (IC + IU, 2024.2 + latest),
  extra `runIdeUltimate` task for Spring Boot manual checks, Kotlin 2.0,
  JVM toolchain 17 via foojay resolver (machine only has JDK 25).
- Packaging: id `io.github.mnaami.environmentswitcher`, MIT LICENSE, CHANGELOG,
  GitHub Actions build.yml (build + verifyPlugin), `signPlugin`/`publishPlugin`
  configured from env vars, never run by Claude.
- Umbrella-repo `.gitignore`: `.idea` → `.idea/*` + `!.idea/environmentSwitcher.xml`
  (ask-first, approved by spec). Plugin folder has its own `.gitignore`.
- Genericity guard: `NoVendorStringsTest` fails the build on "snapsim|ooredoo" under `src/main`.
- Tests: JUnit 5 for pure code; `BasePlatformTestCase` (JUnit 4 style) for platform bits.

## Dependency Graph
```
T1 gradle skeleton + plugin.xml (runIde boots)
 └─ T2 model + EnvironmentsService + SelectedEnvironmentService (+XML round-trip test)
     ├─ T3 SecretStore (PasswordSafe) + VariableResolver (+tests)
     │    └─ T4 EnvProgramPatcher + TargetConfigTypes (+platform test) ── first e2e slice with seeded state
     │         └─ T5 toolbar ComboBoxAction (+ per-env confirm flag, colour)
     ├─ T6 DotEnvParser + EnvFolderImporter (+tests)      ── parallel with T3-T5
     └─ T7 settings Configurable + table UI (+ import button, secret masking, "apply to" types)
T8 README/LICENSE/CHANGELOG/CI, buildPlugin zip, verifyPlugin IC+IU, manual checklist
T9 phase-out (.run regen without EnvFile, delete env/, scripts, bats) — only after T8 verified by user
```
Parallel: T6 with T3-T5; T7 after T2 (uses T3 for secrets, T6 for import button).

## Task List

### Phase 1: Skeleton + state
- [x] **T1 Gradle skeleton** (M)
  - `build.gradle.kts`, `settings.gradle.kts` (foojay), `gradle.properties`,
    wrapper, own `.gitignore`, `plugin.xml` (id `io.github.mnaami.environmentswitcher`,
    sinceBuild 242, depends `com.intellij.modules.java`; optional
    `com.intellij.spring.boot`), bundle, `NoVendorStringsTest`.
  - Accept: `./gradlew build` green; `./gradlew runIde` opens sandbox with plugin listed.
  - Deps: none.
- [x] **T2 Model + persistent state** (M)
  - `Environment`, `EnvironmentsState`, `EnvironmentsService`, `SelectedEnvironmentService`.
  - Accept: XML round-trip test passes; secretKeys persisted, no secret values field exists.
  - Verify: `./gradlew test --tests '*EnvironmentsServiceTest*'`.
  - Deps: T1.

### Checkpoint 1
- [x] Build + tests green; sandbox boots.

### Phase 2: Injection slice (risk first)
- [x] **T3 SecretStore + VariableResolver** (M)
  - Accept: precedence common<env<service<secret; missing secret reported; unknown env → empty + flag.
  - Verify: `VariableResolverTest` (JUnit5), `SecretStoreTest` (platform, in-memory PasswordSafe).
  - Deps: T2.
- [x] **T4 JavaProgramPatcher injection** (M)
  - Applies to enabled type ids (`TargetConfigTypes`); respects explicit config env vars; balloon on missing secrets (once per launch).
  - Accept: platform test: Application config gets vars, JUnit (disabled) untouched, enabling JUnit flips it.
  - Verify: test + manual in `runIdeUltimate` on this project with seeded xml (`SPRING_PROFILES_ACTIVE` visible in Spring Boot console).
  - Deps: T3.
- [x] **T5 Toolbar dropdown** (S)
  - `EnvironmentComboBoxAction`: items per env, colour dot (per-env setting), separator, "Edit Environments…"; per-env "confirm before run" once/session; empty state "No environments · Configure…".
  - Accept: visible new UI + classic; selection persists across IDE restart (workspace).
  - Verify: manual in runIde; unit test on item building.
  - Deps: T4 (for demo), T2.

### Checkpoint 2 (user demo)
- [ ] PENDING USER: Pick env in toolbar → run Offer → right profile/issuer/PORT in console.

### Phase 3: Data entry + import
- [x] **T6 DotEnvParser + EnvFolderImporter** (M)
  - Parser: comments, blank, `=` in value, CRLF, no expansion, quotes literal.
  - Importer (generic folder convention): `<name>.env`→envs, optional
    `common.env`→common, `overrides/*.env` or `services/*.env`→overrides,
    any `*.example` template→secretKeys; `secrets.<name>*.env` → PasswordSafe, never to state.
  - Verify: tests against neutral `src/test/resources/env-sample` (acme names, same layout as SnapSIM `env/`).
  - Deps: T2, T3.
- [x] **T7 Settings page** (L → split: T7a table panel, T7b configurable + import button)
  - T7a `EnvironmentTablePanel`: key/value/secret columns, masked editor for secret rows, add/remove row.
  - T7b `EnvironmentsConfigurable` (Settings > Tools > Env Switcher): env tabs (colour, confirm flag), common tab, overrides tab, "Apply to run configuration types" checklist, add/rename/delete env, "Import folder…" button, apply/reset.
  - Accept: edits persist; secret values go to PasswordSafe only; cancel discards.
  - Verify: manual checklist; unit test for panel model ↔ state mapping.
  - Deps: T2, T3, T6.

### Checkpoint 3
- [x] (automated part) Import from `env/` populates 4 envs + 18 overrides; secrets entered once; run works.

### Phase 4: Ship
- [x] **T8 Packaging + docs** (M)
  - `buildPlugin`; `verifyPlugin` on IC+IU 2024.2 + latest; README (install, usage, folder import convention, screenshots), LICENSE MIT, CHANGELOG, `.github/workflows/build.yml`; umbrella `.gitignore` exception for `.idea/environmentSwitcher.xml`.
  - Accept: zip installs from disk on a second IDE (Community too); envs appear; `grep -riE "snapsim|ooredoo" src/` empty; folder builds when copied alone.
  - Deps: T5, T7.
- [ ] **T9 Phase-out file-based setup** (M) — after user confirms T8
  - Regenerate `.run` without EnvFile block; remove `env/` (keep `services.tsv`
    as import fixture? no: import already done, delete), `scripts/use-env.sh`,
    `check-env.sh`, `tests/env`; fold `env/README.md` into plugin README.
  - Deps: T8 + user go.

### Checkpoint: Complete
- [ ] SPEC success criteria 1-6 met; user commits.

## Risks and Mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| `JavaProgramPatcher` not invoked for Spring Boot configs in 2024.2 | High | T4 is first slice; fallback `RunConfigurationExtension` |
| Toolbar group ids differ between new/classic UI or versions | Med | Register in both groups; verify in runIde early (T5) |
| Gradle needs to download IDE (~1 GB) + JDK 17 behind proxy | Med | Use `proxy` NO_PROXY settings / gradle.properties proxy; first `runIde` may take long |
| PasswordSafe in tests | Low | Test framework provides in-memory implementation |
| Secrets typed into table leak into undo/logs | Med | Masked editor, never log values, state holds keys only |
| `.idea/*` ignore change exposes other files | Low | Single explicit negation; review `git status` |
| Marketplace id/name collision | Med | Decide id before first publish (Q2); name check on plugins.jetbrains.com |
| Config type ids differ across IDE versions | Low | Settings show detected types from `ConfigurationType.CONFIGURATION_TYPE_EP` with ids; defaults by id string |

## Unresolved Questions
1. Proxy settings for Gradle/JetBrains downloads on this machine? (needed for T1)
2. Plugin id/package `io.github.mnaami.environmentswitcher` and name "Env Switcher": confirm GitHub handle and name (cannot change id after first publish).
3. License MIT ok?
