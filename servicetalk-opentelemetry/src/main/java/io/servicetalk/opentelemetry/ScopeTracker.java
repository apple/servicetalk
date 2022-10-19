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

package io.servicetalk.opentelemetry;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static java.util.Objects.requireNonNull;

class ScopeTracker implements TerminalSignalConsumer {

    private final Scope currentScope;
    private final Span span;

    @Nullable
    protected HttpResponseMetaData metaData;

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
                span.setStatus(StatusCode.ERROR);
            }
        } finally {
            closeAll();
        }
    }

    @Override
    public void onError(final Throwable throwable) {
        try {
            tagStatusCode();
            span.setStatus(StatusCode.ERROR);
        } finally {
            closeAll();
        }
    }

    @Override
    public void cancel() {
        try {
            tagStatusCode();
            span.setStatus(StatusCode.ERROR);
        } finally {
            closeAll();
        }
    }

    /**
     * Determine if a {@link HttpResponseMetaData} should be considered an error from a tracing perspective.
     *
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

    protected void tagStatusCode() {
        if (metaData != null) {
            ResponseTagExtractor.INSTANCE.extractTo(metaData, span::setAttribute);
        }
    }

    private void closeAll() {
        try {
            currentScope.close();
        } finally {
            span.end();
        }
    }

    final Span getSpan() {
        return span;
    }
}
