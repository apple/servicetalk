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

import static org.gradle.api.JavaVersion.VERSION_1_8

final class Versions {
  static final String CHECKSTYLE_VERSION = "8.43"
  static final String PMD_VERSION = "6.35.0"
  static final String SPOTBUGS_VERSION = "4.2.3"
  static final String PITEST_VERSION = "1.6.7"
  static final String PITEST_JUNIT5_PLUGIN_VERSION = "0.12"
  static final JavaVersion TARGET_VERSION = VERSION_1_8
}
