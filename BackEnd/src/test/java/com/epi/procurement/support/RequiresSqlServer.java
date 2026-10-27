package com.epi.procurement.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the annotated test class (and its subclasses) only with {@code -Pdb-it}. {@link EnabledIfSystemProperty}
 * itself is not inherited, hence this composed, {@link Inherited} annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@EnabledIfSystemProperty(named = "db.it", matches = "true")
public @interface RequiresSqlServer {
}
