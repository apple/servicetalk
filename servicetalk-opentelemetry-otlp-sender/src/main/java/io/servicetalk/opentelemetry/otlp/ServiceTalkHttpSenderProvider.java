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

import io.servicetalk.http.api.HttpClient;

import io.opentelemetry.sdk.common.export.HttpSender;
import io.opentelemetry.sdk.common.export.HttpSenderConfig;
import io.opentelemetry.sdk.common.export.HttpSenderProvider;

import java.net.URI;

/**
 * SPI implementation that wires the OpenTelemetry OTLP/HTTP exporter onto a ServiceTalk
 * {@link HttpClient}. Loaded via {@link java.util.ServiceLoader}; not for direct use.
 */
public final class ServiceTalkHttpSenderProvider implements HttpSenderProvider {

    @Override
    public HttpSender createSender(HttpSenderConfig config) {
        HttpClient httpClient = ServiceTalkHttpClientFactory.buildHttpClient(
                config.getEndpoint(),
                config.getTimeout(),
                config.getConnectTimeout(),
                config.getSslContext(),
                config.getProxyOptions(),
                config.getRetryPolicy(),
                config.getHeadersSupplier()
        );

        URI endpoint = config.getEndpoint();
        String requestTarget = endpoint.getPath();
        if (requestTarget == null || requestTarget.isEmpty()) {
            requestTarget = "/";
        }

        return new ServiceTalkHttpSender(
                httpClient,
                config.getCompressor(),
                config.getContentType(),
                requestTarget
        );
    }
}
