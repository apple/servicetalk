package io.servicetalk.gradle.plugin.internal

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.core.importer.Locations
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.GeneralCodingRules
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ServiceTalkArchUnitTask extends DefaultTask {

    @TaskAction
    void runArchUnitRules() {
        def buildDir = this.project.layout.buildDirectory.get().asFile
        def locations = new HashSet<Location>();
        locations.addAll(Locations.ofPackage("io.servicetalk"))
        locations.addAll(Locations.of(List.of(buildDir.toURL())))

        def classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importLocations(locations)
        if (classes.size() > 0) {
            def rules = setupRules()
            rules.each {
                getLogger().debug("ArchUnit rule: {}", it.getDescription())
                it.check(classes)
            }
        }
    }

    static List<ArchRule> setupRules() {
        return [
          GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING,
          GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME,
          // TODO : add custom ArchUnit rules here
        ]
    }
}