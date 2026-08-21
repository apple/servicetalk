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
package io.servicetalk.opentelemetry.otlp;

import io.servicetalk.http.api.StreamingHttpClient;

import io.opentelemetry.sdk.common.export.GrpcSender;
import io.opentelemetry.sdk.common.export.GrpcSenderConfig;
import io.opentelemetry.sdk.common.export.GrpcSenderProvider;

/**
 * SPI implementation that wires the OpenTelemetry OTLP/gRPC exporter onto a ServiceTalk
 * {@link StreamingHttpClient}. Loaded via {@link java.util.ServiceLoader}; not for direct use.
 */
public final class ServiceTalkGrpcSenderProvider implements GrpcSenderProvider {

    @Override
    public GrpcSender createSender(GrpcSenderConfig config) {
        StreamingHttpClient httpClient = ServiceTalkHttpClientFactory.buildGrpcClient(
                config.getEndpoint(),
                config.getTimeout(),
                config.getConnectTimeout(),
                config.getSslContext(),
                config.getRetryPolicy(),
                config.getHeadersSupplier()
        );

        return new ServiceTalkGrpcSender(
                httpClient,
                config.getCompressor(),
                config.getFullMethodName()
        );
    }
}
