package com.epi.procurement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Company isolation is enforced centrally (ADR-007, S1-AC-14, S1DR-19): controllers never touch repositories, and
 * only the scoped-query component may use the unscoped purchase request repository.
 */
class ArchitectureTests {

	private static final JavaClasses PRODUCTION = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("com.epi.procurement");

	@Test
	void controllersDoNotUseRepositories() {
		noClasses().that()
			.areAnnotatedWith(RestController.class)
			.should()
			.dependOnClassesThat()
			.areAssignableTo(Repository.class)
			.because("every data access must go through a service applying the caller's scope")
			.check(PRODUCTION);
	}

	/**
	 * S1CR-08: plain SQL on request tables outside the request module would bypass the visibility rules. ArchUnit
	 * cannot see SQL strings, so this checks the sources: request tables may only be named inside the request module
	 * (and in migrations). Dashboards and exports must call the request module instead.
	 */
	@Test
	void requestTablesAreOnlyQueriedInsideTheRequestModule() throws IOException {
		Pattern requestTables = Pattern.compile("\\bpurchase_request(_line|_transition)?\\b");
		Path sources = Path.of("src/main/java/com/epi/procurement");
		Path requestModule = sources.resolve("request");
		try (Stream<Path> files = Files.walk(sources)) {
			List<Path> offenders = files.filter(file -> file.toString().endsWith(".java"))
				.filter(file -> !file.startsWith(requestModule))
				.filter(file -> {
					try {
						return requestTables.matcher(Files.readString(file)).find();
					}
					catch (IOException ex) {
						throw new UncheckedIOException(ex);
					}
				})
				.toList();
			assertThat(offenders).as("Classes outside the request module naming request tables").isEmpty();
		}
	}

	@Test
	void onlyTheScopedQueryComponentUsesThePurchaseRequestRepository() {
		noClasses().that()
			.doNotHaveFullyQualifiedName("com.epi.procurement.request.ScopedRequests")
			.and()
			.doNotHaveFullyQualifiedName("com.epi.procurement.request.PurchaseRequestRepository")
			.should()
			.dependOnClassesThat()
			.haveFullyQualifiedName("com.epi.procurement.request.PurchaseRequestRepository")
			.because("purchase requests are only read through the visibility rules (ScopedRequests)")
			.check(PRODUCTION);
	}

}
