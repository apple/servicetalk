package io.servicetalk.opentelemetry.http;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.BeforeFinallyHttpOperator;

import javax.annotation.Nullable;

import java.lang.reflect.Parameter;

import static java.util.Objects.requireNonNull;

final class ScopeTrackerV2 implements TerminalSignalConsumer {

    private final Context context;
    private final HttpRequestMetaData requestMetaData;
    private final Instrumenter<HttpRequestMetaData, HttpResponseMetaData> instrumenter;

    @Nullable
    private HttpResponseMetaData responseMetaData;

    @Nullable
    private Throwable responseCompleteCause;
    private boolean responseComplete;
    private boolean requestComplete;


    ScopeTrackerV2(Context context, StreamingHttpRequest requestMetaData,
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
        responseFinished(null);
    }

    @Override
    public void onError(final Throwable throwable) {
        System.err.println("instrumenter.end: onError");
        responseFinished(throwable);
    }

    @Override
    public void cancel() {
        System.err.println("instrumenter.end: cancel");
        responseFinished(CancelledRequestException.INSTANCE);
    }

    Single<StreamingHttpResponse> track(Single<StreamingHttpResponse> responseSingle) {
        return responseSingle.liftSync(new BeforeFinallyHttpOperator(this))
                // BeforeFinallyHttpOperator conditionally outputs a Single<Meta> with a failed
                // Publisher<Data> instead of the real Publisher<Data> in case a cancel signal is observed before
                // completion of Meta. So in order for downstream operators to get a consistent view of the data
                // path beforeOnSuccess() needs to be applied last.
                .beforeOnSuccess(this::onResponseMeta);
    }

    void requestComplete() {
        boolean shouldEnd;
        Throwable throwable;
        synchronized (this) {
            if (requestComplete) {
                return;
            }
            requestComplete = true;
            shouldEnd = responseComplete;
            throwable = responseCompleteCause;
        }
        if (shouldEnd) {
            instrumenter.end(context, requestMetaData, responseMetaData, throwable);
        }
    }

    private void responseFinished(@Nullable final Throwable throwable) {
        boolean shouldEnd;
        synchronized (this) {
            if (responseComplete) {
                return;
            }
            responseComplete = true;
            assert responseCompleteCause == null;
            responseCompleteCause = throwable;
            shouldEnd = requestComplete;
        }
        if (shouldEnd) {
            instrumenter.end(context, requestMetaData, responseMetaData, throwable);
        }
    }

    private static final class CancelledRequestException extends Exception {
        private static final long serialVersionUID = 6357694797622093267L;
        static final CancelledRequestException INSTANCE = new CancelledRequestException();

        CancelledRequestException() {
            super("canceled", null, false, false);
        }
    }
}
