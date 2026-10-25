package com.epi.procurement.platform.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Fails fast with an actionable message when no database URL is configured. Only the dev, local and test
 * profiles provide a default (local SQL Server with Windows authentication); every other environment must
 * set {@code DB_URL} explicitly (DR-14). Runs before any bean, including the data source, is created.
 */
@Configuration(proxyBeanMethods = false)
class DatabaseConfigurationGuard {

	@Bean
	static BeanFactoryPostProcessor databaseUrlGuard(Environment environment) {
		return beanFactory -> {
			String url;
			try {
				url = environment.getProperty("spring.datasource.url");
			}
			catch (IllegalArgumentException unresolvedPlaceholder) {
				url = null;
			}
			if (!StringUtils.hasText(url)) {
				throw new IllegalStateException("No database configured: set the DB_URL environment variable "
						+ "(e.g. jdbc:sqlserver://host:1433;databaseName=procurement;encrypt=true) "
						+ "or run with the 'dev' profile for the local default.");
			}
		};
	}

}
