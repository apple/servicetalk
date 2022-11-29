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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc

import java.lang.reflect.Field

final class ProjectUtils {
  private static final String DISABLE_REPOS_INHERIT_PROP = "disableInheritBuildscriptRepositories"

  /**
   * It is necessary to have access to plugins repositories in order to build ServiceTalk's Gradle plugins.
   * Typically these repositories are defined in the `buildscript.repositories` of the build script,
   * so this method attempts to copy them to the main `repositories`, if they are not already there.
   */
  static void inheritRepositoriesFromBuildScript(Project project) {
    if (project.hasProperty(DISABLE_REPOS_INHERIT_PROP)) {
      return
    }

    // We only consider URL-bearing repositories in order to have something concrete to compare,
    // since org.gradle.api.artifacts.repositories.ArtifactRepository doesn't expose an `equal` method
    def repoFilter = { it instanceof IvyArtifactRepository || it instanceof MavenArtifactRepository }

    def configuredRepos =
        project.repositories
            .findAll(repoFilter)
            .collect { new Tuple(it.class, it.url) }
            .toSet()

    def missingRepos = project.buildscript.repositories
        .findAll(repoFilter)
        .findAll { !configuredRepos.contains(new Tuple(it.class, it.url)) }
        .toSet()

    if (missingRepos) {
      if (!project.parent) {
        project.logger.quiet("Some repositories are only configured for buildscript, " +
            "which typically entails that this build will fail because it won't be able to resolve plugins that are " +
            "used as regular dependencies (required by ServiceTalk plugins).\n\n" +
            "Attempting to resolve the issue by adding the following repositories: {}\n\n" +
            "(if problematic, this behavior can be disabled with: -P{})\n",
            missingRepos.collect { it.displayName }, DISABLE_REPOS_INHERIT_PROP)
      }

      // This code is unfortunate but there's no way to re-use an existing repository in another container
      // as it has already been uniquely named in its original container, and repositories are not cloneable
      // nor do they expose a copy constructor/method
      Field isPartOfContainerField = AbstractArtifactRepository.class.getDeclaredField("isPartOfContainer")
      isPartOfContainerField.accessible = true

      missingRepos.each { repo ->
        isPartOfContainerField.set(repo, false)
        project.repositories.addRepository(repo, repo.name)
      }
    }
  }

  static void enforceUtf8FileSystem() {
    def fenc = System.getProperty("file.encoding")
    if (!"UTF-8".equalsIgnoreCase(fenc)) {
      throw new GradleException("File encoding must be UTF-8 but is $fenc, consider using a file system that " +
          "supports it or setting the JAVA_TOOL_OPTIONS env var to: -Dfile.encoding=UTF-8.\n\nMake sure the jvm is " +
          "restarted to pickup these changes (e.g. `gradle --stop`, and restart your IDE)!");
    }
  }

  static void addBuildContextExtensions(Project project) {
    project.ext {
      isCiBuild = "true" == System.getenv("CI")
      isReleaseBuild = project.hasProperty("releaseBuild")
    }
  }

  static void enforceProjectVersionScheme(Project project) {
    def endsWithSnapshot = project.version.toString().toUpperCase().endsWith("-SNAPSHOT")
    if (project.ext.isReleaseBuild) {
      if (endsWithSnapshot) {
        throw new GradleException("Project version for a release build must not contain a '-SNAPSHOT' suffix")
      }
    } else if (!endsWithSnapshot) {
      if (!project.parent) {
        project.logger.quiet("Adding missing '-SNAPSHOT' suffix to project version $project.version for non-release build.")
      }
      project.version += "-SNAPSHOT"
    }
  }

  static void addManifestAttributes(Project project, Manifest manifest) {
    manifest.attributes("Built-JDK": System.getProperty("java.version"),
        "Specification-Title": project.name,
        "Specification-Version": "${-> project.version}",
        "Specification-Vendor": "Apple Inc.",
        "Implementation-Title": project.name,
        "Implementation-Version": "${-> project.version}",
        "Implementation-Vendor": "Apple Inc.",
        "Automatic-Module-Name": "io.${project.name.replace("-", ".")}"
    )
  }

  // https://docs.gradle.org/7.6-rc-3/userguide/validation_problems.html#implicit_dependency
  static void addGeneratedProtoDependsOn(Project project,
                                         /*com.google.protobuf.gradle.GenerateProtoTask*/ DefaultTask task,
                                         boolean projectTestGeneratedOnly) {
    if (!projectTestGeneratedOnly) {
      if (!task.isTest) {
        project.sourcesJar.dependsOn task
      }
      // generateTestProto tasks also depends upon non-test proto tasks
      project.pmdMain.dependsOn task
      project.spotbugsMain.dependsOn task
      project.checkstyleMain.dependsOn task
    }
    project.pmdTest.dependsOn task
    project.spotbugsTest.dependsOn task
    project.checkstyleTest.dependsOn task
  }

  private static <T extends Task> T createTask(Project project, String name, @DelegatesTo.Target Class<T> type,
                                               @DelegatesTo(strategy = 1, genericTypeIndex = 0) Closure<?> config) {
    project.task(name, type: type, config) as T
  }

  static Jar createSourcesJarTask(Project project, SourceSet sourceSet) {
    return createTask(project, sourceSet.getTaskName(null, "sourcesJar"), Jar) {
      description = "Assembles a Jar archive containing the $sourceSet.name sources."
      group = JavaBasePlugin.DOCUMENTATION_GROUP
      archiveAppendix = sourceSet.name == "main" ? null : sourceSet.name
      classifier = "sources"
      addManifestAttributes(project, manifest)
      from sourceSet.allSource
    }
  }

  static Javadoc getOrCreateJavadocTask(Project project, SourceSet sourceSet) {
    def javadocTaskName = sourceSet.getTaskName(null, "javadoc")
    def javadocTask = project.tasks.findByName(javadocTaskName)
    if (!javadocTask) {
      javadocTask = createTask(project, javadocTaskName, Javadoc) {
        description = "Generates Javadoc API documentation for the $sourceSet.name source code."
        group = JavaBasePlugin.DOCUMENTATION_GROUP
        classpath = sourceSet.output + sourceSet.compileClasspath
        source = sourceSet.allJava
        conventionMapping.destinationDir = { project.file("$project.docsDir/$javadocTaskName") }
        conventionMapping.title = { "$project.name $project.version $sourceSet.name API" as String }
      }
    }
    javadocTask
  }

  static Jar createJavadocJarTask(Project project, SourceSet sourceSet) {
    createJavadocJarTask(project, sourceSet, getOrCreateJavadocTask(project, sourceSet))
  }

  static Jar createJavadocJarTask(Project project, SourceSet sourceSet, Javadoc javadocTask) {
    createTask(project, sourceSet.getTaskName(null, "javadocJar"), Jar) {
      description = "Assembles a Jar archive containing the $sourceSet.name Javadoc."
      group = JavaBasePlugin.DOCUMENTATION_GROUP
      archiveAppendix = sourceSet.name == "main" ? null : sourceSet.name
      classifier = "javadoc"
      addManifestAttributes(project, manifest)
      from javadocTask
    }
  }

  static File copyResource(String resourceSourcePath, DirectoryProperty destinationFolder) {
    def content = ProjectUtils.class.getResource(resourceSourcePath).text
    writeToFile(content, destinationFolder, new File(resourceSourcePath).name)
  }

  static File copyResource(String resourceSourcePath, File destinationFolder) {
    def content = ProjectUtils.class.getResource(resourceSourcePath).text
    writeToFile(content, destinationFolder, new File(resourceSourcePath).name)
  }

  static File writeToFile(String content, DirectoryProperty folder, String fileName) {
    def destAsFile = folder.get().asFile;
    writeToFile(content, destAsFile, fileName)
  }

  static File writeToFile(String content, File folder, String fileName) {
    def file = new File(folder, fileName)
    if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
      throw new IOException("Unable to create directory: " + file.parentFile)
    }
    file.createNewFile()
    file.write(content)
    file
  }

  static appendNodes(XmlProvider provider, InputStream resource) {
    def xmlProject = provider.asNode()
    def xmlComponents = new XmlParser().parse(resource)
    xmlComponents.children().each { newChild ->
      // remove a <component> with the same name, so we don't append the new one multiple times.
      def oldChild = xmlProject.get("component").find { newChild.@name.equals(it.@name) }
      if (oldChild != null) {
        xmlProject.remove(oldChild)
      }
      xmlProject.append(newChild)
    }
  }

  static File locateBuildLevelConfigFile(Project project, String path) {
    File configFile = project.file("$project.rootProject.projectDir/$path")
    configFile.exists() ? configFile : project.file("$project.projectDir/$path")
  }

  static void addQualityTask(Project project) {
    project.configure(project) {
      project.task("quality") {
        description = "Run all quality analyzers for all source sets"
        group = "verification"
        if (tasks.findByName("checkstyle")) {
          dependsOn tasks.checkstyle
        }
        if (tasks.findByName("pmd")) {
          dependsOn tasks.pmd
        }
        if (tasks.findByName("spotbugs")) {
          dependsOn tasks.spotbugs
        }
        if (tasks.findByName("javadoc")) {  // verifies that javadoc generates without errors
          dependsOn tasks.javadoc
        }
      }
    }
  }
}
