<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IRen Changelog
## [0.1.4]
### Fixed
- Bug with suggestion of shadowed names
- Inspection bug in git diff editor
- Remove IRen rename history from misc.xml
### Added
- New version of n-gram model

## [0.1.3]
### Fixed
- Some bugs
- Compatibility with nightly builds
### Added
- Heuristic: inspection doesn't suggest names for ignored exceptions in a catch block

## [0.1.2]
### Added
- History of variables' renaming. The plugin remembers variables that you've already renamed 
and the inspection doesn't suggest you to rename them anymore. This information is saved between IDE sessions.
- Heuristics for the inspection. It won't suggest you to rename parameters of the overridden methods anymore.

## [0.1.1]
### Fixed
- Proper detection of idea projects
- Ideas compatibility bug

## [0.1.0]
### Fixed
- Minor bugs
- Text in notifications and progress indicators

## [0.0.8]
### Added
- Loading models for `IntelliJ` repository from the server

## [0.0.7]
### Changed
- IRen settings UI
### Fixed
- Reduce models' cache size
- IRen suggestions in Typo quick fix

## [0.0.6]
### Added
- Persistent counter that works from the disk

## [0.0.5]
### Added
- Notification about trained models
### Fixed
- Inspection triggers less
- Filter stop names (it, self) from predictions

## [0.0.4]
### Added
- Inspection history
### Fixed
- Variable names were suggested looking on the declaration only
- Inspection cache

## [0.0.3]
### Added
- Python support
- Kotlin support

## [0.0.2]
### Added
- Java support
- Replace default refactoring with the IRen refactoring
- Inspection
- Automatic training of models

## [0.0.1]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
