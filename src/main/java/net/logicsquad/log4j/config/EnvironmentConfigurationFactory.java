package net.logicsquad.log4j.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;

/**
 * <p>
 * Custom Log4j 2 {@link ConfigurationFactory} that sources configuration from:
 * </p>
 * 
 * <ul>
 * <li>system properties; and</li>
 * <li>environment variables.</li>
 * </ul>
 * 
 * <h3>System properties</h3>
 * <p>
 * At configuration time, this class searches system properties for keys beginning with
 * {@code app.logging.}. These keys then have the {@code app.logging.} prefix <em>removed</em>, and
 * are used with their values to configure Log4j 2. For example: {@code app.logging.status=ERROR} is
 * converted to {@code status=ERROR}, and this property is used in configuration.
 * </p>
 * 
 * <p>
 * This class will also parse a custom "quick syntax" for declaring {@code Logger}s. Log4j 2
 * configuration takes <em>two</em> properties to configure each {@code Logger}, compared to Log4j
 * 1's single property. This class will parse a property of the form:
 * </p>
 * 
 * <pre>
 * app.logging.quick.&lt;Logger name&gt;=&lt;level&gt;
 * </pre>
 * 
 * <p>
 * For example:
 * </p>
 * 
 * <pre>
 * app.logging.quick.net.logicsquad.foo.bar.SomeClass = WARN
 * </pre>
 * 
 * <p>
 * is equivalent to:
 * </p>
 * 
 * <pre>
 * app.logging.logger.log1.name=net.logicsquad.foo.bar.SomeClass
 * app.logging.logger.log1.level=WARN
 * </pre>
 * 
 * <p>
 * <em>Additionally</em>, the {@code loggers} property will be correctly amended to include this new
 * {@code Logger}, which will be given a unique symbolic name. Note that this syntax is available
 * <em>only</em> via system properties, and won't be parsed via environment variables.
 * </p>
 * 
 * <h3>Environment variables</h3>
 * <p>
 * At configuration time, this class searches environment variables for keys beginning with
 * {@code APP_LOGGING_}. These keys then have the {@code APP_LOGGING_} prefix <em>removed</em>, and
 * the remaining string lowercased (except where a Log4j 2 keyword is case-sensitive, e.g.
 * {@code customLevel}&mdash;these are handled as special cases), and are used with their values to
 * configure Log4j 2. For example: {@code APP_LOGGING_ROOTLOGGER_LEVEL=DEBUG} is converted to
 * {@code rootLogger.level=DEBUG}, and this property is used in configuration.
 * </p>
 * 
 * <h3>Usage</h3>
 * <p>
 * This class can be used in one of the myriad ways available to configure Log4j 2. The easiest
 * approach is to add {@code src/main/resources/log4j2.component.properties} containing the
 * following property:
 * 
 * <pre>
 * log4j.configurationFactory = net.logicsquad.log4j.config.EnvironmentConfigurationFactory
 * </pre>
 * 
 * <p>
 * If no suitable properties are found via system properties or environment variables, some sensible
 * defaults are used to log to the console.
 * </p>
 * 
 * <h3>Extension</h3>
 * <p>
 * The protected method {@link #stringForKey(Properties, String)} can be overridden by a subclass to
 * provide alternate resolution of property <em>values</em>. This supports fairly esoteric use
 * cases, such as where system properties contain <em>encrypted</em> values, but some other service
 * will return plaintext values for those keys.
 * </p>
 * 
 * @author paulh
 * @author Romain Manni-Bucau
 * @see <a href= "https://rmannibucau.metawerx.net/post/log4j2-environment-configuration">Log4j2:
 *      how to configure your logging with environment variables</a>
 * @since 1.0
 */
public class EnvironmentConfigurationFactory extends ConfigurationFactory {
	// What we're doing here is basically pretending that we support some fictional file type called
	// ".env". If we don't do this, and if src/main/resources/log4j2.env is not present, then this class
	// won't configure Log4j.
	/**
	 * Filename suffixes for supported types
	 */
	private static final String[] SUFFIXES = new String[] { ".env", "*" };

	/**
	 * Property key prefix
	 */
	private static final String PROPERTY_PREFIX = "app.logging.";

	/**
	 * Key for list of symbolic {@code Logger} names
	 */
	private static final String LOGGERS_KEY = "loggers";

	/**
	 * Prefix for properties using "quick syntax"
	 */
	private static final String QUICK_PREFIX = "quick.";

	/**
	 * Default properties
	 */
	private static final Map<String, String> DEFAULT_PROPERTIES = new HashMap<>();

	static {
		DEFAULT_PROPERTIES.put("status", "ERROR");
		DEFAULT_PROPERTIES.put("appender.stdout.type", "Console");
		DEFAULT_PROPERTIES.put("appender.stdout.name", "stdout");
		DEFAULT_PROPERTIES.put("appender.stdout.follow", "true");
		DEFAULT_PROPERTIES.put("appender.stdout.layout.type", "PatternLayout");
		DEFAULT_PROPERTIES.put("appender.stdout.layout.pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS}{UTC} %-5p %c - %m%n");
		DEFAULT_PROPERTIES.put("rootLogger.level", "INFO");
		DEFAULT_PROPERTIES.put("rootLogger.appenderRef.stdout.ref", "stdout");
	}

	/**
	 * Case-sensitive keywords
	 */
	private static final Map<String, String> CASE_SENSITIVE_KEYWORDS = new HashMap<>();

	static {
		CASE_SENSITIVE_KEYWORDS.put("customlevel", "customLevel");
		CASE_SENSITIVE_KEYWORDS.put("rootlogger", "rootLogger");
		CASE_SENSITIVE_KEYWORDS.put("appenderref", "appenderRef");
	}

	@Override
	protected String[] getSupportedTypes() {
		return SUFFIXES;
	}

	@Override
	public Configuration getConfiguration(final LoggerContext loggerContext, final ConfigurationSource source) {
		try {
			// Fetch and cook the system properties
			Map<String, String> systemMap = cookedMapForProperties(System.getProperties());
			// Handle any "quick syntax" Loggers
			List<String> loggers = loggerShortNames(systemMap);
			int index = 1;
			for (String className : loggerLongNames(systemMap)) {
				String level = quickLevelForLoggerLongName(className, systemMap);
				String prefix = "logger.quick" + index;
				// Remove property
				systemMap.remove(QUICK_PREFIX + className);
				// Add name
				systemMap.put(prefix + ".name", className);
				// Add level
				systemMap.put(prefix + ".level", level);
				loggers.add("quick" + index);
				index++;
			}
			systemMap.put("loggers", loggers.stream().collect(Collectors.joining(",")));

			// Concatenate the cooked system properties and cooked envars
			final Properties properties = new Properties();
			properties.putAll(cookedMapForMap(System.getenv()));
			properties.putAll(systemMap);

			if (properties.size() == 0) {
				// Set defaults if we found nothing
				setDefaults(properties);
			} else {
				// In the usual case, remove any properties whose values are blank
				Iterator<Entry<Object, Object>> entryIterator = properties.entrySet().iterator();
				while (entryIterator.hasNext()) {
					Entry<Object, Object> entry = entryIterator.next();
					if (entry.getValue() == null || entry.getValue().toString().isEmpty()) {
						entryIterator.remove();
					}
				}
			}
			final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			try (final OutputStream os = byteArrayOutputStream) {
				properties.store(os, "");
			}
			return new PropertiesConfigurationFactory().getConfiguration(loggerContext,
					new ConfigurationSource(new ByteArrayInputStream(byteArrayOutputStream.toByteArray())));
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Returns a {@link Map} containing the subset of entries of {@code properties} where the key starts
	 * with {@link #PROPERTY_PREFIX}.
	 * 
	 * @param properties a {@link Properties}
	 * @return cooked {@link Map}
	 */
	Map<String, String> cookedMapForProperties(final Properties properties) {
		Objects.requireNonNull(properties);
		return properties.stringPropertyNames().stream().filter(e -> e.startsWith(PROPERTY_PREFIX))
				.collect(Collectors.toMap(e -> e.substring(PROPERTY_PREFIX.length()), e -> stringForKey(properties, e)));
	}

	/**
	 * Returns a {@link Map} containing the subset of entries of {@code map} where the "sanitized" key
	 * starts with {@link #PROPERTY_PREFIX}. A sanitized key is the result of
	 * {@link #sanitizeKeyForEntry(Entry)} on the entry&mdash;that is, the key converted from
	 * "environment variable format" to "properties format". Note that the keys in the map returned by
	 * this method will have been converted to "properties format".
	 * 
	 * @param properties a {@link Properties}
	 * @return cooked {@link Map}
	 */
	Map<String, String> cookedMapForMap(final Map<String, String> map) {
		Objects.requireNonNull(map);
		return map.entrySet().stream().filter(e -> sanitizeKeyForEntry(e).startsWith(PROPERTY_PREFIX))
				.collect(Collectors.toMap(e -> sanitizeKeyForEntry(e).substring(PROPERTY_PREFIX.length()), e -> e.getValue()));
	}

	/**
	 * "Sanitizes" a key in environment variable format. Specifically, '_' is replaced by '.', and words
	 * are lower-cased with the exception of case-sensitive keywords.
	 * 
	 * @param entry a {@link Entry}
	 * @return sanitized key from {@code entry}
	 */
	String sanitizeKeyForEntry(final Entry<String, String> entry) {
		Objects.requireNonNull(entry);
		String result = entry.getKey().toLowerCase(Locale.ENGLISH).replace('_', '.');
		for (Entry<String, String> e : CASE_SENSITIVE_KEYWORDS.entrySet()) {
			result = result.replace(e.getKey(), e.getValue());
		}
		return result;
	}

	/**
	 * Sets <em>hard-coded</em> default properties on {@code properties}.
	 * 
	 * @param properties a {@link Properties} object
	 */
	private void setDefaults(final Properties properties) {
		for (Entry<String, String> e : DEFAULT_PROPERTIES.entrySet()) {
			properties.putIfAbsent(e.getKey(), e.getValue());
		}
		return;
	}

	/**
	 * Returns a {@link Map} representing {@code properties}.
	 * 
	 * @param properties a {@link Properties}
	 * @return corresponding {@link Map}
	 */
	Map<String, String> mapForProperties(final Properties properties) {
		Objects.requireNonNull(properties);
		return properties.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
	}

	/**
	 * Returns a string value for {@code key}. This implementation simply returns
	 * {@link Properties#getProperty(String)} from {@code properties}, but subclasses can override this
	 * method to do something else. That is, there is no guarantee that the value string returned was
	 * obtained from {@code properties}, and subclass implementations are free to ignore that parameter
	 * completely.
	 * 
	 * @param properties a {@link Properties}
	 * @param key        a key
	 * @return string value for {@code key}
	 */
	protected String stringForKey(final Properties properties, final String key) {
		return properties.getProperty(key);
	}

	/**
	 * Returns a list of "short names" of {@code Logger}s from the {@code loggers} key. That is, the
	 * comma-separated list of symbolic names for loggers specified in full elsewhere.
	 * 
	 * @param cookedProperties a {@link Map} (already processed by
	 *                         {@link #cookedMapForProperties(Properties)})
	 * @return list of "short names"
	 */
	List<String> loggerShortNames(Map<String, String> cookedProperties) {
		Objects.requireNonNull(cookedProperties);
		List<String> result = new ArrayList<>();
		if (cookedProperties.containsKey(LOGGERS_KEY)) {
			List<String> list = Arrays.asList(cookedProperties.get(LOGGERS_KEY).split("\\s*,\\s*"));
			result.addAll(list);
		}
		return result;
	}

	/**
	 * Returns a list of "long names" of {@code Logger}s parsed from entries using the "quick syntax".
	 * 
	 * @param cookedProperties a {@link Map} (already processed by
	 *                         {@link #cookedMapForProperties(Properties)})
	 * @return list of "long names"
	 */
	List<String> loggerLongNames(Map<String, String> cookedProperties) {
		Objects.requireNonNull(cookedProperties);
		return cookedProperties.entrySet().stream().filter(e -> e.getKey().toString().startsWith(QUICK_PREFIX))
				.map(e -> e.getKey().toString().substring(QUICK_PREFIX.length())).collect(Collectors.toList());
	}

	/**
	 * Returns the log level corresponding to {@code longName} parsed from entries using the "quick
	 * syntax".
	 * 
	 * @param longName         long name of a {@code Logger}
	 * @param cookedProperties a {@link Map} (already processed by
	 *                         {@link #cookedMapForProperties(Properties)})
	 * @return corresponding level
	 */
	String quickLevelForLoggerLongName(String longName, Map<String, String> cookedProperties) {
		Objects.requireNonNull(longName);
		Objects.requireNonNull(cookedProperties);
		return cookedProperties.get(QUICK_PREFIX + longName);
	}
}
