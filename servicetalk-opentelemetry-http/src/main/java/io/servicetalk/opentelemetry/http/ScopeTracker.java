/*
 * Copyright © 2022-2023 Apple Inc. and the ServiceTalk project authors
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
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ScopeTracker implements TerminalSignalConsumer {

    private final Context context;
    private final HttpRequestMetaData requestMetaData;
    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    @Nullable
    private HttpResponseMetaData responseMetaData;

    ScopeTracker(Context context, StreamingHttpRequest requestMetaData,
                 Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter) {
        this.context = requireNonNull(context);
        this.requestMetaData = requireNonNull(requestMetaData);
        this.instrumenter = requireNonNull(instrumenter);
    }

    void onResponseMeta(final HttpResponseMetaData metaData) {
        this.responseMetaData = metaData;
    }

    @Override
    public void onComplete() {
        assert responseMetaData != null : "can't have succeeded without capturing metadata first";
        instrumenter.end(context, requestMetaData, responseMetaData, null);
    }

    @Override
    public void onError(final Throwable throwable) {
        instrumenter.end(context, requestMetaData, responseMetaData, throwable);
    }

    @Override
    public void cancel() {
        instrumenter.end(context, requestMetaData, responseMetaData, CancelledRequestException.INSTANCE);
    }

    Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle) {
        return responseSingle.liftSync(new BeforeFinallyHttpOperator(this))
                // BeforeFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
                // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed before
                // completion of Meta. So in order for downstream operators to get a consistent view of the data
                // path beforeOnSuccess() needs to be applied last.
                .beforeOnSuccess(this::onResponseMeta);
    }

    private static final class CancelledRequestException extends Exception {
        private static final long serialVersionUID = 6357694797622093267L;
        static final CancelledRequestException INSTANCE = new CancelledRequestException();

        CancelledRequestException() {
            super("canceled", null, false, false);
        }
    }
}

