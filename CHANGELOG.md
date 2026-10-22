# Changelog

This project adheres to [Semantic
Versioning](https://semver.org/spec/v2.0.0.html).

## Release 1.0 (2021-12-23)

Initial public release.


## Release 1.1 (2026-10-22)

### Changed
- Releases are now published through the Central Publisher Portal,
  since OSSRH has shut
  down. [#3](https://github.com/logicsquad/log4j2-envconfig/issues/3)

### Fixed
- The default configuration, `INFO` and above to the console, is now
  applied when no properties, or only blank ones, are found. Log4j's
  own `ERROR`-only configuration was used instead, so an application
  that logs before its own configuration is applied now logs at
  `INFO`. The new `defaultProperties()` lets a subclass change
  them. [#2](https://github.com/logicsquad/log4j2-envconfig/issues/2)
- The POM now declares the 2-clause BSD license, as in `LICENSE`,
  rather than the 3-clause
  license. [#4](https://github.com/logicsquad/log4j2-envconfig/issues/4)
- Quick-syntax loggers no longer cause loggers declared without a
  `loggers` list to be dropped. A list from environment variables is
  now respected, and a blank list is
  ignored. [#5](https://github.com/logicsquad/log4j2-envconfig/issues/5)
