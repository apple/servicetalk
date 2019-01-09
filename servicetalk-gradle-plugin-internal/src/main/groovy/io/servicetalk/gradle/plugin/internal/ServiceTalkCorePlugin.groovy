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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class ServiceTalkCorePlugin implements Plugin<Project> {
  void apply(Project project) {
    if (project.subprojects) {
      project.subprojects {
        configureProject it
      }
    } else {
      configureProject project
    }
  }

  private static void configureProject(Project project) {
    applyLicensePlugin project
    applyJavaPlugins project
    configureTests project
  }

  public static void applyLicensePlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("com.github.hierynomus.license")
      license {
        header = null
        headerURI = getClass().getResource("license/HEADER.txt").toURI()
        strictCheck = true
        mapping {
          java = 'SLASHSTAR_STYLE'
          gradle = 'SLASHSTAR_STYLE'
        }
        headerDefinitions {
          // Redefine XML style to align with Intellij IDEA format
          // doc: https://github.com/hierynomus/license-gradle-plugin#creating-custom-header-definitions
          xml_style {
            firstLine = '<!--'
            beforeEachLine = '  ~ '
            endLine = '  -->'
            skipLinePattern = '^<\\?xml.*>$'
            firstLineDetectionPattern = '(\\\\s|\\\\t)*<!--.*$'
            lastLineDetectionPattern = '.*-->(\\\\s|\\\\t)*$'
            allowBlankLines = true
            isMultiline = true
          }
        }
      }

      // Include some files from the root directory
      // doc: https://github.com/hierynomus/license-gradle-plugin#running-on-a-non-java-project
      def rootFileTree = fileTree("$rootDir") {
        includes = ["*.gradle", "*.properties", "scripts/**", "gradle/**"]
        excludes = ["gradle/wrapper/**"]
      }

      project.task("licenseRoot", type: com.hierynomus.gradle.license.tasks.LicenseCheck) {
        source = rootFileTree
      }
      tasks.license.dependsOn licenseRoot

      project.task("licenseFormatRoot", type: com.hierynomus.gradle.license.tasks.LicenseFormat) {
        source = rootFileTree
      }
      tasks.licenseFormat.dependsOn licenseFormatRoot
    }
  }

  private static void applyJavaPlugins(Project project) {
    project.configure(project) {
      pluginManager.apply("java")
      pluginManager.apply("maven-publish")
      pluginManager.apply("com.jfrog.bintray")

      sourceCompatibility = 1.8

      publishing {
        publications {
          mavenJava(MavenPublication) {
            // set compile -> runtime deps
            // see http://forums.gradle.org/gradle/topics/maven_publish_plugin_generated_pom_making_dependency_scope_runtime
            pom.withXml { provider ->
              provider.asNode().dependencies.dependency.findAll { pomDep ->
                project.configurations["compile"].dependencies.any { dep ->
                  dep.group == pomDep.groupId.text() &&
                      dep.name == pomDep.artifactId.text()
                }
              }.each {
                it.scope*.value = "compile"
              }
            }
          }
        }
      }

      def releaseBuild = project.hasProperty("releaseBuild")
      def endsWithSnapshot = project.version.toString().toUpperCase().endsWith("-SNAPSHOT")
      if (releaseBuild) {
        if (endsWithSnapshot) {
          throw new GradleException("Project version for a release build must not contain a '-SNAPSHOT' suffix")
        }
      } else {
        if (!endsWithSnapshot) {
          project.version += "-SNAPSHOT"
        }
      }

      // bintray publishing information
      def bintrayUser = System.getenv("BINTRAY_USER")
      def bintrayKey = System.getenv("BINTRAY_KEY")
      if (bintrayUser && bintrayKey) {
        bintray {
          user = bintrayUser
          key = bintrayKey
          publications = ["mavenJava"]
          pkg {
            userOrg = "servicetalk"
            repo = "servicetalk"
            name = project.name
            licenses = ["Apache-2.0"]
            vcsUrl = "https://github.com/servicetalk/servicetalk.git"
          }
          override = true
          publish = true
        }
      }

      if (!repositories) {
        repositories {
          jcenter()
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

        jvmArgs '-server', '-Xms2g', '-Xmx4g', '-dsa', '-da', '-ea:io.servicetalk...',
                '-XX:+AggressiveOpts', '-XX:+TieredCompilation', '-XX:+UseBiasedLocking', '-XX:+UseFastAccessorMethods',
                '-XX:+OptimizeStringConcat', '-XX:+HeapDumpOnOutOfMemoryError', '-XX:+PrintGCDetails'
      }
    }
  }
}
