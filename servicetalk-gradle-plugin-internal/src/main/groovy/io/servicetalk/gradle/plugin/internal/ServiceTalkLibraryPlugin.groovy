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

import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

import static io.servicetalk.gradle.plugin.internal.ProjectUtils.addManifestAttributes
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.addQualityTask
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.createJavadocJarTask
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.createSourcesJarTask
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.locateBuildLevelConfigFile
import static io.servicetalk.gradle.plugin.internal.Versions.PMD_VERSION
import static io.servicetalk.gradle.plugin.internal.Versions.SPOTBUGS_VERSION
import static io.servicetalk.gradle.plugin.internal.Versions.TARGET_VERSION
import static org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import static org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import static org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import static org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import static org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED

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

      java {
        sourceCompatibility = TARGET_VERSION
        targetCompatibility = TARGET_VERSION
      }

      def javaRelease = Integer.parseInt(TARGET_VERSION.getMajorVersion())

      if (JavaVersion.current().isJava9Compatible()) {
        compileJava {
          options.release = javaRelease
        }
        compileTestJava {
          options.release = javaRelease
        }

        // Not every project has compileTestFixturesJava task so we have to defer attempting configuration
        project.afterEvaluate {
          def compileTasks = project.tasks.withType(JavaCompile)
          def compileJavaTask = compileTasks?.findByName("compileJava")
          def compileJavaTestFixturesTask = compileTasks?.findByName("compileTestFixturesJava")
          if (null != compileJavaTask && null != compileJavaTestFixturesTask) {
            def useRelease = compileJavaTask?.options?.release?.getOrNull() ?: javaRelease
            // if another action has set a higher language version, never reduce it.
            if (compileJavaTestFixturesTask?.options?.release?.getOrNull() == null ||
                compileJavaTestFixturesTask.options.release.get() < useRelease) {
              compileJavaTestFixturesTask.options.release = useRelease
            }
          }
        }
      }

      jar {
        addManifestAttributes(project, manifest)
      }

      javadoc {
        options.noQualifiers "all"
        options.addBooleanOption("Xwerror", true)
        options.addBooleanOption("Xdoclint:all,-missing", true)
        options.addBooleanOption("protected", true)
      }

      def sourcesJar = createSourcesJarTask(project, sourceSets.main)
      def javadocJar = createJavadocJarTask(project, sourceSets.main)

      artifacts {
        archives sourcesJar
        archives javadocJar
      }

      // Keep publishing and signing configuration in sync with servicetalk-bom/build.gradle
      publishing {
        publications {
          mavenJava(MavenPublication) {
            // publish jars, sources and docs
            from components.java
            artifact(javadocJar)
            artifact(sourcesJar)
            pom {
              name = project.name
              description = 'A networking framework that evolves with your application'
              url = 'https://servicetalk.io'
              licenses {
                license {
                  name = 'The Apache License, Version 2.0'
                  url = 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                }
              }
              developers {
                developer {
                  id = 'servicetalk-project-authors'
                  name = 'ServiceTalk project authors'
                  email = 'servicetalk-oss@group.apple.com'
                }
              }
              scm {
                connection = "scm:git:git://${scmHost}/${scmPath}.git"
                developerConnection = "scm:git:ssh://${scmHost}:${scmPath}.git"
                url = "https://${scmHost}/${scmPath}"
              }
              issueManagement {
                system = 'ServiceTalk Issues'
                url = "${issueManagementUrl}"
              }
              ciManagement {
                system = 'ServiceTalk CI'
                url = "${ciManagementUrl}"
              }
              // Validate that all dependencies either have an explicit version or corresponding BOM:
              withXml {
                def pomNode = asNode()
                pomNode.dependencies?.dependency?.each { dep ->
                  def groupId = dep.groupId.text()
                  def artifactId = dep.artifactId.text()
                  def version = dep.version?.text()
                  if (version == null || version.trim().isEmpty()) {
                    // Some dependencies have shorter groupId for their corresponding BOM:
                    def altGroupId = groupId.count('.') > 1 ? groupId.substring(0, groupId.lastIndexOf('.')) : groupId
                    def bom = pomNode.dependencyManagement?.dependencies?.dependency?.find { mDep ->
                      def mGroupId = mDep.groupId.text()
                      (mGroupId == groupId || mGroupId == altGroupId) && mDep.type?.text() == 'pom'
                    }
                    if (!bom) {
                      throw new GradleException("POM generation failed: Missing version for dependency " +
                          "'${groupId}:${artifactId}'. Did you forget to add a version number or a platform BOM?")
                    }
                  }
                }
              }
            }
          }
        }

        if (!repositories) {
          repositories {
            maven {
              name = "sonatype"
              def releasesRepoUrl = "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
              def snapshotsRepoUrl = "https://central.sonatype.com/repository/maven-snapshots/"
              url = project.isReleaseBuild ? releasesRepoUrl : snapshotsRepoUrl
              credentials {
                username = System.getenv("SONATYPE_USER")
                password = System.getenv("SONATYPE_TOKEN")
              }
            }
          }
        }
      }

      if (!!findProperty("signingKey") && !!findProperty("signingPassword")) {
        pluginManager.apply("signing")
        signing {
          def signingKey = findProperty("signingKey")
          def signingPassword = findProperty("signingPassword")
          useInMemoryPgpKeys(signingKey, signingPassword)
          sign publishing.publications.mavenJava
        }
      }

      tasks.withType(AbstractPublishToMaven) {
        onlyIf {
          // Disable all tasks that try to publish something else, expect defined "mavenJava" publication.
          // That could be automatically configured "pluginMaven" publication for gradle plugins that are required
          // only for Gradle Plugin Portal and should not be published to Maven Central
          publication == publishing.publications.mavenJava
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
      tasks.withType(Test).all {
        useJUnitPlatform()
        // expected format for timeout: <number>[ns|μs|ms|s|m|h|d])
        def junit5DefaultTimeout = Boolean.valueOf(System.getenv("CI") ?: "false") ? "30s" : "10s"
        def junit5TimeoutParamName = "junit.jupiter.execution.timeout.default"
        def junit5Timeout = System.getProperty(junit5TimeoutParamName, "$junit5DefaultTimeout")
        systemProperty junit5TimeoutParamName, "$junit5Timeout"
        systemProperty "junit.jupiter.extensions.autodetection.enabled", "true"

        testLogging {
          events = [FAILED]
          showStandardStreams = false
          exceptionFormat = FULL

          warn {
            // Show more complete info when gradle run in --warn mode
            events = [STARTED, PASSED, SKIPPED, FAILED]
            showStandardStreams = true
          }
        }

        // if property is defined and true allow tests to continue running after first fail
        ignoreFailures = Boolean.getBoolean("servicetalk.test.ignoreFailures")

        jvmArgs "-server", "-Xms2g", "-Xmx4g", "-dsa", "-da", "-ea:io.servicetalk...",
                "-XX:+HeapDumpOnOutOfMemoryError"

        // Always require native libraries for running tests. This helps to make sure transport-level state machine
        // receives all expected network events from Netty.
        systemProperty "io.servicetalk.transport.netty.requireNativeLibs", "true"
      }

      dependencies {
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
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
        incrementalAnalysis = true
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
        // https://github.com/spotbugs/spotbugs/issues/2567
        enabled = JavaVersion.current() < JavaVersion.VERSION_21
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
