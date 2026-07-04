package com.fasttrans.account;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the clean-architecture dependency rule: infrastructure -> application -> domain.
 * Domain must not depend on frameworks. Proto-generated code (..grpc..) is excluded.
 */
class CleanArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                // Exclude proto-generated gRPC classes — not part of the layered design.
                .withImportOption(location -> !location.contains("/com/fasttrans/account/grpc/"))
                .importPackages("com.fasttrans.account");
    }

    @Test
    void layeredDependenciesAreRespected() {
        ArchRule rule = layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Infrastructure").definedBy("..infrastructure..")
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");
        rule.check(classes);
    }

    @Test
    void domainHasNoFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..",
                        "org.apache.kafka..",
                        "io.grpc..",
                        "net.devh..",
                        "com.fasttrans.account.grpc..",
                        "com.fasttrans.account.infrastructure..",
                        "com.fasttrans.account.application.."
                );
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
        rule.check(classes);
    }
}
