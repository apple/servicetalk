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
 * ServiceTalk transport backend for the OpenTelemetry OTLP exporters.
 *
 * <p>This module provides {@link io.opentelemetry.sdk.common.export.GrpcSenderProvider} and
 * {@link io.opentelemetry.sdk.common.export.HttpSenderProvider} SPI implementations that route
 * OTLP exports through ServiceTalk clients. With this module on the classpath the standard
 * OpenTelemetry OTLP exporters
 * ({@code OtlpGrpcSpanExporter}, {@code OtlpHttpSpanExporter}, etc.) automatically discover
 * and use the ServiceTalk-based senders via {@link java.util.ServiceLoader}.
 *
 * <p>This is an alternative to the upstream OkHttp-based sender shipped with the OpenTelemetry
 * SDK. If both this module and {@code opentelemetry-exporter-sender-okhttp} are on the classpath
 * the OpenTelemetry SDK selects only one provider; for deterministic behavior, exclude
 * {@code opentelemetry-exporter-sender-okhttp}.
 */
@ElementsAreNonnullByDefault
package io.servicetalk.opentelemetry.otlp;

import io.servicetalk.annotations.ElementsAreNonnullByDefault;
