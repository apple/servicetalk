/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.gradle.plugin.internal

import com.github.spotbugs.SpotBugsTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.Module
import org.gradle.plugins.ide.idea.model.ModuleDependency

import static ProjectUtils.addManifestAttributes
import static ProjectUtils.appendNodes
import static ProjectUtils.copyResource
import static ProjectUtils.createJavadocJarTask
import static ProjectUtils.createSourcesJarTask
import static ProjectUtils.fixBomDependencies
import static ProjectUtils.writeToFile

class ServiceTalkLibraryPlugin extends ServiceTalkCorePlugin {
  void apply(Project project) {
    super.apply(project)

    applyDocPlugins project

    if (project.subprojects) {
      applyIdeaPlugin project
      applyEclipsePlugin project

      project.subprojects {
        configureProject it
      }
    } else {
      configureProject project
    }
  }

  private static void configureProject(Project project) {
    applyJavaLibraryPlugin project
    applyQualityPlugins project
    applyIdeaPlugin project
    applyEclipsePlugin project

    // TODO apply japicmp plugin

    configureTestFixtures project
  }

  private static void applyDocPlugins(Project project) {
    project.configure(project) {
      pluginManager.apply("org.asciidoctor.convert")

      asciidoctor {
        sourceDir = file("docs")
        logDocuments = true
        attributes "source-highlighter": "coderay", "linkcss": true
        resources {
          from(sourceDir) {
            include '*.png'
          }
        }
      }

      // Combine subproject javadocs into one directory
      project.task("javadocAll", type: Javadoc) {
        destinationDir = file("$buildDir/javadoc")
        gradle.projectsEvaluated {
          source files(subprojects.javadoc.source)
          classpath = files(subprojects.javadoc.classpath)
        }
      }

      project.task("publishDocs", type: Exec, dependsOn: [asciidoctor, "javadocAll"]) {
        def script = getClass().getResourceAsStream("docs/publish-docs.sh").text
        commandLine "sh", "-c", script
      }
    }
  }

  private static void applyJavaLibraryPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("java-library")

      jar {
        addManifestAttributes(project, manifest)
      }

      javadoc {
        options.noQualifiers "all"
        // -quiet is a workaround for addStringOption(s) being broken: it's ignored as already added in the command by Gradle
        options.addStringOption("Xwerror", "-quiet")
      }

      def sourcesJar = createSourcesJarTask(project, sourceSets.main)
      def javadocJar = createJavadocJarTask(project, sourceSets.main)

      artifacts {
        archives sourcesJar
        archives javadocJar
      }

      publishing {
        publications {
          mavenJava(MavenPublication) {
            // publish jars, sources and docs
            from components.java
            artifact(javadocJar)
            artifact(sourcesJar)
            fixBomDependencies(pom)
          }
        }
      }
    }
  }

  public static void applyIdeaPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("idea")

      if (project.parent == null) {
        idea.project.languageLevel = "1.8"
        idea.project.targetBytecodeVersion = JavaVersion.VERSION_1_8

        idea.project.ipr.withXml { XmlProvider provider ->
          appendNodes(provider, getClass().getResourceAsStream("idea/ipr-components.xml"))
        }
        idea.workspace.iws.withXml { XmlProvider provider ->
          appendNodes(provider, getClass().getResourceAsStream("idea/iws-components.xml"))
        }
        idea.module.iml {
          whenMerged { Module module ->
            // BOM projects are broken dependencies in IDEA
            module.dependencies = module.dependencies
                .findAll { dep -> !(dep instanceof ModuleDependency && dep.name.contains("-bom")) }
          }
        }
      }
    }
  }

  public static void applyEclipsePlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("eclipse")

      // safer/easier to always regenerate
      tasks.eclipse.dependsOn tasks.cleanEclipse

      if (project.parent != null) {
        // TODO review this when shading is finalized
        // assumes all subprojects depend on (shaded) netty
        // tasks.eclipseClasspath.dependsOn ":service-talk-core:shadedNettySourcesJar"

        eclipse.classpath.file.withXml { XmlProvider provider ->
          def xmlClasspath = provider.asNode()
          for (entry in xmlClasspath.classpathentry) {
            if (entry.@kind == "lib" && entry.@path.contains("netty-all-shaded")) {
              entry.@sourcepath = entry.@path.replaceFirst(".jar", "-sources.jar")
            }
          }
        }
      }
    }
  }

  private static void applyQualityPlugins(Project project) {
    project.configure(project) {
      pluginManager.apply("checkstyle")
      pluginManager.apply("pmd")
      pluginManager.apply("com.github.spotbugs")

      checkstyle {
        toolVersion = "8.16"
        configDir = file("$buildDir/checkstyle")
      }

      // Overwrite the default set of file for Checkstyle analysis from only java files to all files of the source set
      // See: https://docs.gradle.org/current/dsl/org.gradle.api.plugins.quality.Checkstyle.html#org.gradle.api.plugins.quality.Checkstyle:source
      sourceSets.all {
        tasks.getByName(it.getTaskName("checkstyle", null)).setSource(it.getAllSource())
      }

      project.task("checkstyleResources") {
        description = "Copy Checkstyle resources to its configuration directory"
        group = "verification"

        mustRunAfter clean

        doLast {
          copyResource("checkstyle/checkstyle.xml", checkstyle.configDir)
          copyResource("checkstyle/global-suppressions.xml", checkstyle.configDir)

          File checkstyleLocalSuppressionsFile = file("$rootDir/gradle/checkstyle/suppressions.xml")
          if (checkstyleLocalSuppressionsFile.exists()) {
            writeToFile(checkstyleLocalSuppressionsFile.text, checkstyle.configDir, "local-suppressions.xml")
          }
        }
      }

      project.task("checkstyleRoot", type: Checkstyle) {
        description = "Run Checkstyle analysis for files in the root directory"
        classpath = sourceSets.main.output
        source = fileTree(".") {
          includes = ["*.gradle", "*.properties", "gradle/**"]
          excludes = ["gradle/wrapper/**"]
        }
      }

      tasks.withType(Checkstyle).all {
        group = "verification"
        it.dependsOn checkstyleResources
      }

      project.task("checkstyle") {
        description = "Run Checkstyle analysis for all source sets"
        group = "verification"
        dependsOn tasks.withType(Checkstyle)
      }

      pmd {
        toolVersion = "6.10.0"
        ruleSets = []
        ruleSetConfig = resources.text.fromString(getClass().getResourceAsStream("pmd/basic.xml").text)
      }

      tasks.withType(Pmd).all {
        group = "verification"
      }

      project.task("pmd") {
        description = "Run PMD analysis for all source sets"
        group = "verification"
        dependsOn tasks.withType(Pmd)
      }

      // Exclusions are configured at each project level
      File spotbugsMainExclusionsFile = file("$rootDir/gradle/spotbugs/main-exclusions.xml")
      File spotbugsTestExclusionsFile = file("$rootDir/gradle/spotbugs/test-exclusions.xml")
      File spotbugsTestFixturesExclusionsFile = file("$rootDir/gradle/spotbugs/testFixtures-exclusions.xml")

      // This task defaults to XML reporting for CI, but humans like HTML
      tasks.withType(SpotBugsTask) {
        reports {
          xml.enabled = "true" == System.getenv("CI")
          html.enabled = "true" != System.getenv("CI")
        }
      }

      spotbugs {
        toolVersion = "3.1.10"

        // Apply the test exclusions to test fixtures, by making them the default.
        if (spotbugsTestFixturesExclusionsFile.exists()) {
          excludeFilter = spotbugsTestFixturesExclusionsFile
        }
      }

      tasks.withType(SpotBugsTask).all {
        group = "verification"
      }

      spotbugsMain {
        // Override the exclusions for main code.
        if (spotbugsMainExclusionsFile.exists()) {
          excludeFilter = spotbugsMainExclusionsFile
        }
      }

      spotbugsTest {
        // Override the exclusions for test code.
        if (spotbugsTestExclusionsFile.exists()) {
          excludeFilter = spotbugsTestExclusionsFile
        }
      }

      project.task("spotbugs") {
        description = "Run SpotBugs analysis for all source sets"
        group = "verification"
        dependsOn tasks.withType(SpotBugsTask)
      }

      project.task("quality") {
        description = "Run all quality analysers for all source sets"
        group = "verification"
        dependsOn tasks.checkstyle
        dependsOn tasks.pmd
        dependsOn tasks.spotbugs
      }
    }
  }

  private static void configureTestFixtures(Project project) {
    project.configure(project) {
      File testFixturesFolder = file("$projectDir/src/testFixtures")
      if (!testFixturesFolder.exists()) {
        return
      }

      SourceSetContainer projectSourceSets = project.sourceSets
      SourceSet testFixturesSourceSet = projectSourceSets.create("testFixtures") {
        compileClasspath += projectSourceSets["main"].output
        runtimeClasspath += projectSourceSets["main"].output
      }

      project.task("testFixturesJar", type: Jar) {
        appendix = "testFixtures"
        addManifestAttributes(project, manifest)
        from testFixturesSourceSet.output
      }

      // for project dependencies
      project.artifacts.add("testFixturesRuntime", testFixturesJar)

      projectSourceSets.test.compileClasspath += testFixturesSourceSet.output
      projectSourceSets.test.runtimeClasspath += testFixturesSourceSet.output

      project.dependencies {
        testFixturesImplementation project.configurations["implementation"]
        testFixturesImplementation project.configurations["api"]
        testFixturesRuntime project.configurations["runtime"]
        testImplementation project.configurations["testFixturesImplementation"]
        testRuntimeOnly project.configurations["testFixturesRuntime"]
      }

      def sourcesJar = createSourcesJarTask(project, testFixturesSourceSet)
      def javadocJar = createJavadocJarTask(project, testFixturesSourceSet)

      publishing {
        publications {
          testFixtures(MavenPublication) {
            artifactId = "$testFixturesJar.baseName-$testFixturesJar.appendix"
            from new TestFixturesComponent(project)
            artifact(testFixturesJar)
            artifact(sourcesJar)
            artifact(javadocJar)
            fixBomDependencies(pom)
          }
        }
      }

      if (bintray.publications) {
        bintray {
          publications = ["mavenJava", "testFixtures"]
        }
      }

      project.plugins.withType(IdeaPlugin) {
        project.idea.module.testSourceDirs += testFixturesSourceSet.allSource.srcDirs
        project.idea.module.scopes["TEST"].plus += [project.configurations["testFixturesRuntime"]]
      }

      project.plugins.withType(EclipsePlugin) {
        project.eclipse.classpath.sourceSets += [testFixturesSourceSet]
        project.eclipse.classpath.plusConfigurations += [project.configurations["testFixturesRuntime"]]
      }
    }
  }
}
