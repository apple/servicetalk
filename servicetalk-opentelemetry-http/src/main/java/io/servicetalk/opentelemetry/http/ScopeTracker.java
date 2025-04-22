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
import io.servicetalk.http.utils.AfterFinallyHttpOperator;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class ScopeTracker implements TerminalSignalConsumer {

    private static final AtomicIntegerFieldUpdater<ScopeTracker> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(ScopeTracker.class, "state");

    private static final int IDLE = 0;
    private static final int REQUEST_COMPLETE = 1;
    private static final int RESPONSE_COMPLETE = 2;
    private static final int FINISHED = 3;

    private final Context context;
    private final HttpRequestMetaData requestMetaData;
    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    @Nullable
    private Throwable responseCompleteCause;
    private volatile int state;


    @Nullable
    private HttpResponseMetaData responseMetaData;

    private ScopeTracker(boolean isClient, Context context, StreamingHttpRequest requestMetaData,
                 Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter) {
        this.state = isClient ? REQUEST_COMPLETE : IDLE;
        this.context = requireNonNull(context);
        this.requestMetaData = requireNonNull(requestMetaData);
        this.instrumenter = requireNonNull(instrumenter);
    }

    static ScopeTracker client(Context context, StreamingHttpRequest requestMetaData,
                               Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter) {
        return new ScopeTracker(true, context, requestMetaData, instrumenter);
    }

    static ScopeTracker server(Context context, StreamingHttpRequest request,
                               Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter) {
        ScopeTracker tracker = new ScopeTracker(false, context, request, instrumenter);
        request.transformMessageBody(body -> body.afterFinally(tracker::requestComplete));
        return tracker;
    }

    void onResponseMeta(final HttpResponseMetaData metaData) {
        this.responseMetaData = metaData;
    }

    @Override
    public void onComplete() {
        assert responseMetaData != null : "can't have succeeded without capturing metadata first";
        responseFinished(null);
    }

    @Override
    public void onError(final Throwable throwable) {
        responseFinished(throwable);
    }

    @Override
    public void cancel() {
        responseFinished(CancelledRequestException.INSTANCE);
    }

    private void requestComplete() {
        if (STATE_UPDATER.compareAndSet(this, IDLE, REQUEST_COMPLETE)) {
            // nothing to do: it's up to the response to finish now.
        } else if (STATE_UPDATER.compareAndSet(this, RESPONSE_COMPLETE, FINISHED)) {
            instrumenter.end(context, requestMetaData, responseMetaData, responseCompleteCause);
        }
    }

    private void responseFinished(@Nullable final Throwable throwable) {
        // Technically we can have racing calls for `responseFinished` (say a cancel and an error)
        // but in those cases it's always going to be racy, so it really doesn't matter who 'won'.
        // However, we do need to set the value _before_ we CAS, to ensure we have visibility across
        // threads for if the requestComplete is responsible for ending the span.
        responseCompleteCause = throwable;
        if (STATE_UPDATER.compareAndSet(this, IDLE, RESPONSE_COMPLETE)) {
            // nothing to do: it's up to the request to finish now.
        } else if (STATE_UPDATER.compareAndSet(this, REQUEST_COMPLETE, FINISHED)) {
            instrumenter.end(context, requestMetaData, responseMetaData, throwable);
        }
    }

    Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle) {
        // Note that we use `discardEventsAfterCancel` to make sure the events that the observer sees are the
        // same that the users see.
        return responseSingle.liftSync(new AfterFinallyHttpOperator(this, true))
                // AfterFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
                // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed before
                // completion of Meta. So in order for downstream operators to get a consistent view of the data
                // path beforeOnSuccess() needs to be applied last.
                .beforeOnSuccess(this::onResponseMeta);
    }

    private static final class CancelledRequestException extends Exception {
        private static final long serialVersionUID = 6357694797622093267L;
        static final CancelledRequestException INSTANCE = new CancelledRequestException();

        CancelledRequestException() {
            super("cancelled", null, false, false);
        }
    }
}
