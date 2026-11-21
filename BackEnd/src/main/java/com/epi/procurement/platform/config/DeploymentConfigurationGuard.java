package com.epi.procurement.platform.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Refuses to start with a configuration that is unsafe for the environment it runs in (audit S-15, S-16, D-2;
 * review R-4). Runs before any bean is created and reports every problem at once.
 * <p>
 * The environment is what {@code APP_ENVIRONMENT} declares, never what the public address suggests:
 * <ul>
 * <li>{@code development} (or nothing, with a development profile): a developer machine. The development
 * profiles ({@code dev}, {@code local}, {@code test}) turn off secure cookies and enable API documentation, the
 * testing endpoints and the adjustable clock, so they are accepted <b>only against a database on this machine</b>,
 * and only when the application listens on this machine alone ({@code server.address} loopback, the default of
 * those profiles), unless {@code APP_ENVIRONMENT=development} states the intent. A server started by mistake with
 * the developer command line is refused when its database is elsewhere, and reachable by nobody when it is
 * local.</li>
 * <li>{@code demo}: a demonstration server. Deployed rules apply; the {@code demo} profile is allowed.</li>
 * <li>{@code uat}, {@code staging}, {@code preprod}, {@code production}: deployed. No development profile, no
 * {@code demo} profile (it creates demonstration accounts, one of them a global administrator), whatever the
 * address.</li>
 * </ul>
 * Without a development profile {@code APP_ENVIRONMENT} is mandatory: a deployment always says what it is.
 * Deployed rules: secure cookies, no testing hooks, no API documentation, a stated e-mail mode, an HTTPS public
 * address. An unknown environment name or e-mail mode is refused in every case.
 */
@Configuration(proxyBeanMethods = false)
class DeploymentConfigurationGuard {

	private static final Set<String> MAIL_MODES = Set.of("smtp", "file", "failing");

	private static final String DEVELOPMENT = "development";

	private static final String DEMONSTRATION = "demo";

	private static final Set<String> DEPLOYED = Set.of("uat", "staging", "preprod", "production");

	private static final Pattern DATABASE_HOST = Pattern.compile("^jdbc:sqlserver://([^;:/\\\\]*)",
			Pattern.CASE_INSENSITIVE);

	private static final Set<String> THIS_MACHINE = Set.of("localhost", "127.0.0.1", ".", "(local)");

	private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

	/** URL properties that send the connection to another host than the one written after {@code //}. */
	private static final Pattern REDIRECTING_PROPERTY = Pattern.compile("(?i);\\s*(servername|server|failoverpartner)\\s*=");

	@Bean
	static BeanFactoryPostProcessor deploymentGuard(Environment environment) {
		return beanFactory -> {
			List<String> problems = problems(environment);
			if (!problems.isEmpty()) {
				throw new IllegalStateException(
						"Unsafe configuration, the application will not start: " + String.join(" | ", problems));
			}
		};
	}

	static List<String> problems(Environment environment) {
		List<String> problems = new ArrayList<>();
		boolean developmentProfile = environment.acceptsProfiles(Profiles.of("dev", "local", "test"));
		boolean demoProfile = environment.acceptsProfiles(Profiles.of("demo"));
		String declared = text(environment, "app.environment").toLowerCase(Locale.ROOT);
		String publicUrl = text(environment, "app.public-base-url").toLowerCase(Locale.ROOT);
		String mailMode = text(environment, "app.mail.mode");

		if (StringUtils.hasText(mailMode) && !MAIL_MODES.contains(mailMode)) {
			problems.add("app.mail.mode (MAIL_MODE) is '" + mailMode + "'; allowed values are smtp and file");
		}
		if (!declared.isEmpty() && !DEVELOPMENT.equals(declared) && !DEMONSTRATION.equals(declared)
				&& !DEPLOYED.contains(declared)) {
			problems.add("APP_ENVIRONMENT is '" + declared + "'; allowed values are development, demo, uat, staging, "
					+ "preprod and production");
			return problems;
		}

		if (declared.isEmpty() || DEVELOPMENT.equals(declared)) {
			if (!developmentProfile) {
				problems.add(declared.isEmpty()
						? "APP_ENVIRONMENT is not set: a deployment must say what it is (demo, uat, staging, preprod or "
								+ "production); a developer machine uses the dev profile"
						: "APP_ENVIRONMENT=development needs the dev, local or test profile");
				return problems;
			}
			// A developer machine. Without an explicit declaration, the database must be on this machine and the
			// application must listen on this machine only: a server with its own local database is otherwise
			// indistinguishable from a laptop (review RR-6).
			String url = text(environment, "spring.datasource.url");
			String poolUrl = text(environment, "spring.datasource.hikari.jdbc-url");
			String host = databaseHost(url);
			boolean redirected = REDIRECTING_PROPERTY.matcher(url).find() || !poolUrl.isEmpty();
			if (declared.isEmpty() && (!THIS_MACHINE.contains(host) || redirected)) {
				problems.add("a development profile (dev, local, test) is active with a database on '"
						+ (redirected ? "another host named in the connection properties" : host)
						+ "': development profiles disable secure cookies and enable testing endpoints, so they start "
						+ "only against a database on this machine. On a server, start without them and set "
						+ "APP_ENVIRONMENT; on a developer machine with a remote database, set APP_ENVIRONMENT=development");
			}
			String listen = text(environment, "server.address").toLowerCase(Locale.ROOT);
			if (declared.isEmpty() && !LOOPBACK.contains(listen)) {
				problems.add("a development profile (dev, local, test) is active and the application would listen on "
						+ (listen.isEmpty() ? "every network interface" : "'" + listen + "'")
						+ ": without APP_ENVIRONMENT=development a development instance listens on this machine only "
						+ "(server.address / SERVER_ADDRESS = 127.0.0.1)");
			}
			if (publicUrl.startsWith("https://")) {
				problems.add("a development profile (dev, local, test) is active with a public HTTPS address ("
						+ publicUrl + "); start without development profiles and set APP_ENVIRONMENT");
			}
			return problems;
		}

		// Declared demo, uat, staging, preprod or production: deployed rules, whatever the address.
		if (developmentProfile) {
			problems.add("a development profile (dev, local, test) is active with APP_ENVIRONMENT=" + declared
					+ "; development profiles are never accepted in a deployed environment");
		}
		if (demoProfile && !DEMONSTRATION.equals(declared)) {
			problems.add("the demo profile is active with APP_ENVIRONMENT=" + declared + ": it creates demonstration "
					+ "accounts, including a global administrator; use APP_ENVIRONMENT=demo for a demonstration server");
		}
		if (!environment.getProperty("app.security.cookie-secure", Boolean.class, true)
				|| !environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, true)) {
			problems.add("session cookies must be secure in a deployed environment (app.security.cookie-secure and "
					+ "server.servlet.session.cookie.secure)");
		}
		if (environment.getProperty("app.testing.endpoints", Boolean.class, false)
				|| environment.getProperty("app.testing.adjustable-clock", Boolean.class, false)) {
			problems.add("app.testing.* hooks must be off in a deployed environment");
		}
		if (environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
				|| environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)) {
			problems.add("API documentation (springdoc) must be off in a deployed environment");
		}
		if (!StringUtils.hasText(mailMode)) {
			problems.add("MAIL_MODE is not set: choose smtp to send e-mail, or file to write messages (which contain "
					+ "live links) to MAIL_DIRECTORY");
		}
		else if ("failing".equals(mailMode)) {
			problems.add("app.mail.mode=failing is a test hook and cannot be used in a deployed environment");
		}
		if (!publicUrl.startsWith("https://")) {
			problems.add("APP_PUBLIC_BASE_URL must be the public HTTPS address of the application (it is '"
					+ publicUrl + "')");
		}
		return problems;
	}

	/** The server name of a SQL Server JDBC URL, without instance or port; empty when it cannot be read. */
	static String databaseHost(String jdbcUrl) {
		Matcher matcher = DATABASE_HOST.matcher(jdbcUrl.trim());
		return matcher.find() ? matcher.group(1).trim().toLowerCase(Locale.ROOT) : "";
	}

	private static String text(Environment environment, String key) {
		try {
			String value = environment.getProperty(key);
			return value == null ? "" : value.trim();
		}
		catch (IllegalArgumentException unresolvedPlaceholder) {
			return "";
		}
	}

}
