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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc

class ProjectUtils {

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

  private static <T extends Task> T createTask(Project project, String name, @DelegatesTo.Target Class<T> type,
                                               @DelegatesTo(strategy = 1, genericTypeIndex = 0) Closure<?> config) {
    project.task(name, type: type, config) as T
  }

  static Jar createSourcesJarTask(Project project, SourceSet sourceSet) {
    return createTask(project, sourceSet.getTaskName(null, "sourcesJar"), Jar) {
      description = "Assembles a Jar archive containing the $sourceSet.name sources."
      group = JavaBasePlugin.DOCUMENTATION_GROUP
      appendix = sourceSet.name == "main" ? null : sourceSet.name
      classifier = "sources"
      addManifestAttributes(project, manifest)
      from sourceSet.allSource
    }
  }

  static Jar createJavadocJarTask(Project project, SourceSet sourceSet) {
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

    return createTask(project, sourceSet.getTaskName(null, "javadocJar"), Jar) {
      description = "Assembles a Jar archive containing the $sourceSet.name Javadoc."
      group = JavaBasePlugin.DOCUMENTATION_GROUP
      appendix = sourceSet.name == "main" ? null : sourceSet.name
      classifier = "javadoc"
      addManifestAttributes(project, manifest)
      from javadocTask
    }
  }

  static File copyResource(String resourceSourcePath, File destinationFolder) {
    def content = ProjectUtils.class.getResource(resourceSourcePath).text
    writeToFile(content, destinationFolder, new File(resourceSourcePath).name)
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

  /**
   * MavenPublication currently wrongly outputs BOM dependencies as regular dependencies instead of
   * outputting them in a dependencyManagement, with 'pom' type and 'import' scope.
   * This method corrects the issue.
   */
  static void fixBomDependencies(MavenPom pom) {
    pom.withXml {
      Node rootNode = it.asNode()
      Node dependenciesNode = rootNode["dependencies"].find() as Node
      if (!dependenciesNode) {
        return
      }

      List<Node> bomDependencies = dependenciesNode.children().findAll { it["artifactId"].text().contains("-bom") } as List<Node>
      if (!bomDependencies) {
        return
      }

      bomDependencies.each { dependenciesNode.remove(it) }
      Node managedDependenciesNode = getOrCreateNode(getOrCreateNode(rootNode, "dependencyManagement"), "dependencies")
      bomDependencies.collect {
        getOrCreateNode(it, "type").setValue("pom")
        getOrCreateNode(it, "scope").setValue("import")
        it
      }.each {
        managedDependenciesNode.append(it)
      }
    }
  }

  static Node getOrCreateNode(Node parentNode, String tag) {
    def node = parentNode[tag].find()
    if (!node) {
      node = parentNode.appendNode(tag)
    }
    node
  }
}
