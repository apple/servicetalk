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

import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.artifact.FileBasedMavenArtifact

import static io.servicetalk.gradle.plugin.internal.ProjectUtils.addBuildContextExtensions
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.appendNodes
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.copyResource
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.enforceProjectVersionScheme
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.enforceUtf8FileSystem
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.locateBuildLevelConfigFile
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.writeToFile
import static io.servicetalk.gradle.plugin.internal.Versions.CHECKSTYLE_VERSION
import static io.servicetalk.gradle.plugin.internal.Versions.TARGET_VERSION

class ServiceTalkCorePlugin implements Plugin<Project> {
  void apply(Project project, boolean publishesArtifacts = true) {
    enforceUtf8FileSystem()
    addBuildContextExtensions project
    enforceProjectVersionScheme project
    applyCheckstylePlugin project
    applyIdeaPlugin project

    if (publishesArtifacts) {
      applyMavenPublishPlugin project // Sign & Publish to Maven Central
      //      applyBintrayPlugin project // Publish to Bintray, depends on applyMavenPublishPlugin
    }
  }

  private static void applyCheckstylePlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("checkstyle")

      checkstyle {
        toolVersion = CHECKSTYLE_VERSION
        configDir = file("$buildDir/checkstyle")
      }

      // Overwrite the default set of file for Checkstyle analysis from only java files to all files of the source set
      // See: https://docs.gradle.org/current/dsl/org.gradle.api.plugins.quality.Checkstyle.html#org.gradle.api.plugins.quality.Checkstyle:source
      if (project.findProperty("sourceSets")) {
        sourceSets.all {
          tasks.getByName(it.getTaskName("checkstyle", null)).setSource(it.getAllSource())
        }
      }

      project.task("checkstyleResources") {
        description = "Copy Checkstyle resources to its configuration directory"
        group = "verification"

        if (tasks.findByName("clean")) {
          mustRunAfter clean
        }

        doLast {
          copyResource("checkstyle/checkstyle.xml", checkstyle.configDir)
          copyResource("checkstyle/global-suppressions.xml", checkstyle.configDir)
          copyResource("checkstyle/copyright-slashstar-style.header", checkstyle.configDir)
          copyResource("checkstyle/copyright-xml-style.header", checkstyle.configDir)
          copyResource("checkstyle/copyright-script-style.header", checkstyle.configDir)
          copyResource("checkstyle/copyright-sh-script-style.header", checkstyle.configDir)

          File checkstyleLocalSuppressionsFile = locateBuildLevelConfigFile(project, "/gradle/checkstyle/suppressions.xml")
          if (checkstyleLocalSuppressionsFile.exists()) {
            writeToFile(checkstyleLocalSuppressionsFile.text, checkstyle.configDir, "local-suppressions.xml")
          }
        }
      }

      project.task("checkstyleRoot", type: Checkstyle) {
        description = "Run Checkstyle analysis for files in the root directory"
        // The classpath field must be non-null, but could be empty because it's not required for this task:
        classpath = project.files([])
        source = fileTree(".") {
          includes = ["docker/**", "gradle/**", "*.gradle", "*.properties", "scripts/**", "buildSrc/**", "docs/**"]
          excludes = ["**/gradle/wrapper/**", "**/build/**", "**/.gradle/**", "**/gradlew*", "**/.cache/**",
                      "**/.out/**", "**/node_modules/**", "**/*.png", "**/*.zip"]
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
    }
  }

  private static void applyIdeaPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("idea")

      if (project.parent == null) {
        idea.project.languageLevel = TARGET_VERSION.toString()
        idea.project.targetBytecodeVersion = TARGET_VERSION

        idea.project.ipr.withXml { XmlProvider provider ->
          appendNodes(provider, getClass().getResourceAsStream("idea/ipr-components.xml"))
        }
        idea.workspace.iws.withXml { XmlProvider provider ->
          appendNodes(provider, getClass().getResourceAsStream("idea/iws-components.xml"))
        }
        // idea plugin doesn't account for buildSrc directory, so manually add it.
        idea.module.iml.withXml { XmlProvider provider ->
          Node contentNode = provider.asNode().component.find { it.@name == "NewModuleRootManager" }.content[0]
          contentNode.appendNode("sourceFolder", [url: "file://\$MODULE_DIR\$/buildSrc/src/main/java"])
        }
      }
    }
  }

  private static void applyMavenPublishPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("maven-publish")
    }
  }

  private static void applyBintrayPlugin(Project project) {
    project.configure(project) {
      pluginManager.apply("com.jfrog.bintray")

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
            vcsUrl = "https://github.com/apple/servicetalk.git"
          }
          override = true
          publish = true
        }

        // Temporary workaround for https://github.com/bintray/gradle-bintray-plugin/issues/229
        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
        project.tasks.withType(BintrayUploadTask) {
          doFirst {
            publishing.publications.withType(MavenPublication).each { publication ->
              File moduleFile = project.buildDir.toPath()
                  .resolve("publications/${publication.name}/module.json").toFile()

              if (moduleFile.exists()) {
                publication.artifact(new FileBasedMavenArtifact(moduleFile) {
                  @Override
                  protected String getDefaultExtension() {
                    return "module"
                  }
                })
              }
            }
          }
        }
      }
    }
  }
}
