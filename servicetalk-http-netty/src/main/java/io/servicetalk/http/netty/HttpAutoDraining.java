package io.servicetalk.http.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import javax.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

public final class HttpAutoDraining implements StreamingHttpServiceFilterFactory {

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return offloadNone();
    }

    @Override
    public StreamingHttpServiceFilter create(StreamingHttpService service) {
        return new DrainingStreamingHttpServiceFilter(service);
    }

    private static final class DrainingStreamingHttpServiceFilter extends StreamingHttpServiceFilter {

        DrainingStreamingHttpServiceFilter(StreamingHttpService service) {
            super(service);
        }

        @Override
        public Single<StreamingHttpResponse> handle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                    StreamingHttpResponseFactory responseFactory) {
            return defer(() -> doHandle(ctx, request, responseFactory).shareContextOnSubscribe());
        }

        private Single<StreamingHttpResponse> doHandle(HttpServiceContext ctx, StreamingHttpRequest request,
                                                    StreamingHttpResponseFactory responseFactory) {
            final AtomicBoolean payloadSubscribed = new AtomicBoolean();
            request.payloadBody().beforeOnSubscribe(ignored -> payloadSubscribed.set(true));
            return delegate().handle(ctx, request, responseFactory).liftSync(subscriber ->
                    new SingleSource.Subscriber<StreamingHttpResponse>() {

                        // TODO: this could be a field with atomic field updater
                        final AtomicBoolean cancellation = new AtomicBoolean();
                        @Override
                        public void onSubscribe(Cancellable cancellable) {
                            subscriber.onSubscribe(() -> {
                                try {
                                    if (cancellation.compareAndSet(false, true)) {
                                        maybeCancelMessageBody(payloadSubscribed, request.messageBody());
                                    }
                                } finally {
                                    cancellable.cancel();
                                }
                            });
                        }

                        @Override
                        public void onSuccess(@Nullable StreamingHttpResponse result) {
                            assert result != null;

                            if (cancellation.compareAndSet(false, true)) {
                                try {
                                    result.transformMessageBody(body ->
                                        body.afterFinally(new TerminalSignalConsumer() {
                                            @Override
                                            public void onComplete() {
                                                if (payloadSubscribed.compareAndSet(false, true)) {
                                                    request.payloadBody().ignoreElements()
                                                            .shareContextOnSubscribe().subscribe();
                                                }
                                            }

                                            @Override
                                            public void onError(Throwable throwable) {
                                                cancel();
                                            }

                                            @Override
                                            public void cancel() {
                                                maybeCancelMessageBody(payloadSubscribed, request.messageBody());
                                            }
                                        }));
                                } finally {
                                    subscriber.onSuccess(result);
                                }
                            } else {
                                // The response was cancelled so let's swallow it.
                                cancelMessageBody(result.payloadBody());
                                maybeCancelMessageBody(payloadSubscribed, request.payloadBody());
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            try {
                                maybeCancelMessageBody(payloadSubscribed, request.messageBody());
                            } finally {
                                subscriber.onError(t);
                            }
                        }
                    }
            );
        }

        private void maybeCancelMessageBody(AtomicBoolean payloadSubscribed, Publisher<?> messageBody) {
            if (payloadSubscribed.compareAndSet(false, true)) {
                cancelMessageBody(messageBody);
            }
        }

        private void cancelMessageBody(Publisher<?> messageBody) {
            toSource(messageBody.shareContextOnSubscribe()).subscribe(CancelImmediatelySubscriber.INSTANCE);
        }
    }
}
