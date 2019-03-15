/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.DoBeforeFinallyOnHttpResponseOperator;
import io.servicetalk.opentracing.inmemory.api.InMemoryTraceStateFormat;

import io.opentracing.Scope;
import io.opentracing.Tracer;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.opentracing.tag.Tags.ERROR;
import static io.opentracing.tag.Tags.HTTP_STATUS;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.opentracing.http.TracingHttpHeadersFormatter.traceStateFormatter;
import static java.util.Objects.requireNonNull;

abstract class AbstractTracingHttpFilter {

    protected final Tracer tracer;
    protected final String componentName;
    protected final InMemoryTraceStateFormat<HttpHeaders> formatter;

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

    /**
     * Determine if a {@link HttpResponseMetaData} should be considered an error from a tracing perspective.
     * @param metaData The {@link HttpResponseMetaData} to test.
     * @return {@code true} if the {@link HttpResponseMetaData} should be considered an error for tracing.
     */
    protected boolean isError(final HttpResponseMetaData metaData) {
        return metaData.status().statusClass().equals(SERVER_ERROR_5XX);
    }

    abstract ScopeTracker newTracker();

    abstract class ScopeTracker implements TerminalSignalConsumer {

        @Nullable
        private Scope currentScope;
        @Nullable
        private HttpResponseMetaData metaData;

        @Nullable
        Scope currentScope() {
            return currentScope;
        }

        abstract Scope newScope(HttpRequestMetaData requestMetaData);

        Single<StreamingHttpResponse> prepareScopeAndRequestOrFailEarly(
                final HttpRequestMetaData requestMetaData,
                final Supplier<Single<StreamingHttpResponse>> singleSupplier) {
            try {
                currentScope = newScope(requestMetaData);
                return requireNonNull(singleSupplier.get());
            } catch (Throwable t) {
                if (currentScope != null) {
                    onError(t);
                }
                return Single.error(t);
            }
        }

        void onResponseMeta(final HttpResponseMetaData metaData) {
            assert currentScope != null : "never null after preparation";
            this.metaData = metaData;
        }

        @Override
        public void onComplete() {
            assert metaData != null : "can't have succeeded without capturing metadata first";
            assert currentScope != null : "never null after preparation";
            tagStatusCode();
            try {
                if (isError(metaData)) {
                    ERROR.set(currentScope.span(), true);
                }
            } finally {
                currentScope.close();
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            assert currentScope != null : "never null after preparation";
            tagStatusCode();
            ERROR.set(currentScope.span(), true);
            currentScope.close();
        }

        @Override
        public void onCancel() {
            assert currentScope != null : "never null after preparation";
            tagStatusCode();
            ERROR.set(currentScope.span(), true);
            currentScope.close();
        }

        private void tagStatusCode() {
            assert currentScope != null : "never null after preparation";
            if (metaData != null) {
                HTTP_STATUS.set(currentScope.span(), metaData.status().code());
            }
        }
    }

    final Single<StreamingHttpResponse> trackRequest(final StreamingHttpRequest request,
                                                     final Supplier<Single<StreamingHttpResponse>> singleSupplier) {
        return Single.defer(() -> {
            final ScopeTracker scopeTracker = newTracker();
            return scopeTracker.prepareScopeAndRequestOrFailEarly(request, singleSupplier)
                    .liftSynchronous(new DoBeforeFinallyOnHttpResponseOperator(scopeTracker))
                    // DoBeforeFinallyOnHttpResponseOperator influences how the nested source will be emitted, hence
                    // doBeforeSuccess() is applied after to ensure we have a consistent view of the data path
                    .doBeforeSuccess(scopeTracker::onResponseMeta);
        }).subscribeShareContext();
    }
}
