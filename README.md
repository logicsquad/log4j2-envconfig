Log4j 2 EnvironmentConfigurationFactory
=======================================

What is this?
-------------
This project provides a `ConfigurationFactory` subclass for
configuring [Log4j 2](https://logging.apache.org/log4j/2.x/) from
system properties and environment variables.

Getting started
---------------
You can use `EnvironmentConfigurationFactory` in your projects by
including it as a Maven dependency:

    <dependency>
      <groupId>net.logicsquad</groupId>
      <artifactId>log4j2-envconfig</artifactId>
      <version>1.0</version>
    </dependency>

Add `src/main/resources/log4j2.component.properties` containing the
following property:

    log4j.configurationFactory=net.logicsquad.log4j.config.EnvironmentConfigurationFactory

You can now use system properties or environment variables to
configure Log4j 2.

### System properties
`EnvironmentConfigurationFactory` searches system properties for keys
beginning with `app.logging.`. These keys then have the `app.logging.`
prefix _removed_, and are used with their values to configure Log4j 2.
For example: `app.logging.status=ERROR` is converted to
`status=ERROR`, and this property is used in configuration.

`EnvironmentConfigurationFactory` will also parse a custom "quick
syntax" for declaring `Logger`s. Log4j 2 configuration takes _two_
properties to configure each `Logger`, compared to Log4j 1's single
property. You can use properties of the form:

    app.logging.quick.<Logger name>=<level>

For example:

    app.logging.quick.net.logicsquad.foo.bar.SomeClass=WARN

is equivalent to:

    app.logging.logger.log1.name=net.logicsquad.foo.bar.SomeClass
    app.logging.logger.log1.level=WARN


Note that this syntax is available _only_ via system properties, and
won't be parsed via environment variables.

### Environment variables
`EnvironmentConfigurationFactory` searches environment variables for
keys beginning with `APP_LOGGING_`. These keys then have the
`APP_LOGGING_` prefix _removed_, and the remaining string lowercased
(except where a Log4j 2 keyword is case-sensitive, e.g.
`customLevel`—these are handled as special cases), and are used with
their values to configure Log4j 2. For example:
`APP_LOGGING_ROOTLOGGER_LEVEL=DEBUG` is converted to
`rootLogger.level=DEBUG`, and this property is used in configuration.

Contributing
------------
By all means, open issue tickets and pull requests if you have
something to contribute.

References
----------
`EnvironmentConfigurationFactory` was inspired by Romain Manni-Bucau's
article ["Log4j2: how to configure your logging with environment
variables"](https://rmannibucau.metawerx.net/post/log4j2-environment-configuration).
