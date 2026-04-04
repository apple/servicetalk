/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

/**
 * End-to-end example demonstrating ServiceTalk's OpenTelemetry integration with Jaeger.
 * <p>
 * This package shows how ServiceTalk's HTTP transport is automatically discovered and used
 * by OpenTelemetry SDK via Java's Service Provider Interface (SPI) mechanism. No explicit
 * transport configuration is required - simply having servicetalk-opentelemetry-client on
 * the classpath is sufficient.
 *
 * @see io.servicetalk.examples.http.opentelemetry.jaeger.JaegerOtlpExample
 */
package io.servicetalk.examples.http.opentelemetry.jaeger;
