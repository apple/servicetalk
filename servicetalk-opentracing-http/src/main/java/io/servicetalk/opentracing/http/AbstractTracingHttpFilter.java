/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.http;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;
import io.servicetalk.opentracing.inmemory.B3KeyValueFormatter;
import io.servicetalk.opentracing.inmemory.api.InMemorySpanContextFormat;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;

import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.opentracing.http.AbstractTracingHttpFilter.HttpHeadersB3KeyValueFormatter.traceStateFormatter;
import static java.util.Objects.requireNonNull;

abstract class AbstractTracingHttpFilter {

    protected final Tracer tracer;
    protected final String componentName;
    protected final InMemorySpanContextFormat<HttpHeaders> formatter;

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids.
     */
    AbstractTracingHttpFilter(final Tracer tracer,
                              final String componentName,
                              final boolean validateTraceKeyFormat) {
        this.tracer = requireNonNull(tracer);
        this.componentName = requireNonNull(componentName);
        this.formatter = traceStateFormatter(validateTraceKeyFormat);
    }

    static final class HttpHeadersB3KeyValueFormatter extends B3KeyValueFormatter<HttpHeaders> {

        static final InMemorySpanContextFormat<HttpHeaders> FORMATTER_VALIDATION =
                new HttpHeadersB3KeyValueFormatter(true);
        static final InMemorySpanContextFormat<HttpHeaders> FORMATTER_NO_VALIDATION =
                new HttpHeadersB3KeyValueFormatter(false);

        HttpHeadersB3KeyValueFormatter(boolean verifyExtractedValues) {
            super(HttpHeaders::set, HttpHeaders::get, verifyExtractedValues);
        }

        static InMemorySpanContextFormat<HttpHeaders> traceStateFormatter(boolean validateTraceKeyFormat) {
            return validateTraceKeyFormat ? FORMATTER_VALIDATION : FORMATTER_NO_VALIDATION;
        }
    }

    static class ScopeTracker implements TerminalSignalConsumer {

        private final Scope currentScope;
        private final Span span;

        @Nullable
        private HttpResponseMetaData metaData;

        ScopeTracker(Scope currentScope, final Span span) {
            this.currentScope = requireNonNull(currentScope);
            this.span = requireNonNull(span);
        }

        void onResponseMeta(final HttpResponseMetaData metaData) {
            this.metaData = metaData;
        }

        @Override
        public void onComplete() {
            assert metaData != null : "can't have succeeded without capturing metadata first";
            tagStatusCode();
            try {
                if (isError(metaData)) {
                    ERROR.set(span, true);
                }
            } finally {
                closeAll();
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            try {
                tagStatusCode();
                ERROR.set(span, true);
            } finally {
                closeAll();
            }
        }

        @Override
        public void cancel() {
            try {
                tagStatusCode();
                ERROR.set(span, true);
            } finally {
                closeAll();
            }
        }

        /**
         * Determine if a {@link HttpResponseMetaData} should be considered an error from a tracing perspective.
         * @param metaData The {@link HttpResponseMetaData} to test.
         * @return {@code true} if the {@link HttpResponseMetaData} should be considered an error for tracing.
         */
        protected boolean isError(final HttpResponseMetaData metaData) {
            return metaData.status().statusClass().equals(SERVER_ERROR_5XX);
        }

        Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle) {
            return responseSingle.liftSync(new BeforeFinallyHttpOperator(this))
                    // BeforeFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
                    // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed before
                    // completion of Meta. So in order for downstream operators to get a consistent view of the data
                    // path beforeOnSuccess() needs to be applied last.
                    .beforeOnSuccess(this::onResponseMeta);
        }

        private void tagStatusCode() {
            if (metaData != null) {
                HTTP_STATUS.set(span, metaData.status().code());
            }
        }

        private void closeAll() {
            try {
                currentScope.close();
            } finally {
                span.finish();
            }
        }

        final Span getSpan() {
            return span;
        }
    }
}
