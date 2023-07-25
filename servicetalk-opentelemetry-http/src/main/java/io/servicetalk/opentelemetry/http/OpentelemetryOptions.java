/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A set of options for creating the opentelemetry filter.
 */
public final class OpentelemetryOptions {

    private final List<String> captureRequestHeaders;
    private final List<String> captureResponseHeaders;
    private final boolean enableMetrics;

    OpentelemetryOptions(List<String> captureRequestHeaders, List<String> captureResponseHeaders,
                         boolean enableMetrics) {
        this.captureRequestHeaders = captureRequestHeaders;
        this.captureResponseHeaders = captureResponseHeaders;
        this.enableMetrics = enableMetrics;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public List<String> getCaptureRequestHeaders() {
        return Collections.unmodifiableList(captureRequestHeaders);
    }

    public List<String> getCaptureResponseHeaders() {
        return Collections.unmodifiableList(captureResponseHeaders);
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    /**
     * a Builder of {@link OpentelemetryOptions}.
     */
    public static class Builder {
        private List<String> captureRequestHeaders = new ArrayList<>();
        private List<String> captureResponseHeaders = new ArrayList<>();
        private boolean enableMetrics;

        /**
         * set the headers to be captured as extra tags.
         * @param captureRequestHeaders extra headers to be captured in client/server requests and added as tags.
         * @return an instance of itself
         */
        public OpentelemetryOptions.Builder captureRequestHeaders(List<String> captureRequestHeaders) {
            this.captureRequestHeaders.addAll(Objects.requireNonNull(captureRequestHeaders));
            return this;
        }

        /**
         * set the headers to be captured as extra tags.
         * @param captureResponseHeaders extra headers to be captured in client/server response and added as tags.
         * @return an instance of itself
         */
        public OpentelemetryOptions.Builder captureResponseHeaders(List<String> captureResponseHeaders) {
            this.captureResponseHeaders.addAll(Objects.requireNonNull(captureResponseHeaders));
            return this;
        }

        /**
         * whether to enable span metrics.
         * @param enableMetrics whether to enable opentelemetry metrics.
         * @return an instance of itself
         */
        public OpentelemetryOptions.Builder enableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }

        public OpentelemetryOptions build() {
            return new OpentelemetryOptions(captureRequestHeaders, captureResponseHeaders, enableMetrics);
        }
    }
}
