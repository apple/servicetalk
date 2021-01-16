/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.GracefulAutoCloseable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.ConnectablePayloadWriter;
import io.servicetalk.concurrent.internal.ConcurrentTerminalSubscriber;
import io.servicetalk.grpc.api.GrpcRoutes.BlockingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.BlockingStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.RequestStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.ResponseStreamingRoute;
import io.servicetalk.grpc.api.GrpcRoutes.Route;
import io.servicetalk.grpc.api.GrpcRoutes.StreamingRoute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.util.Objects.requireNonNull;

final class GrpcRouteConversions {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcRouteConversions.class);

    private GrpcRouteConversions() {
        // No instance
    }

    static <Req, Resp> Route<Req, Resp> toRoute(
            final StreamingRoute<Req, Resp> original) {
        requireNonNull(original);
        return new Route<Req, Resp>() {
            @Override
            public Single<Resp> handle(final GrpcServiceContext ctx, final Req request) {
                return original.handle(ctx, from(request)).firstOrError();
            }

            @Override
            public Completable closeAsync() {
                return original.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return original.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> Route<Req, Resp> toRoute(
            final BlockingStreamingRoute<Req, Resp> original) {
        return toRoute(toStreaming(original));
    }

    static <Req, Resp> Route<Req, Resp> toRoute(
            final BlockingRoute<Req, Resp> original) {
        return toRoute(toStreaming(original));
    }

    static <Req, Resp> StreamingRoute<Req, Resp> toStreaming(
            final Route<Req, Resp> original) {
        requireNonNull(original);
        return new StreamingRoute<Req, Resp>() {
            @Override
            public Publisher<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                return request.firstOrError().flatMapPublisher(req -> original.handle(ctx, req).toPublisher());
            }

            @Override
            public Completable closeAsync() {
                return original.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return original.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> StreamingRoute<Req, Resp> toStreaming(
            final BlockingStreamingRoute<Req, Resp> original) {
        requireNonNull(original);
        return new StreamingRoute<Req, Resp>() {
            private final AsyncCloseable closeable = toAsyncCloseable(original);
            @Override
            public Publisher<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                return new Publisher<Resp>() {
                    @Override
                    protected void handleSubscribe(final Subscriber<? super Resp> subscriber) {
                        final ConnectablePayloadWriter<Resp> connectablePayloadWriter =
                                new ConnectablePayloadWriter<>();
                        final Publisher<Resp> pub = connectablePayloadWriter.connect();
                        final ConcurrentTerminalSubscriber<? super Resp> concurrentTerminalSubscriber =
                                new ConcurrentTerminalSubscriber<>(subscriber, false);
                        toSource(pub).subscribe(concurrentTerminalSubscriber);
                        final GrpcPayloadWriter<Resp> grpcPayloadWriter = new GrpcPayloadWriter<Resp>() {
                            @Override
                            public void write(final Resp resp) throws IOException {
                                connectablePayloadWriter.write(resp);
                            }

                            @Override
                            public void close() throws IOException {
                                connectablePayloadWriter.close();
                            }

                            @Override
                            public void close(final Throwable cause) throws IOException {
                                connectablePayloadWriter.close(cause);
                            }

                            @Override
                            public void flush() throws IOException {
                                connectablePayloadWriter.flush();
                            }
                        };
                        try {
                            original.handle(ctx, request.toIterable(), grpcPayloadWriter);
                        } catch (Throwable t) {
                            concurrentTerminalSubscriber.onError(t);
                        } finally {
                            try {
                                grpcPayloadWriter.close();
                            } catch (IOException e) {
                                if (!concurrentTerminalSubscriber.processOnError(e)) {
                                    LOGGER.error("Failed to close GrpcPayloadWriter", e);
                                }
                            }
                        }
                    }
                };
            }

            @Override
            public Completable closeAsync() {
                return closeable.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return closeable.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> StreamingRoute<Req, Resp> toStreaming(
            final BlockingRoute<Req, Resp> original) {
        requireNonNull(original);
        return new StreamingRoute<Req, Resp>() {
            private final AsyncCloseable closeable = toAsyncCloseable(original);
            @Override
            public Publisher<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                return request.firstOrError().map(req -> {
                    try {
                        return original.handle(ctx, req);
                    } catch (Exception e) {
                        return throwException(e);
                    }
                }).toPublisher();
            }

            @Override
            public Completable closeAsync() {
                return closeable.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return closeable.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> RequestStreamingRoute<Req, Resp>
    toRequestStreamingRoute(final Route<Req, Resp> original) {
        requireNonNull(original);
        return new RequestStreamingRoute<Req, Resp>() {
            @Override
            public Single<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                return request.firstOrError().flatMap(req -> original.handle(ctx, req));
            }

            @Override
            public Completable closeAsync() {
                return original.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return original.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> RequestStreamingRoute<Req, Resp>
    toRequestStreamingRoute(final StreamingRoute<Req, Resp> original) {
        requireNonNull(original);
        return new RequestStreamingRoute<Req, Resp>() {
            @Override
            public Single<Resp> handle(final GrpcServiceContext ctx, final Publisher<Req> request) {
                return original.handle(ctx, request).firstOrError();
            }

            @Override
            public Completable closeAsync() {
                return original.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return original.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> RequestStreamingRoute<Req, Resp>
    toRequestStreamingRoute(final BlockingStreamingRoute<Req, Resp> original) {
        return toRequestStreamingRoute(toStreaming(original));
    }

    static <Req, Resp> RequestStreamingRoute<Req, Resp>
    toRequestStreamingRoute(final BlockingRoute<Req, Resp> original) {
        return toRequestStreamingRoute(toStreaming(original));
    }

    static <Req, Resp> ResponseStreamingRoute<Req, Resp>
    toResponseStreamingRoute(final Route<Req, Resp> original) {
        requireNonNull(original);
        return new ResponseStreamingRoute<Req, Resp>() {
            @Override
            public Publisher<Resp> handle(final GrpcServiceContext ctx, final Req request) {
                return original.handle(ctx, request).toPublisher();
            }

            @Override
            public Completable closeAsync() {
                return original.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return original.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> ResponseStreamingRoute<Req, Resp>
    toResponseStreamingRoute(final StreamingRoute<Req, Resp> original) {
        requireNonNull(original);
        return new ResponseStreamingRoute<Req, Resp>() {
            @Override
            public Publisher<Resp> handle(final GrpcServiceContext ctx, final Req request) {
                return original.handle(ctx, from(request));
            }

            @Override
            public Completable closeAsync() {
                return original.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return original.closeAsyncGracefully();
            }
        };
    }

    static <Req, Resp> ResponseStreamingRoute<Req, Resp>
    toResponseStreamingRoute(final BlockingStreamingRoute<Req, Resp> original) {
        return toResponseStreamingRoute(toStreaming(original));
    }

    static <Req, Resp> ResponseStreamingRoute<Req, Resp>
    toResponseStreamingRoute(final BlockingRoute<Req, Resp> original) {
        return toResponseStreamingRoute(toStreaming(original));
    }

    static AsyncCloseable toAsyncCloseable(final GracefulAutoCloseable original) {
        return AsyncCloseables.toAsyncCloseable(graceful -> new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                try {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(subscriber, cause);
                    return;
                }

                try {
                    if (graceful) {
                        original.closeGracefully();
                    } else {
                        original.close();
                    }
                    subscriber.onComplete();
                } catch (Throwable t) {
                    subscriber.onError(t);
                }
            }
        });
    }
}
