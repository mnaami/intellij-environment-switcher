# Environment Switcher

Postman-style environments for IntelliJ run configurations.

Define named environments (dev, test, staging, prod, ...) once, pick one from a
dropdown next to the run configuration selector, and every run configuration
you launch gets that environment's variables. Secrets live in the IDE password
safe (your OS keychain), never in project files.

- Works in IntelliJ IDEA 2024.2+, Community and Ultimate.
- Applies to Java-based run configurations: Application, JAR Application and
  Spring Boot by default; enable any other type in settings.
- Team-shareable: environments are stored in `.idea/envSwitcher.xml`; the
  selected environment and all secret values stay on your machine.

## Install

From a release zip: *Settings | Plugins | ⚙ | Install Plugin from Disk…*.

Build it yourself:

```bash
./gradlew buildPlugin        # build/distributions/env-switcher-<version>.zip
```

## Use

1. *Settings | Tools | Environment Switcher*: add environments and their variables.
   Tick **Secret** on a row to keep its value out of the project file.
2. Pick an environment in the toolbar dropdown (left of the run widget).
3. Run. The console shows the variables the process received.

Variables set explicitly on a run configuration always win over the
environment. Resolution order, lowest to highest: *Common Variables*, the
environment, *Module Overrides* for the run configuration's module, secrets.

Per environment you can choose a colour and enable **Ask for confirmation
before running**, useful for production.

### Import a folder of `.env` files

*Settings | Tools | Environment Switcher | Import Folder…* reads:

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

Commit `.idea/envSwitcher.xml`. If your repository ignores `.idea` as a whole,
switch to an explicit rule:

```gitignore
.idea/*
!.idea/envSwitcher.xml
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
- `.idea/envSwitcher.xml` contains secret key names only.

## License

MIT, see [LICENSE](LICENSE).
