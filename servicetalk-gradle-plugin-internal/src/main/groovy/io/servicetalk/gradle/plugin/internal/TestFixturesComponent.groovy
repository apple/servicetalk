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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.model.ObjectFactory

import static java.util.Collections.emptySet
import static org.gradle.api.internal.attributes.ImmutableAttributes.EMPTY
import static org.gradle.util.GUtil.toWords

class TestFixturesComponent implements SoftwareComponentInternal {
  private final Set<TestFixturesUsageContext> usages

  TestFixturesComponent(Project project) {
    usages = [
        new TestFixturesUsageContext(project.configurations["testFixturesImplementation"], project.objects),
        new TestFixturesUsageContext(project.configurations["testFixturesRuntime"], project.objects)
    ]
  }

  @Override
  Set<TestFixturesUsageContext> getUsages() {
    usages
  }

  @Override
  String getName() {
    "javaTestFixtures"
  }

  private static class TestFixturesUsageContext implements UsageContext {
    private final Configuration configuration
    private final Usage usage

    TestFixturesUsageContext(Configuration configuration, ObjectFactory objectFactory) {
      this.configuration = configuration
      def usageName = toWords(configuration.name, '-' as char).toLowerCase()
      usage = objectFactory.named(Usage.class, usageName)
    }

    @Override
    Usage getUsage() {
      usage
    }

    @Override
    Set<? extends PublishArtifact> getArtifacts() {
      emptySet()
    }

    @Override
    Set<? extends ModuleDependency> getDependencies() {
      configuration.incoming.dependencies.withType(ModuleDependency.class)
    }

    @Override
    Set<? extends DependencyConstraint> getDependencyConstraints() {
      configuration.incoming.dependencyConstraints
    }

    @Override
    Set<? extends Capability> getCapabilities() {
      emptySet()
    }

    @Override
    String getName() {
      configuration.name
    }

    @Override
    AttributeContainer getAttributes() {
      EMPTY
    }

    @Override
    Set<ExcludeRule> getGlobalExcludes() {
      emptySet()
    }
  }
}
