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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ScopeTracker implements TerminalSignalConsumer {

    private final Scope currentScope;
    private final Context context;
    private final StreamingHttpRequest request;
    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    @Nullable
    protected HttpResponseMetaData metaData;

    ScopeTracker(Scope currentScope, Context context, StreamingHttpRequest request,
                 Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter) {
        this.currentScope = requireNonNull(currentScope);
        this.context = requireNonNull(context);
        this.request = requireNonNull(request);
        this.instrumenter = requireNonNull(instrumenter);
    }

    void onResponseMeta(final HttpResponseMetaData metaData) {
        this.metaData = metaData;
    }

    @Override
    public void onComplete() {
        assert metaData != null : "can't have succeeded without capturing metadata first";
        try {
            instrumenter.end(context, request, metaData, null);
        } finally {
            closeAll();
        }
    }

    @Override
    public void onError(final Throwable throwable) {
        try {
            instrumenter.end(context, request, metaData, throwable);
        } finally {
            closeAll();
        }
    }

    @Override
    public void cancel() {
        try {
            instrumenter.end(context, request, metaData, null);
        } finally {
            closeAll();
        }
    }

    Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle) {
        return responseSingle.liftSync(new BeforeFinallyHttpOperator(this))
            // BeforeFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
            // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed before
            // completion of Meta. So in order for downstream operators to get a consistent view of the data
            // path beforeOnSuccess() needs to be applied last.
            .beforeOnSuccess(this::onResponseMeta);
    }

    private void closeAll() {
        currentScope.close();
    }
}
