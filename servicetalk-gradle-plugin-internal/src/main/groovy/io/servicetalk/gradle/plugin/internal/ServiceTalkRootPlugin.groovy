/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc

import static io.servicetalk.gradle.plugin.internal.ProjectUtils.addQualityTask
import static io.servicetalk.gradle.plugin.internal.ProjectUtils.addArchUnitTask

final class ServiceTalkRootPlugin extends ServiceTalkCorePlugin {
  void apply(Project project) {
    super.apply(project, false)

    enforceCheckstyleRoot project
    addJavadocAllTask project
    addArchUnitTask project
    addQualityTask project
  }

  private static void enforceCheckstyleRoot(Project project) {
    project.configure(project) {
      pluginManager.apply("base")
      check.dependsOn checkstyleRoot
    }
  }

  private static void addJavadocAllTask(Project project) {
    project.configure(project) {
      project.task("javadocAll", type: Javadoc) {
        description = "Consolidate sub-project's Javadoc into a single location"
        group = "documentation"
        destinationDir = file("$buildDir/javadoc")
        // Disable module directories to avoid "undefined" sub-folder bug in JDK11. See:
        // - https://stackoverflow.com/questions/53732632/gradle-javadoc-search-redirects-to-undefined-url
        // - https://bugs.openjdk.java.net/browse/JDK-8215291
        if (JavaVersion.current().isJava11()) {
          options.addBooleanOption('-no-module-directories', true)
        }

        gradle.projectsEvaluated {
          subprojects.findAll {!it.name.contains("examples")}.each { prj ->
            prj.tasks.withType(Javadoc).each { javadocTask ->
              source += javadocTask.source
              classpath += javadocTask.classpath
              excludes += javadocTask.excludes
              includes += javadocTask.includes
              dependsOn javadocTask
            }
          }
        }
      }
    }
  }
}
