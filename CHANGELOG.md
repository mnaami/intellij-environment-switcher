# Changelog

## [Unreleased]

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
