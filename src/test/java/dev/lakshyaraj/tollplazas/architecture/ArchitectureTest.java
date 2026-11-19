package dev.lakshyaraj.tollplazas.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// keeps the module boundaries honest as the code grows.
@AnalyzeClasses(packages = "dev.lakshyaraj.tollplazas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "dev.lakshyaraj.tollplazas";

    @ArchTest
    static final ArchRule geometryIsPureJava = noClasses().that().resideInAPackage(BASE + ".geo..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", BASE + ".tollplaza..",
                    BASE + ".routing..", BASE + ".api..");

    @ArchTest
    static final ArchRule matchingDoesNotKnowAboutSpringOrHttp = noClasses().that().resideInAPackage(BASE + ".matching..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", BASE + ".api..", BASE + ".routing..");

    @ArchTest
    static final ArchRule onlyTheApiLayerTalksHttp = noClasses().that().resideOutsideOfPackages(BASE + ".api..", BASE + ".config..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web.bind..", "org.springframework.web.servlet..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule nothingDependsOnTheApiLayer = noClasses().that().resideOutsideOfPackages(BASE + ".api..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".api..");

    @ArchTest
    static final ArchRule noCycles = slices().matching(BASE + ".(*)..").should().beFreeOfCycles();
}
