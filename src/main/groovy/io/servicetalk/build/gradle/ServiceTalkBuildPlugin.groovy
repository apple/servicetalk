/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.build.gradle

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
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
import static io.servicetalk.build.gradle.ProjectUtils.generateMavenDependencies
import static io.servicetalk.build.gradle.ProjectUtils.getOrCreateNode
import static io.servicetalk.build.gradle.ProjectUtils.writeToFile

class ServiceTalkBuildPlugin implements Plugin<Project> {
  void apply(Project project) {
    configureProperties project

    applyDocPlugins project

    if (project.subprojects) {
      applyIdeaPlugin project
      applyEclipsePlugin project

      project.subprojects {
        configureJavaProject it
      }
    } else {
      configureJavaProject project
    }
  }

  private static void configureJavaProject(Project project) {
    applyJavaPlugin project
    applyIdeaPlugin project
    applyEclipsePlugin project
    applyLicensePlugin project
    applyCommonPlugins project
    applyQualityPlugins project

    // TODO apply japicmp plugin

    configureSubProject project

    // TODO allow subprojects to opt-in test fixtures
    configureTestFixtures project
  }

  private static void configureProperties(Project project) {
    project.configure(project) {
      ext {
        nettyStableVersion = "4.1.22.Final"
        nettySnapshotVersion = "4.1.23.Final-SNAPSHOT"
        nettyTcnativeStableVersion = "2.0.7.Final"
        nettyTcnativeSnapshotVersion = "2.0.8.Final-SNAPSHOT"

        if (Boolean.valueOf(System.getenv("USE_NETTY_SNAPSHOT")) || Boolean.valueOf(System.getProperty("useNettySnapshot"))) {
          nettyVersion = nettySnapshotVersion
          nettyTcnativeVersion = nettyTcnativeSnapshotVersion
        } else {
          nettyVersion = nettyStableVersion
          nettyTcnativeVersion = nettyTcnativeStableVersion
        }

        jsr305Version = "3.0.2"

        log4jVersion = "2.10.0"
        slf4jVersion = "1.7.25"

        junitVersion = "4.12"
        testngVersion = "5.14.10"
        hamcrestVersion = "1.3"
        mockitoCoreVersion = "2.13.0"

        reactiveStreamsVersion = "1.0.2"
        jcToolsVersion = "2.1.1"
        jacksonVersion = "2.9.3"

        // Used for testing DNS ServiceDiscoverer
        apacheDirectoryServerVersion = "1.5.7"
        commonsLangVersion = "2.6"

        // Necessary for the japicmp.gradle script to check API/ABI compatibility against previous artificats.
        baselineAPIGroup = "io.servicetalk"
        baselineAPIVersion = "0.1.0-apple-SNAPSHOT"
      }
    }
  }

  private static void applyDocPlugins(Project project) {
    project.configure(project) {
      apply plugin: "org.asciidoctor.convert"

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

  private static void applyJavaPlugin(Project project) {
    project.configure(project) {
      apply plugin: "java"
    }
  }

  public static void applyIdeaPlugin(Project project) {
    project.configure(project) {
      apply plugin: "idea"

      // safer/easier to always regenerate
      tasks.idea.dependsOn tasks.cleanIdea

      if (project.parent == null) {
        idea.project.languageLevel = "1.8"
        idea.project.targetBytecodeVersion = JavaVersion.VERSION_1_8

        idea.project.ipr.withXml { XmlProvider provider ->
          appendNodes(provider, getClass().getResourceAsStream("idea/ipr-components.xml"))
        }
        idea.workspace.iws.withXml { XmlProvider provider ->
          appendNodes(provider, getClass().getResourceAsStream("idea/iws-components.xml"))
        }

        // This runs for every module, but the files are written to the correct location, but multiple times.
        idea.project.ipr {
          def resources = ["fileTemplates/includes/License.java",
                   "fileTemplates/internal/AnnotationType.java",
                   "fileTemplates/internal/Class.java",
                   "fileTemplates/internal/Enum.java",
                   "fileTemplates/internal/Interface.java",
                   "fileTemplates/internal/package-info.java"]

          // This is a bit of a hack. It depends on running gradle from the directory that you want to use as the
          // IntelliJ IDEA project dir.
          def folder = new File(".").canonicalFile
          for (def resource : resources) {
            copyResource("idea/" + resource, folder, resource)
          }
        }
      }
    }
  }

  public static void applyEclipsePlugin(Project project) {
    project.configure(project) {
      apply plugin: "eclipse"

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

  private static void applyLicensePlugin(Project project) {
    project.configure(project) {
      apply plugin: "com.github.hierynomus.license"
      license {
        header = null
        headerURI = getClass().getResource("license/HEADER.txt").toURI()
        strictCheck = true
        mapping {
          java='SLASHSTAR_STYLE'
        }
      }
    }
  }

  private static void applyCommonPlugins(Project project) {
    project.configure(project) {
      apply plugin: "maven-publish"

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

      // compatibility with apple"s CI system

      task("package", dependsOn: assemble)

      def versionString = version.toString()

      if (0 == repositories.size()) {
        repositories {
          jcenter()
        }
      } else {
        if (!versionString.endsWith("-apple")) {
          version += "-apple"
        }
      }

      if (!hasProperty("releaseBuild") && !versionString.endsWith("-SNAPSHOT")) {
        version += "-SNAPSHOT"
      }
    }
  }

  private static void applyQualityPlugins(Project project) {
    project.configure(project) {

      apply plugin: "checkstyle"
      apply plugin: "pmd"
      apply plugin: "com.github.spotbugs"

      checkstyle {
        toolVersion = "8.8"
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

      tasks.checkstyleMain.dependsOn checkstyleConfig
      tasks.checkstyleTest.dependsOn checkstyleConfig
      tasks.matching { it.name == "checkstyleTestFixtures" }.all {
        it.dependsOn checkstyleConfig
      }

      pmd {
        toolVersion = "6.1.0"
        sourceSets = [sourceSets.main, sourceSets.test]
        ruleSets = []
        ruleSetConfig = resources.text.fromString(getClass().getResourceAsStream("pmd/basic.xml").text)
      }

      // Exclusions are configured at each project level
      File spotbugsMainExclusionsFile = file("$rootDir/gradle/spotbugs/main-exclusions.xml")
      File spotbugsTestExclusionsFile = file("$rootDir/gradle/spotbugs/test-exclusions.xml")
      File spotbugsTestFixturesExclusionsFile = file("$rootDir/gradle/spotbugs/testFixtures-exclusions.xml")
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
    }
  }

  private static void configureSubProject(Project project) {
    project.configure(project) {
      sourceCompatibility = 1.8

      dependencies {
        compile "com.google.code.findbugs:jsr305:$jsr305Version"
        compile "org.slf4j:slf4j-api:$slf4jVersion"

        testCompile "junit:junit:$junitVersion"
        testCompile "org.hamcrest:hamcrest-library:$hamcrestVersion"
        testRuntime "org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion"
        testRuntime "org.apache.logging.log4j:log4j-core:$log4jVersion"
      }

      test {
        testLogging.showStandardStreams = true

        jvmArgs '-server', '-Xms2g', '-Xmx4g', '-dsa', '-da', '-ea:com.apple...', '-ea:servicetalk...', '-XX:+AggressiveOpts', '-XX:+TieredCompilation', '-XX:+UseBiasedLocking', '-XX:+UseFastAccessorMethods', '-XX:+OptimizeStringConcat', '-XX:+HeapDumpOnOutOfMemoryError', '-XX:+PrintGCDetails'
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
        testFixturesCompile project.configurations["compile"]
        testFixturesRuntime project.configurations["runtime"]
        testCompile project.configurations["testFixturesCompile"]
        testRuntime project.configurations["testFixturesRuntime"]
      }

      def sourcesJar = createSourcesJarTask(project, testFixturesSourceSet)
      def javadocJar = createJavadocJarTask(project, testFixturesSourceSet)

      publishing {
        publications {
          testFixtures(MavenPublication) {
            artifactId = "$testFixturesJar.baseName-$testFixturesJar.appendix"
            artifact(testFixturesJar)
            artifact(sourcesJar)
            artifact(javadocJar)
            pom.withXml { provider ->
              Node dependenciesNode = getOrCreateNode(provider.asNode(), "dependencies")
              Configuration testFixturesCompileConfig = project.configurations["testFixturesCompile"]
              Configuration testFixturesRuntimeConfig = project.configurations["testFixturesRuntime"]
              def mainPub = findByName("mavenJava")
              dependenciesNode.append(generateMavenDependencies(
                  [project.dependencies.create("$mainPub.groupId:$mainPub.artifactId:$mainPub.version")], "compile").first())
              for (depNode in generateMavenDependencies(testFixturesCompileConfig.allDependencies, "compile")) {
                dependenciesNode.append(depNode)
              }
              for (depNode in generateMavenDependencies(testFixturesRuntimeConfig.allDependencies - testFixturesCompileConfig.allDependencies, "runtime")) {
                dependenciesNode.append(depNode)
              }
            }
          }
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
