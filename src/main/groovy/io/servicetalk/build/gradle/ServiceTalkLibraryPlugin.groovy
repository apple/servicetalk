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
package io.servicetalk.build.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin

import static io.servicetalk.build.gradle.ProjectUtils.addManifestAttributes
import static io.servicetalk.build.gradle.ProjectUtils.appendNodes
import static io.servicetalk.build.gradle.ProjectUtils.copyResource
import static io.servicetalk.build.gradle.ProjectUtils.createJavadocJarTask
import static io.servicetalk.build.gradle.ProjectUtils.createSourcesJarTask
import static io.servicetalk.build.gradle.ProjectUtils.fixBomDependencies
import static io.servicetalk.build.gradle.ProjectUtils.writeToFile

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
        toolVersion = "8.10"
        configDir = file("$buildDir/checkstyle")
      }

      project.task("checkstyleConfig") {
        mustRunAfter clean

        doLast {
          copyResource("checkstyle/checkstyle.xml", checkstyle.configDir, "checkstyle.xml")
          copyResource("checkstyle/global-suppressions.xml", checkstyle.configDir, "global-suppressions.xml")

          File checkstyleLocalSuppressionsFile = file("$rootDir/gradle/checkstyle/suppressions.xml")
          if (checkstyleLocalSuppressionsFile.exists()) {
            writeToFile(checkstyleLocalSuppressionsFile.text, checkstyle.configDir, "local-suppressions.xml")
          }
        }
      }

      project.task("checkstyle") {
        dependsOn checkstyleMain
        dependsOn checkstyleTest
      }

      tasks.checkstyleMain.dependsOn checkstyleConfig
      tasks.checkstyleTest.dependsOn checkstyleConfig
      tasks.matching { it.name == "checkstyleTestFixtures" }.all {
        it.dependsOn checkstyleConfig
        tasks.checkstyle.dependsOn it
      }

      pmd {
        toolVersion = "6.3.0"
        sourceSets = [sourceSets.main, sourceSets.test]
        ruleSets = []
        ruleSetConfig = resources.text.fromString(getClass().getResourceAsStream("pmd/basic.xml").text)
      }

      project.task("pmd") {
        dependsOn pmdMain
        dependsOn pmdTest
      }

      tasks.matching { it.name == "pmdTestFixtures" }.all {
        tasks.pmd.dependsOn it
      }

      // Exclusions are configured at each project level
      File spotbugsMainExclusionsFile = file("$rootDir/gradle/spotbugs/main-exclusions.xml")
      File spotbugsTestExclusionsFile = file("$rootDir/gradle/spotbugs/test-exclusions.xml")
      File spotbugsTestFixturesExclusionsFile = file("$rootDir/gradle/spotbugs/testFixtures-exclusions.xml")

      // This task defaults to XML reporting for CI, but humans like HTML
      if (!System.getProperty("CI")) {
        tasks.withType(com.github.spotbugs.SpotBugsTask) {
          reports {
            xml.enabled = false
            html.enabled = true
          }
        }
      }

      spotbugs {
        toolVersion = "3.1.1"
        sourceSets = [sourceSets.main]

        // Apply the test exclusions to test fixtures, by making them the default.
        if (spotbugsTestFixturesExclusionsFile.exists()) {
          excludeFilter = spotbugsTestFixturesExclusionsFile
        }
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
        dependsOn spotbugsMain
        dependsOn spotbugsTest
      }

      tasks.matching { it.name == "spotbugsTestFixtures" }.all {
        tasks.spotbugs.dependsOn it
      }

      project.task("quality") {
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

      if (System.getenv("BINTRAY_USER") && System.getenv("BINTRAY_KEY")) {
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
