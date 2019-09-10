/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.publish.maven.MavenPublication

import static io.servicetalk.gradle.plugin.internal.ProjectUtils.addManifestAttributes
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.addQualityTask
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.createJavadocJarTask
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.createSourcesJarTask
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.locateBuildLevelConfigFile
import static io.servicetalk.gradle.plugin.internal.Versions.PMD_VERSION
import static io.servicetalk.gradle.plugin.internal.Versions.SPOTBUGS_VERSION
import static io.servicetalk.gradle.plugin.internal.Versions.TARGET_VERSION

final class ServiceTalkLibraryPlugin extends ServiceTalkCorePlugin {
  void apply(Project project) {
    super.apply project

    applyJavaLibraryPlugin project
    configureTestFixtures project
    configureTests project
    enforceCheckstyleRoot project
    applyPmdPlugin project
    applySpotBugsPlugin project
    addQualityTask project
  }

  private static void applyJavaLibraryPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("java-library")

      sourceCompatibility = TARGET_VERSION
      targetCompatibility = TARGET_VERSION

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
          }
        }
      }
    }
  }

  private static void configureTestFixtures(Project project) {
    project.configure(project) {
      def fixturesDir = file("src/testFixtures/java")
      def fixturesFilesCount = layout.files(fixturesDir).getAsFileTree().size()

      if (fixturesFilesCount > 0) {
        pluginManager.apply("java-test-fixtures")

        idea {
          module {
            testSourceDirs += fixturesDir
          }
        }
      }
    }
  }

  private static void configureTests(Project project) {
    project.configure(project) {
      test {
        testLogging {
          events "passed", "skipped", "failed"
          showStandardStreams = true
        }

        jvmArgs "-server", "-Xms2g", "-Xmx4g", "-dsa", "-da", "-ea:io.servicetalk...",
            "-XX:+AggressiveOpts", "-XX:+TieredCompilation", "-XX:+UseBiasedLocking",
                "-XX:+OptimizeStringConcat", "-XX:+HeapDumpOnOutOfMemoryError"
      }
    }
  }

  private static void enforceCheckstyleRoot(Project project) {
    project.configure(project) {
      check.dependsOn checkstyleRoot
    }
  }

  private static void applyPmdPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("pmd")

      pmd {
        toolVersion = PMD_VERSION
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
    }
  }

  private static void applySpotBugsPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("com.github.spotbugs")

      spotbugs {
        toolVersion = SPOTBUGS_VERSION
      }

      // This task defaults to XML reporting for CI, but humans like HTML
      tasks.withType(SpotBugsTask) {
        reports {
          xml.enabled = project.ext.isCiBuild
          html.enabled = !project.ext.isCiBuild
        }
      }

      tasks.withType(SpotBugsTask).all {
        group = "verification"
      }

      sourceSets.all {
        def exclusionFile = locateBuildLevelConfigFile(project, "/gradle/spotbugs/" + it.name + "-exclusions.xml")
        if (exclusionFile.exists()) {
          tasks.getByName(it.getTaskName("spotbugs", null)) {
            excludeFilter = exclusionFile
          }
        }
      }

      project.task("spotbugs") {
        description = "Run SpotBugs analysis for all source sets"
        group = "verification"
        dependsOn tasks.withType(SpotBugsTask)
      }
    }
  }
}
