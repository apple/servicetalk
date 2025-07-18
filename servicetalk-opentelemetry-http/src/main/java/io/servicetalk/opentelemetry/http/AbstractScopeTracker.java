/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionInfo;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

abstract class AbstractScopeTracker<T> implements TerminalSignalConsumer {

    private static final AtomicIntegerFieldUpdater<AbstractScopeTracker> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractScopeTracker.class, "state");

    protected static final int IDLE = 0;
    protected static final int REQUEST_COMPLETE = 1;
    protected static final int RESPONSE_COMPLETE = 2;
    protected static final int FINISHED = 3;

    protected final Context context;
    protected final RequestInfo requestInfo;
    protected final Instrumenter<RequestInfo, T> instrumenter;

    @Nullable
    protected Throwable responseCompleteCause;
    private volatile int state;

    protected AbstractScopeTracker(boolean isClient, Context context, StreamingHttpRequest request,
                                   @Nullable ConnectionInfo connectionInfo,
                                   Instrumenter<RequestInfo, T> instrumenter) {
        this.state = isClient ? REQUEST_COMPLETE : IDLE;
        this.context = requireNonNull(context);
        this.requestInfo = new RequestInfo(request, connectionInfo);
        this.instrumenter = requireNonNull(instrumenter);
    }

    @Override
    public final void onComplete() {
        responseFinished(null);
    }

    @Override
    public final void onError(final Throwable throwable) {
        responseFinished(throwable);
    }

    @Override
    public final void cancel() {
        responseFinished(CancelledRequestException.INSTANCE);
    }

    protected final void requestComplete() {
        if (STATE_UPDATER.compareAndSet(this, IDLE, REQUEST_COMPLETE)) {
            // nothing to do: it's up to the response to finish now.
        } else if (STATE_UPDATER.compareAndSet(this, RESPONSE_COMPLETE, FINISHED)) {
            finishSpan(responseCompleteCause);
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
            finishSpan(throwable);
        }
    }

    protected abstract void finishSpan(@Nullable Throwable error);

    /**
     * Track a response Single, applying the appropriate monitoring and lifecycle management.
     * <p>
     * Implementations should set up response metadata capture and apply the necessary
     * operators to track the response lifecycle.
     *
     * @param responseSingle the response Single to track
     * @return the tracked response Single
     */
    public abstract Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle);

    private static final class CancelledRequestException extends Exception {
        private static final long serialVersionUID = 6357694797622093267L;
        static final CancelledRequestException INSTANCE = new CancelledRequestException();

        CancelledRequestException() {
            super("cancelled", null, false, false);
        }
    }
}
