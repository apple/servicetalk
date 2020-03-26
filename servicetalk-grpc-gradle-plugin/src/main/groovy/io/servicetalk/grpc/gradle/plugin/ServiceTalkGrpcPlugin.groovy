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
package io.servicetalk.grpc.gradle.plugin

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.util.GradleVersion

import java.nio.charset.StandardCharsets

class ServiceTalkGrpcPlugin implements Plugin<Project> {
  void apply(Project project) {
    if (GradleVersion.current().baseVersion < GradleVersion.version("4.10")) {
      throw new GradleException("This plugin requires Gradle 4.10 or higher.")
    }

    if (project.plugins.hasPlugin("com.google.protobuf")) {
      throw new InvalidUserCodeException("Plugin `servicetalk-grpc` needs to be applied " +
          "*before* plugin `com.google.protobuf`. (The latter is applied automatically by the former.)")
    }

    ServiceTalkGrpcExtension extension = project.extensions.create("serviceTalkGrpc", ServiceTalkGrpcExtension)
    extension.conventionMapping.generatedCodeDir = { project.file("$project.buildDir/generated/source/proto") }

    def compileOnlyDeps = project.getConfigurations().getByName("compileOnly").getDependencies()
    def testCompileOnlyDeps = project.getConfigurations().getByName("testCompileOnly").getDependencies()
    project.afterEvaluate {
      Properties pluginProperties = new Properties()
      pluginProperties.load(getClass().getResourceAsStream("/META-INF/servicetalk-grpc-gradle-plugin.properties"))

      // In order to locate servicetalk-grpc-protoc we need either the ServiceTalk version for artifact resolution
      // or be provided with a direct path to the protoc plugin executable
      def serviceTalkGrpcProtoc = "servicetalk-grpc-protoc"
      def serviceTalkVersion = pluginProperties."implementation-version"
      def serviceTalkProtocPluginPath = extension.serviceTalkProtocPluginPath
      if (!serviceTalkVersion && !serviceTalkProtocPluginPath) {
        throw new IllegalStateException("Failed to retrieve ServiceTalk version from plugin meta " +
          "and `serviceTalkGrpc.serviceTalkProtocPluginPath` is not set.")
      }

      def protobufVersion = extension.protobufVersion
      if (!protobufVersion) {
        throw new InvalidUserDataException("Please set `serviceTalkGrpc.protobufVersion`.")
      }

      // If this project is outside of ServiceTalk's gradle build we need to add an explicit dependency on the
      // uber jar which contains the protoc logic, as otherwise the grpc-gradle-plugin will only add a dependency
      // on the executable script
      File uberJarFile
      String scriptNamePrefix
      if (serviceTalkProtocPluginPath) {
        scriptNamePrefix = serviceTalkGrpcProtoc + "-" + project.version
        uberJarFile = new File(serviceTalkProtocPluginPath)
      } else {
        scriptNamePrefix = serviceTalkGrpcProtoc + "-" + serviceTalkVersion
        def stGrpcProtocDep =
            project.getDependencies().create("io.servicetalk:$servicetalk-grpc-protoc:$serviceTalkVersion:all")
        compileOnlyDeps.add(stGrpcProtocDep)
        testCompileOnlyDeps.add(stGrpcProtocDep)

        uberJarFile = project.configurations.compileOnly.find { it.name.startsWith(serviceTalkGrpcProtoc) }
        if (uberJarFile == null) {
          throw new IllegalStateException("failed to find the $serviceTalkGrpcProtoc:$serviceTalkVersion:all")
        }
      }

      File scriptExecutableFile
      try {
        if (org.gradle.internal.os.OperatingSystem.current().isWindows()) {
          scriptExecutableFile = File.createTempFile(scriptNamePrefix, ".bat")
          prepareScriptFile(scriptExecutableFile)
          new FileOutputStream(scriptExecutableFile).withCloseable { execOutputStream ->
            execOutputStream.write(("@ECHO OFF\r\n" +
                "java -jar " + uberJarFile.getAbsolutePath() + " %*\r\n").getBytes(StandardCharsets.US_ASCII))
          }
        } else {
          scriptExecutableFile = File.createTempFile(scriptNamePrefix, ".sh")
          prepareScriptFile(scriptExecutableFile)
          new FileOutputStream(scriptExecutableFile).withCloseable { execOutputStream ->
            execOutputStream.write(("#!/bin/sh\n" +
                "exec java -jar " + uberJarFile.getAbsolutePath() + " \"\$@\"\n").getBytes(StandardCharsets.US_ASCII))
          }
        }
        finalizeOutputFile(scriptExecutableFile)
      } catch (Exception e) {
        throw new IllegalStateException("servicetalk-grpc-gradle plugin failed to create executable script file which executes the protoc jar plugin.", e)
      }

      project.configure(project) {
        Task ideaTask = extension.generateIdeConfiguration ? project.tasks.findByName("ideaModule") : null
        Task eclipseTask = extension.generateIdeConfiguration ? project.tasks.findByName("eclipse") : null

        protobuf {
          protoc {
            artifact = "com.google.protobuf:protoc:$protobufVersion"
          }

          plugins {
            servicetalk_grpc {
              path = scriptExecutableFile
            }
          }

          generateProtoTasks {
            all().each { task ->
              task.plugins {
                servicetalk_grpc {
                  outputSubDir = "java"
                }
              }

              if (ideaTask != null) {
                ideaTask.dependsOn(task)
              }
              if (eclipseTask != null) {
                eclipseTask.dependsOn(task)
              }
            }
          }

          generatedFilesBaseDir = project.file(extension.generatedCodeDir).absolutePath
        }

        clean {
          delete protobuf.generatedFilesBaseDir
        }

        if (extension.generateIdeConfiguration) {
          def generatedMainDir = project.file("$extension.generatedCodeDir/main/java")
          def generatedTestDir = project.file("$extension.generatedCodeDir/test/java")

          project.plugins.withType(IdeaPlugin) {
            project.idea.module {
              sourceDirs += [generatedMainDir]
              testSourceDirs += [generatedTestDir]
              generatedSourceDirs += [generatedMainDir, generatedTestDir]
            }
          }

          project.plugins.withType(EclipsePlugin) {
            def addOptionalAttributesNode = { node ->
              def attributesNode = new Node(node, 'attributes')
              def attributeNode = new Node(attributesNode, 'attribute', [name: 'optional', value: 'true'])
              node.append(attributeNode)
              return node
            }

            project.eclipse {
              classpath {
                file {
                  withXml {
                    def node = it.asNode()
                    addOptionalAttributesNode(new Node(node, 'classpathentry', [kind: 'src', path: project.relativePath(generatedMainDir)]))
                    addOptionalAttributesNode(new Node(node, 'classpathentry', [kind: 'src', path: project.relativePath(generatedTestDir)]))
                  }
                }
              }
            }
          }
        }
      }
    }

    // Google's plugin processes its configuration in an afterEvaluate callback,
    // hence we need to apply it *after* registering our own afterEvaluate callback
    project.plugins.apply("com.google.protobuf")

    project.afterEvaluate {
      // Protobuf plugin forcefully adds to IDEA's model
      if (!extension.generateIdeConfiguration) {
        IdeaModel ideaModel = project.getExtensions().findByType(IdeaModel)

        if (ideaModel != null) {
          def generatedMainDir = project.file("$extension.generatedCodeDir/main/java")
          ideaModel.module.sourceDirs.remove(generatedMainDir)

          def generatedTestDir = project.file("$extension.generatedCodeDir/test/java")
          ideaModel.module.testSourceDirs.remove(generatedTestDir)
        }
      }
    }
  }

  private static void prepareScriptFile(File outputFile) throws IOException {
    if (!outputFile.exists()) {
      if (!outputFile.getParentFile().isDirectory() && !outputFile.getParentFile().mkdirs()) {
        throw new IOException("unable to make directories for file: " + outputFile.getCanonicalPath())
      }
    } else {
      // Clear the file's contents
      new PrintWriter(outputFile).close()
    }
  }

  private static void finalizeOutputFile(File outputFile) throws IOException {
    if (!outputFile.setExecutable(true)) {
      outputFile.delete()
      throw new IOException("unable to set file as executable: " + outputFile.getCanonicalPath())
    }
  }
}
