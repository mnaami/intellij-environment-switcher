# Spec: Env Switcher (IntelliJ plugin, generic, publishable)

## Objective

A generic, Postman-style environment switcher for IntelliJ: a dropdown in the
main toolbar, next to the run-configuration dropdown, that lists named
environments (dev, test, staging, prod, ...) and applies the selected one as
environment variables to run configurations launched from the IDE.
Environments are managed in a settings page, shared through the project file,
with secrets kept in the OS keychain.

The plugin is a standalone product intended for JetBrains Marketplace.
SnapSIM is the first user, not a dependency: nothing SnapSIM-specific (names,
hosts, service lists, folder layouts) appears in code or defaults. The SnapSIM
setup is just data imported from its `env/` folder, and this replaces the
EnvFile plugin and `use-env.sh` symlink built earlier for that project.

Users: any JVM developer on IntelliJ IDEA 2024.2+ (Community or Ultimate),
macOS/Windows/Linux. Primary case: Spring Boot microservices, but any
Java-based run configuration works.

Success looks like:
- Pick `staging` in the toolbar, press Run on `Offer`: the process starts with
  the staging variables and the developer's staging secrets, no dialog, no
  file edit.
- A new developer opens the project, sees the environments already there,
  enters their secrets once per environment, and is productive.
- No secret value ever lands in a committed file.

### Assumptions (validated with the user 2026-09-09)
1. Storage is inside the plugin, Postman-style: shared values in a committed
   project file, edited in a settings UI. The `env/` folder is imported once,
   then retired.
2. Secrets are stored per environment in IntelliJ PasswordSafe (OS keychain).
3. Variables are injected at launch into every Java-based run configuration
   whose type is enabled in settings (defaults on: Spring Boot, Application,
   Gradle-less JAR; defaults off: JUnit/TestNG). No per-config checkbox.
4. Kotlin, Gradle IntelliJ Platform Plugin 2.x. Code lives for now in
   `intellij-env-switcher/` inside the umbrella repo but is a self-contained
   Gradle root (own README, LICENSE, .gitignore, CI file) so it can be moved to
   its own repository with `git filter-repo`/copy and published unchanged.
5. Target IntelliJ IDEA 2024.2 and newer; base platform is IntelliJ IDEA
   Community, Spring Boot support is an *optional* dependency so the plugin
   loads in Community too.
6. Generic by construction (user, 2026-09-09): no product names in code,
   ids, defaults or bundle strings.

## Tech Stack

- Kotlin 2.x (JVM 17 toolchain, as required by 2024.2 platform)
- Gradle 8.x, `org.jetbrains.intellij.platform` plugin 2.x
- Platform: `intellijIdeaCommunity("2024.2")` for the build; optional
  dependency on `com.intellij.spring.boot` (via `<depends optional="true"
  config-file="envswitcher-springboot.xml">`) for Spring Boot recognition;
  `runIde` variant against Ultimate for manual checks
- Plugin id `io.github.mnaami.environmentswitcher` (open question 5), display name
  "Env Switcher", vendor Mohammed Nami, MIT license
- Marketplace readiness: `pluginVerifier` across 2024.2 → latest, no
  `untilBuild`, change-notes, description with screenshots, signed zip
  (`signPlugin`/`publishPlugin` wired but keys supplied by the user)
- Kotlin UI DSL v2 for the settings page, `ComboBoxAction` for the toolbar
- `PersistentStateComponent` (project level) for shared state,
  `PasswordSafe` for secrets
- Tests: JUnit 5 + IntelliJ Platform test framework (`BasePlatformTestCase`)

## Commands

```bash
cd intellij-env-switcher

./gradlew build                 # compile + test + verify plugin.xml
./gradlew test                  # unit + light platform tests
./gradlew runIde                # launch a sandbox IDE with the plugin
./gradlew buildPlugin           # -> build/distributions/env-switcher-<ver>.zip
./gradlew verifyPlugin          # binary compatibility against declared IDE range
./gradlew ktlintCheck           # style (ktlint gradle plugin)
```

Install: Settings > Plugins > gear > Install Plugin from Disk > the zip.

## Project Structure

```
intellij-env-switcher/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties                 → pluginVersion, platformVersion=2024.2
  src/main/resources/META-INF/plugin.xml
  src/main/kotlin/io/github/mnaami/environmentswitcher/
    model/
      Environment.kt                → name, variables: Map<String,String>, secretKeys: Set<String>
      EnvironmentsState.kt          → environments, commonVariables, serviceOverrides, version
    state/
      EnvironmentsService.kt        → project-level PersistentStateComponent, @State(storages=[Storage("environmentSwitcher.xml")]) -> .idea/environmentSwitcher.xml (project-shared; SnapSIM repo adds a .gitignore exception)
      SelectedEnvironmentService.kt → project-level, workspace file (not shared): selected env name
      SecretStore.kt                → PasswordSafe wrapper: get/set/delete(env, key)
    resolve/
      VariableResolver.kt           → merges common < env < service override < secrets -> Map
    inject/
      EnvProgramPatcher.kt          → JavaProgramPatcher: adds resolved vars to JavaParameters.env for enabled config types
      TargetConfigTypes.kt          → which run-config type ids receive variables (user setting)
    ui/
      EnvironmentComboBoxAction.kt  → toolbar dropdown
      EnvironmentsConfigurable.kt   → Settings > Tools > Env Switcher (envs, common, overrides, "apply to" config types)
      EnvironmentTablePanel.kt      → key / value / secret table with masked editor
    importer/
      DotEnvParser.kt               → parses KEY=VALUE files (comments, blank lines, no expansion)
      EnvFolderImporter.kt          → import a folder of <name>.env files; optional common.env, overrides/<module>.env (also services/), secrets template (*.example) marks secret keys
  src/test/kotlin/io/github/mnaami/environmentswitcher/
    resolve/VariableResolverTest.kt
    importer/DotEnvParserTest.kt
    importer/EnvFolderImporterTest.kt
    state/EnvironmentsServiceTest.kt        → XML round-trip
    inject/EnvProgramPatcherTest.kt         → light platform test: Application config gets vars; disabled type untouched
  src/test/resources/env-sample/            → neutral fixture (acme-style names), same layout as SnapSIM env/
  README.md, LICENSE (MIT), CHANGELOG.md, .gitignore, .github/workflows/build.yml
  SPEC.md, PLAN.md
```

### Data model

```
EnvironmentsState
  version: Int = 1
  commonVariables: Map<String, String>          # e.g. HOST_IP_ADDRESS
  environments: List<Environment>               # dev, test, staging, prod
    Environment(name, variables, secretKeys)    # secretKeys: names whose value lives in PasswordSafe
  overrides: Map<moduleName, Map<String,String>>          # e.g. offer-service -> PORT=9092
  targetConfigTypeIds: Set<String>                        # run-config types that receive variables
```

Resolution for a run configuration of module `m` in environment `e`:
`common` overridden by `e.variables` overridden by `overrides[m]`
overridden by secrets from PasswordSafe for `(e, key in e.secretKeys)`.
Existing variables set explicitly on the run configuration always win.
Missing secret: variable is not set, and a balloon warns once per launch
listing the missing keys.

### Toolbar

`ComboBoxAction` registered in the run toolbar group, placed before the
run-configuration combo (new UI: group `RunToolbarMainActionGroup`, verify at
implementation). Shows the selected env name with a per-environment colour chosen in
settings (default palette; "confirm before run" flag per env replaces the
hard-coded prod rule). Items: one per environment, separator, "Edit Environments...".

## Code Style

Kotlin official style, ktlint defaults, 4-space indent, no wildcard imports.
No product-specific words (SnapSIM, Ooredoo, hostnames) anywhere under `src/`; a test greps for them.
Services are `@Service(Service.Level.PROJECT)` classes retrieved via
`project.service<T>()`. UI strings in `messages/EnvSwitcherBundle.properties`.
One class per file; test names in backticks describe behaviour.

```kotlin
class VariableResolver(
    private val state: EnvironmentsState,
    private val secrets: SecretStore,
) {
    fun resolve(envName: String, moduleName: String?): Resolution {
        val env = state.environments.firstOrNull { it.name == envName }
            ?: return Resolution.empty(missingEnvironment = envName)
        val merged = LinkedHashMap(state.commonVariables)
        merged.putAll(env.variables)
        moduleName?.let { state.serviceOverrides[it] }?.let(merged::putAll)
        val missing = mutableListOf<String>()
        for (key in env.secretKeys) {
            secrets.get(envName, key)?.let { merged[key] = it } ?: missing.add(key)
        }
        return Resolution(merged, missingSecrets = missing)
    }
}
```

## Testing Strategy

- **Unit (JUnit 5, plain)**: `DotEnvParser`, `VariableResolver`,
  `EnvFolderImporter` against the neutral `src/test/resources/env-sample`; a
  `NoVendorStringsTest` greps `src/main` for product names. Target: every
  branch of precedence and every parser edge case (comments, `=` in values,
  quotes kept literally, CRLF).
- **Light platform tests (`BasePlatformTestCase`)**: `EnvironmentsService`
  state round-trip through XML; `EnvProgramPatcher` sets the expected
  variables on an Application run configuration and leaves a disabled type
  (JUnit) untouched; Spring Boot path checked manually in the Ultimate sandbox; `SecretStore` against the in-memory PasswordSafe
  used by the test framework.
- **Manual (runIde, checklist in README)**: dropdown visible in new and
  classic UI; switching env and running `Offer` shows the right
  `SPRING_PROFILES_ACTIVE` in the console; missing-secret balloon; settings
  page edit, apply, cancel; import from `env/` folder.
- Coverage expectation: resolver, parser, importer at 100% branch; no
  coverage gate on UI classes.

## Boundaries

- **Always**
  - `./gradlew build` green before handing over a task.
  - Keep secrets out of `EnvironmentsState`; only key names are persisted.
  - Store the selected environment in the workspace file, never in the shared
    state file.
  - Keep the plugin generic: no product names, hosts or service lists in
    code, ids, defaults or resources; SnapSIM data exists only as imported state.
  - Keep the folder self-contained (no references to files outside
    `intellij-env-switcher/`), ready to become its own repository.
- **Ask first**
  - Adding any dependency beyond the platform, Kotlin stdlib and test libs.
  - Changing the `.run/*.run.xml` generator or deleting `env/` (phase-out
    steps happen only after the plugin is installed and verified).
  - Lowering the `sinceBuild` below 242; adding a hard (non-optional)
    dependency on any Ultimate-only plugin.
  - Publishing to Marketplace or signing (user runs `publishPlugin` with their token).
- **Never**
  - `git commit` or `git push`; the user commits.
  - Write secret values to disk in plain text, logs included.
  - Modify files inside the service submodules.

## Success Criteria

1. In a sandbox IDE opened on this project, the toolbar shows an environment
   dropdown listing dev, test, staging, prod after importing the current
   `env/` folder through Settings > Tools > Env Switcher > Import.
2. Selecting `staging` and running `Offer` starts the JVM with
   `SPRING_PROFILES_ACTIVE=staging`, the staging Keycloak issuer, `PORT=9092`
   and the staging datasource password from PasswordSafe; selecting `dev`
   then running again flips all of them without touching the run config.
3. Running a non-Spring-Boot configuration (a JUnit test) is unaffected.
4. `.idea/environmentSwitcher.xml` contains no value for any key marked secret;
   `grep -i password` on it returns key names only.
5. `./gradlew build verifyPlugin` passes for IntelliJ IDEA Community and
   Ultimate, 2024.2 and the latest 2025.x; the plugin loads in Community
   (Spring Boot features simply absent).
6. `buildPlugin` zip installs from disk on a colleague's IDE and the
   environments appear without any extra file.
7. `grep -riE "snapsim|ooredoo" src/` returns nothing; the folder builds when
   copied alone to an empty directory.

## Phase-out of the file-based setup (after success criteria met)

- Regenerate `.run/*.run.xml` without the EnvFile extension block.
- Delete `env/`, `scripts/use-env.sh`, `scripts/check-env.sh` secret rules,
  `tests/env/*.bats`; keep `services.tsv` and `gen-run-configs.sh` only if the
  run configs are still generated.
- Update `env/README.md` into `intellij-env-switcher/README.md`.

## Open Questions
5. Plugin id / package: `io.github.mnaami.environmentswitcher` assumes a GitHub
   account `mnaami`. Confirm handle or choose another reverse-domain id
   (must be unique on Marketplace, cannot change after first publish).
6. Display name "Env Switcher" may collide on Marketplace; alternatives:
   "Run Environments", "EnvBoard", "Env Profiles".

1. Resolved (user, 2026-09-09): shared state file is
   `.idea/environmentSwitcher.xml`. The repo ignores `.idea` as a whole, and
   git cannot re-include a file under an ignored directory, so `.gitignore`
   changes from `.idea` to `.idea/*` plus `!.idea/environmentSwitcher.xml`.
   This is part of the plan (ask-first item: `.gitignore` edit).
2. Per-environment secret entry: one masked field per key in the table, or a
   single "Enter secrets for <env>" dialog? Default: inline in the table.
3. Should `prod` require a confirmation dialog on Run? Default: yes, once per
   session.
4. Import: run automatically when `env/` exists and state is empty, or only
   via the "Import from env folder" button? Default: button plus a one-time
   notification suggesting it.
