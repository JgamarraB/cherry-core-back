package com.cherrycore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hace fallar el build si alguien rompe las capas de la arquitectura hexagonal.
 *
 * <pre>
 * infrastructure → application → domain
 * </pre>
 */
@AnalyzeClasses(packages = "com.cherrycore", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    APPLICATION, INFRASTRUCTURE,
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..",
                    "org.hibernate..", "tools.jackson..", "com.fasterxml.jackson..")
            .because("el dominio no debe depender de frameworks ni de otras capas")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    INFRASTRUCTURE, "org.springframework.web..", "jakarta.persistence..", "jakarta.servlet..")
            .because("los casos de uso solo hablan con puertos, no con adaptadores")
            .allowEmptyShould(true);
}
