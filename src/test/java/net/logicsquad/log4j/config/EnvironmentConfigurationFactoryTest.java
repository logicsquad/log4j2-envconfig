package net.logicsquad.log4j.config;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests on {@link EnvironmentConfigurationFactory}.
 *
 * @author paulh
 * @since 1.0
 */
public class EnvironmentConfigurationFactoryTest {
	private static final String TEST_PROPERTIES_1 = "test1.properties";
	private static final int TEST_PROPERTIES_COUNT_1 = 2;
	private static final String TEST_PROPERTIES_2 = "test2.properties";
	private static final String TEST_PROPERTIES_3 = "test3.properties";
	private static final String TEST_PROPERTIES_4 = "test4.properties";
	private static final String PROPERTY_PREFIX = "app.logging.";
	private static final String NOISY_LOGGER = "net.logicsquad.foo.bar.Noisy";

	private EnvironmentConfigurationFactory factory;

	private Map<String, String> envarKeyTests;

	private Properties properties1;

	// properties
	private Properties properties2;

	// envars
	private Properties properties3;
	private Map<String, String> envarMap;

	// quick
	private Map<String, String> cookedProperties4;

	@Before
	public void setup() throws IOException {
		factory = new EnvironmentConfigurationFactory();

		// For each of these, the key should be sanitised to the value
		envarKeyTests = new HashMap<>();
		envarKeyTests.put("APP_LOGGING_FOO", "app.logging.foo");
		envarKeyTests.put("APP_LOGGING_CUSTOMLEVEL", "app.logging.customLevel");
		envarKeyTests.put("APP_LOGGING_ROOTLOGGER", "app.logging.rootLogger");
		envarKeyTests.put("APP_LOGGING_APPENDERREF", "app.logging.appenderRef");

		properties1 = new Properties();
		properties1.load(getClass().getClassLoader().getResourceAsStream(TEST_PROPERTIES_1));
		properties2 = new Properties();
		properties2.load(getClass().getClassLoader().getResourceAsStream(TEST_PROPERTIES_2));
		properties3 = new Properties();
		properties3.load(getClass().getClassLoader().getResourceAsStream(TEST_PROPERTIES_3));
		envarMap = factory.mapForProperties(properties3);

		Properties properties4 = new Properties();
		properties4.load(getClass().getClassLoader().getResourceAsStream(TEST_PROPERTIES_4));
		cookedProperties4 = factory.cookedMapForProperties(properties4);
		return;
	}

	@After
	public void teardown() {
		// Clear any properties set by a test
		for (String key : System.getProperties().stringPropertyNames()) {
			if (key.startsWith(PROPERTY_PREFIX)) {
				System.clearProperty(key);
			}
		}
		return;
	}

	@Test
	public void canReadProperties() {
		assertTrue(properties1.stringPropertyNames().size() > 0);
		return;
	}

	@Test(expected = NullPointerException.class)
	public void sanitizeKeyForEntryThrowsOnNull() {
		factory.sanitizeKeyForEntry(null);
		return;
	}

	@Test
	public void sanitizeKeyForEntrySanitizesKeys() {
		for (Entry<String, String> e : envarKeyTests.entrySet()) {
			assertEquals(e.getValue(), factory.sanitizeKeyForEntry(e));
		}
		return;
	}

	@Test(expected = NullPointerException.class)
	public void mapForPropertiesThrowsOnNull() {
		factory.mapForProperties(null);
		return;
	}

	@Test
	public void mapForPropertiesReturnsExpectedMap() {
		Map<String, String> result = factory.mapForProperties(properties1);
		assertEquals(TEST_PROPERTIES_COUNT_1, result.size());
		return;
	}

	@Test(expected = NullPointerException.class)
	public void cookedMapForPropertiesThrowsOnNull() {
		factory.cookedMapForProperties(null);
		return;
	}

	@Test(expected = NullPointerException.class)
	public void cookedMapForMapThrowsOnNull() {
		factory.cookedMapForMap(null);
		return;
	}

	@Test
	public void cookedMapMethodsProduceSameResult() {
		assertEquals(factory.cookedMapForMap(envarMap), factory.cookedMapForProperties(properties2));
		return;
	}

	@Test(expected = NullPointerException.class)
	public void loggerShortNamesThrowsOnNull() {
		factory.loggerShortNames(null);
		return;
	}

	@Test
	public void loggerShortNamesReturnsExpectedNames() {
		List<String> expected = new ArrayList<>();
		expected.add("er");
		expected.add("erdb");
		assertEquals(expected, factory.loggerShortNames(cookedProperties4));
		return;
	}

	@Test(expected = NullPointerException.class)
	public void loggerLongNamesThrowsOnNull() {
		factory.loggerLongNames(null);
		return;
	}

	@Test
	public void loggerLongNamesReturnsExpectedNames() {
		List<String> result = factory.loggerLongNames(cookedProperties4);
		assertEquals(2, result.size());
		assertTrue(result.contains("net.logicsquad.foo.bar.Application"));
		assertTrue(result.contains("net.logicsquad.foo.bar.Session"));
		return;
	}

	@Test(expected = NullPointerException.class)
	public void quickLevelForLoggerLongNameThrowsOnNullString() {
		factory.quickLevelForLoggerLongName(null, cookedProperties4);
		return;
	}

	@Test(expected = NullPointerException.class)
	public void quickLevelForLoggerLongNameThrowsOnNullProperties() {
		factory.quickLevelForLoggerLongName("foo", null);
		return;
	}

	@Test(expected = NullPointerException.class)
	public void quickLevelForLoggerLongNameThrowsOnNullBoth() {
		factory.quickLevelForLoggerLongName(null, null);
		return;
	}

	@Test
	public void quickLevelForLoggerLongNameReturnsExpectedLevel() {
		assertEquals("WARN", factory.quickLevelForLoggerLongName("net.logicsquad.foo.bar.Application", cookedProperties4));
		assertEquals("DEBUG", factory.quickLevelForLoggerLongName("net.logicsquad.foo.bar.Session", cookedProperties4));
		return;
	}

	@Test
	public void getConfigurationAppliesDefaultsWhenNoPropertiesFound() {
		assertDefaultsApplied(configurationFor(factory), factory.defaultProperties());
		return;
	}

	@Test
	public void getConfigurationAppliesDefaultsWhenAllValuesBlank() {
		System.setProperty(PROPERTY_PREFIX + "rootLogger.level", "");
		assertDefaultsApplied(configurationFor(factory), factory.defaultProperties());
		return;
	}

	@Test
	public void getConfigurationIgnoresDefaultsWhenPropertiesFound() {
		System.setProperty(PROPERTY_PREFIX + "rootLogger.level", "WARN");
		Configuration configuration = configurationFor(factory);
		assertEquals(Level.WARN, configuration.getRootLogger().getLevel());
		assertNull(configuration.getAppender(factory.defaultProperties().get("appender.stdout.name")));
		return;
	}

	@Test
	public void defaultPropertiesReturnsMutableCopy() {
		Map<String, String> defaults = factory.defaultProperties();
		defaults.put("foo", "bar");
		assertFalse(factory.defaultProperties().containsKey("foo"));
		return;
	}

	@Test
	public void getConfigurationAppliesDefaultsFromSubclass() {
		EnvironmentConfigurationFactory subclass = new EnvironmentConfigurationFactory() {
			@Override
			protected Map<String, String> defaultProperties() {
				Map<String, String> result = super.defaultProperties();
				result.put("logger.noisy.name", NOISY_LOGGER);
				result.put("logger.noisy.level", "WARN");
				return result;
			}
		};
		Configuration configuration = configurationFor(subclass);
		assertDefaultsApplied(configuration, factory.defaultProperties());
		assertEquals(Level.WARN, configuration.getLoggers().get(NOISY_LOGGER).getLevel());
		return;
	}

	/**
	 * Returns an initialized {@link Configuration} from {@code factory}.
	 *
	 * @param factory an {@link EnvironmentConfigurationFactory}
	 * @return initialized {@link Configuration}
	 */
	private static Configuration configurationFor(EnvironmentConfigurationFactory factory) {
		Configuration configuration = factory.getConfiguration(new LoggerContext("test"), ConfigurationSource.NULL_SOURCE);
		configuration.initialize();
		return configuration;
	}

	/**
	 * Asserts that {@code configuration} has the root level, appender and pattern given in {@code defaults}.
	 *
	 * @param configuration a {@link Configuration}
	 * @param defaults      default properties
	 */
	private static void assertDefaultsApplied(Configuration configuration, Map<String, String> defaults) {
		assertEquals(Level.toLevel(defaults.get("rootLogger.level")), configuration.getRootLogger().getLevel());
		Appender appender = configuration.getAppender(defaults.get("appender.stdout.name"));
		assertNotNull(appender);
		assertEquals(defaults.get("appender.stdout.layout.pattern"), ((PatternLayout) appender.getLayout()).getConversionPattern());
		return;
	}
}
