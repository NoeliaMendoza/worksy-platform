package com.plataformaempleo.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Verifica las reglas de la arquitectura hexagonal en todos los módulos de la API. */
@AnalyzeClasses(packages = "com.plataformaempleo.api", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule dominioSinFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..")
            .because("el dominio debe ser independiente de Spring y de JPA");

    @ArchTest
    static final ArchRule dominioNoConoceAdaptadores = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..application..")
            .because("las dependencias apuntan hacia el dominio");

    @ArchTest
    static final ArchRule aplicacionNoConoceAdaptadores = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .because("los casos de uso solo usan puertos");
}
