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
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.opentracing.http.AbstractTracingHttpFilter.HttpHeadersB3KeyValueFormatter.traceStateFormatter;
import static java.util.Objects.requireNonNull;

abstract class AbstractTracingHttpFilter {
    final Tracer tracer;
    final String componentName;
    final BiConsumer<SpanContext, HttpHeaders> injector;
    final Function<HttpHeaders, SpanContext> extractor;

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param validateTraceKeyFormat {@code true} to validate the contents of the trace ids while formatting.
     */
    AbstractTracingHttpFilter(final Tracer tracer, final String componentName, final boolean validateTraceKeyFormat) {
        this(tracer, componentName, traceStateFormatter(validateTraceKeyFormat));
    }

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param componentName The component name used during building new spans.
     * @param format the {@link Format} to use to inject/extract trace info to/from {@link HttpHeaders}.
     */
    AbstractTracingHttpFilter(final Tracer tracer, final String componentName, final Format<HttpHeaders> format) {
        requireNonNull(format);
        this.tracer = requireNonNull(tracer);
        this.componentName = requireNonNull(componentName);
        this.injector = (spanContext, headers) -> tracer.inject(spanContext, format, headers);
        this.extractor = headers -> tracer.extract(format, headers);
    }

    /**
     * Create a new instance.
     *
     * @param tracer The {@link Tracer}.
     * @param format the {@link Format} to use to inject/extract trace info to/from {@link TextMap}.
     * @param componentName The component name used during building new spans.
     */
    AbstractTracingHttpFilter(final Tracer tracer, final Format<TextMap> format, final String componentName) {
        requireNonNull(format);
        this.tracer = requireNonNull(tracer);
        this.componentName = requireNonNull(componentName);
        this.injector = (spanContext, headers) -> tracer.inject(spanContext, format, new HttpHeadersToTextMap(headers));
        this.extractor = headers -> tracer.extract(format, new HttpHeadersToTextMap(headers));
    }

    static final class HttpHeadersB3KeyValueFormatter extends B3KeyValueFormatter<HttpHeaders> {

        private static final InMemorySpanContextFormat<HttpHeaders> FORMATTER_VALIDATION =
                new HttpHeadersB3KeyValueFormatter(true);
        private static final InMemorySpanContextFormat<HttpHeaders> FORMATTER_NO_VALIDATION =
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

    private static final class HttpHeadersToTextMap implements TextMap {
        private final HttpHeaders headers;

        private HttpHeadersToTextMap(final HttpHeaders headers) {
            this.headers = requireNonNull(headers);
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return new Iterator<Map.Entry<String, String>>() {
                private final Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();

                @Override
                public boolean hasNext() {
                    return itr.hasNext();
                }

                @Override
                public Map.Entry<String, String> next() {
                    return new Map.Entry<String, String>() {
                        private final Map.Entry<CharSequence, CharSequence> next = itr.next();
                        @Override
                        public String getKey() {
                            return Objects.toString(next.getKey());
                        }

                        @Override
                        public String getValue() {
                            return Objects.toString(next.getValue());
                        }

                        @Override
                        public String setValue(final String value) {
                            return Objects.toString(next.setValue(value));
                        }
                    };
                }

                @Override
                public void remove() {
                    itr.remove();
                }
            };
        }

        @Override
        public void put(final String s, final String s1) {
            headers.set(s, s1);
        }
    }
}
