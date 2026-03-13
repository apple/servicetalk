/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
 * ServiceTalk-based transport implementations for OpenTelemetry SDK exporters.
 * <p>
 * This package provides implementations of OpenTelemetry's {@code GrpcSender} and
 * {@code HttpSender} interfaces, which enable using ServiceTalk's high-performance HTTP and
 * gRPC clients as the underlying transport for OpenTelemetry telemetry data.
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.servicetalk.opentelemetry.client.ServiceTalkHttpSender} - HTTP transport using
 *   ServiceTalk's HTTP client</li>
 *   <li>{@link io.servicetalk.opentelemetry.client.ServiceTalkGrpcSender} - gRPC transport using
 *   ServiceTalk's HTTP/2 client</li>
 * </ul>
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create an HTTP sender
 * ServiceTalkHttpSender httpSender = ServiceTalkHttpSender.builder()
 *     .setEndpoint("http://localhost:4318/v1/traces")
 *     .setCompression(true)
 *     .build();
 *
 * // Use with OpenTelemetry's OTLP exporter
 * SpanExporter spanExporter = OtlpHttpSpanExporter.builder()
 *     .setHttpSender(httpSender)
 *     .build();
 *
 * // Register with the OpenTelemetry SDK
 * SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
 *     .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
 *     .build();
 * }</pre>
 * <p>
 * These senders work universally with traces, metrics, and logs - the same sender implementation
 * can be used with {@code OtlpHttpSpanExporter}, {@code OtlpHttpMetricExporter}, and
 * {@code OtlpHttpLogRecordExporter}.
 *
 * @see io.servicetalk.opentelemetry.client.ServiceTalkHttpSender
 * @see io.servicetalk.opentelemetry.client.ServiceTalkGrpcSender
 */
@ElementsAreNonnullByDefault
package io.servicetalk.opentelemetry.client;

import io.servicetalk.annotations.ElementsAreNonnullByDefault;
