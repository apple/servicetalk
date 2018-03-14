/**
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin

class ServiceTalkBuildPlugin implements Plugin<Project> {
  void apply(Project project) {
    configureProperties project

    applyDocPlugins project
    applyIdeaPlugin project
    applyEclipsePlugin project

    if (project.subprojects) {
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

  private static void applyIdeaPlugin(Project project) {
    project.configure(project) {
      apply plugin: "idea"

      // safer/easier to always regenerate
      tasks.idea.dependsOn tasks.cleanIdea

      if (project.parent == null) {
        idea.project.languageLevel = "1.8"
        idea.project.ipr.withXml { XmlProvider provider ->
          def xmlProject = provider.asNode()
          def xmlComponents = new XmlParser().parse(getClass().getResourceAsStream("idea/components.xml"))
          xmlComponents.children().each { xmlProject.append it }
        }
      }
    }
  }

  private static void applyEclipsePlugin(Project project) {
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
      }
    }
  }

  private static void applyCommonPlugins(Project project) {
    project.configure(project) {
      apply plugin: "maven-publish"

      jar {
        manifest {
          attributes "Built-JDK": System.getProperty("java.version"),
              "Specification-Title": project.name,
              "Specification-Version": "${-> project.version}",
              "Specification-Vendor": "Apple Inc.",
              "Implementation-Title": project.name,
              "Implementation-Version": "${-> project.version}",
              "Implementation-Vendor": "Apple Inc.",
              "Automatic-Module-Name": "io.${project.name.replace("-", ".")}"
        }
      }

      javadoc {
        options.noQualifiers "all"
      }

      project.task("sourcesJar", type: Jar, dependsOn: classes) {
        classifier = "sources"
        from sourceSets.main.allSource
      }

      project.task("javadocJar", type: Jar, dependsOn: javadoc) {
        classifier = "javadoc"
        from javadoc.destinationDir
      }

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
        config = resources.text.fromString(getClass().getResourceAsStream("checkstyle/checkstyle.xml").text)
        sourceSets = [sourceSets.main, sourceSets.test]
      }

      checkstyleMain {
        // Required by some of the checks
        // https://discuss.gradle.org/t/checkstyle-not-getting-compiled-classes-on-classpath-in-1-0-milestone-6/2353/10
        classpath += configurations.compile
      }

      checkstyleTest {
        // Required by some of the checks
        // https://discuss.gradle.org/t/checkstyle-not-getting-compiled-classes-on-classpath-in-1-0-milestone-6/2353/10
        classpath += configurations.compile

        config = resources.text.fromString(getClass().getResourceAsStream("checkstyle/checkstyle-test.xml").text)
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
      SourceSetContainer projectSourceSets = project.sourceSets
      def testFixturesSourceSet = projectSourceSets.create("testFixtures") {
        compileClasspath += projectSourceSets["main"].output
        runtimeClasspath += projectSourceSets["main"].output
      }

      project.task("testFixturesJar", type: Jar) {
        appendix = "testFixtures"
        from testFixturesSourceSet.output
      }

      // for project dependencies
      project.artifacts.add("testFixturesRuntime", testFixturesJar)

      sourceSets.test.compileClasspath += testFixturesSourceSet.output
      sourceSets.test.runtimeClasspath += testFixturesSourceSet.output

      project.dependencies {
        testFixturesCompile project.configurations["compile"]
        testFixturesRuntime project.configurations["runtime"]
        testCompile project.configurations["testFixturesCompile"]
        testRuntime project.configurations["testFixturesRuntime"]
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
