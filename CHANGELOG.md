# Changelog

## [Unreleased]

## [1.2.2] - 2026-11-13
### Fixed
- Replaced the deprecated `DialogWrapper.IdeModalityType.PROJECT` in the
  Edit Environments dialog, reported by the Plugin Verifier against
  IntelliJ IDEA 2025.2 to 2026.3. The platform logged an error each time
  the dialog opened with it.
### Changed
- The Edit Environments dialog uses the IDE's standard modality again, so
  it blocks other project windows while open, like other IDE dialogs.

## [1.2.1] - 2026-10-07
### Fixed
- Unticking one variable no longer greys out every row drawn after it in
  the Edit Environments tables.

## [1.2.0] - 2026-10-07
### Added
- Enabled checkbox on every variable (environments, common, overrides):
  unchecked variables stay in the list but are not injected.
### Changed
- The Edit Environments dialog is modal to its own project window only,
  like Settings, so other open projects stay usable.

## [1.1.1] - 2026-09-20
### Fixed
- Replaced the deprecated single-argument `DynamicBundle` constructor,
  reported by the Plugin Verifier against the 2026.3 EAP.

## [1.1.0] - 2026-09-19
### Fixed
- Replaced deprecated and scheduled-for-removal platform APIs reported by
  the JetBrains Plugin Verifier (list renderers, password-safe attributes).

## [1.0.0] - 2026-09-10
### Added
- Toolbar dropdown next to the run widget to select the active environment
  (new and classic UI), with a configurable "Env:" prefix and colour dot.
- Run | Edit Environments… dialog: environments with colour and
  confirm-before-run flag, common variables, per-module overrides, copy
  environment, masked secret rows stored in the IDE password safe.
- Injection of the selected environment into Java-based run configurations
  (Application, JAR Application, Spring Boot by default).
- Settings | Tools | Environment Switcher: run configuration types,
  missing-secret policy (warn or block), confirmation scope, `${NAME}`
  expansion, secret storage (password safe or project file), auto-mark of
  sensitive names, toolbar preferences.
- Import of a folder of `.env` files, including developer secret files
  straight into the password safe.
