# Environment Switcher

Postman-style environments for IntelliJ run configurations.

Define named environments (dev, test, staging, prod, ...) once, pick one from a
dropdown next to the run configuration selector, and every run configuration
you launch gets that environment's variables. Secrets live in the IDE password
safe (your OS keychain), never in project files.

- Works in IntelliJ IDEA 2024.2+, Community and Ultimate.
- Applies to Java-based run configurations: Application, JAR Application and
  Spring Boot by default; enable any other type in settings.
- Team-shareable: environments are stored in `.idea/environmentSwitcher.xml`; the
  selected environment and all secret values stay on your machine.

## Install

From a release zip: *Settings | Plugins | ⚙ | Install Plugin from Disk…*.

Build it yourself:

```bash
./gradlew buildPlugin        # build/distributions/env-switcher-<version>.zip
```

## Use

1. *Run | Edit Environments…* (also at the bottom of the toolbar dropdown): add
   environments and their variables.
   Tick **Secret** on a row to keep its value out of the project file.
2. Pick an environment in the toolbar dropdown (left of the run widget).
3. Run. The console shows the variables the process received.

Variables set explicitly on a run configuration always win over the
environment. Resolution order, lowest to highest: *Common Variables*, the
environment, *Module Overrides* for the run configuration's module, secrets.

Per environment you can choose a colour and enable **Ask for confirmation
before running**, useful for production.

### Settings

*Settings | Tools | Environment Switcher* holds behaviour, not data. Team
policies are stored in the shared project file; toolbar preferences are yours.

| Setting | Default |
|---|---|
| Run configuration types that receive variables | Application, JAR Application, Spring Boot |
| When a secret has no stored value: warn and run, or block the run | warn |
| Environments marked "ask for confirmation": once per IDE session, or every run | once per session |
| Expand `${NAME}` references in values (chained references work, unknown ones stay literal) | off |
| Secret storage: IDE password safe, or plain text in the project file | password safe |
| Automatically mark new `*PASSWORD*`, `*SECRET*`, `*TOKEN*`, `*KEY*` variables as secret | on |
| Toolbar: show a prefix before the name (text configurable, default "Env:"), show colour dot | on, on |

### Import a folder of `.env` files

*Run | Edit Environments… | Import Folder…* reads:

```
<name>.env                  one environment per file (dev.env, prod.env, ...)
common.env                  optional, shared by all environments
overrides/<module>.env      optional per-module overrides (services/ also accepted)
*.example                   optional secrets template: its keys become secret keys
secrets.<name>[.local].env  optional developer secrets, stored in the password safe only
local.env, current.*        ignored
```

Import replaces the environments, common variables and overrides in the
dialog; nothing is saved until you press OK or Apply.

### Sharing with a team

Commit `.idea/environmentSwitcher.xml`. If your repository ignores `.idea` as a whole,
switch to an explicit rule:

```gitignore
.idea/*
!.idea/environmentSwitcher.xml
```

Each developer enters secret values once per environment; the file only lists
which keys are secret.

## Development

```bash
./gradlew build              # compile, ktlint, tests
./gradlew runIde             # sandbox IDE (Community)
./gradlew runIdeUltimate     # sandbox IDE (Ultimate) for Spring Boot checks
./gradlew verifyPlugin       # binary compatibility against recommended IDEs
```

Requires a JDK 17 toolchain (Gradle downloads one) and network access to
download the IntelliJ Platform.

### Manual checklist before a release

- Dropdown visible in the new UI and in the classic UI.
- Select an env, run a plain Application: variables present in the process.
- Ultimate: same with a Spring Boot run configuration.
- Missing secret shows a balloon and the variable is absent.
- "Ask for confirmation" environment prompts once per IDE session.
- Settings: add/rename/delete env, secret masking, cancel discards, import folder.
- `.idea/environmentSwitcher.xml` contains secret key names only.

## License

MIT, see [LICENSE](LICENSE).
