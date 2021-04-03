/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestFactory;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.BlockingIterables.singletonBlockingIterable;
import static io.servicetalk.grpc.api.GrpcUtils.initRequest;
import static io.servicetalk.grpc.api.GrpcUtils.readGrpcMessageEncoding;
import static io.servicetalk.grpc.api.GrpcUtils.uncheckedCast;
import static io.servicetalk.grpc.api.GrpcUtils.validateResponseAndGetPayload;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcClientCallFactory implements GrpcClientCallFactory {
    /**
     * gRPC timeout is stored in context as a deadline so that when propagated to a new request the remaining time to be
     * included in the request can be calculated.
     */
    static final AsyncContextMap.Key<Instant> GRPC_DEADLINE_KEY = AsyncContextMap.Key.newKey("grpc-deadline");

    private final StreamingHttpClient streamingHttpClient;
    private final GrpcExecutionContext executionContext;
    @Nullable
    private final Duration defaultTimeout;

    DefaultGrpcClientCallFactory(final StreamingHttpClient streamingHttpClient,
                                 @Nullable final Duration defaultTimeout) {
        this.streamingHttpClient = requireNonNull(streamingHttpClient);
        executionContext = new DefaultGrpcExecutionContext(streamingHttpClient.executionContext());
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public <Req, Resp> ClientCall<Req, Resp>
    newCall(final GrpcSerializationProvider serializationProvider,
            final Class<Req> requestClass, final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        final HttpClient client = streamingHttpClient.asClient();
        final List<ContentCodec> supportedCodings = serializationProvider.supportedMessageCodings();
        return (metadata, request) -> {
            final HttpRequest httpRequest = newAggregatedRequest(metadata, request, client,
                    serializationProvider, supportedCodings, requestClass);
            @Nullable
            final GrpcExecutionStrategy strategy = metadata.strategy();
            return (strategy == null ? client.request(httpRequest) : client.request(strategy, httpRequest))
                    .map(response -> validateResponseAndGetPayload(response, serializationProvider.deserializerFor(
                                    readGrpcMessageEncoding(response, supportedCodings), responseClass)))
                    .onErrorResume(t -> Single.failed(toGrpcException(t)));
        };
    }

    private static GrpcStatusException toGrpcException(Throwable cause) {

        if (cause instanceof GrpcStatusException) {
            return (GrpcStatusException) cause;
        }

        GrpcStatusCode statusCode;
        if (cause instanceof CancellationException) {
            // local cancel
            statusCode = GrpcStatusCode.CANCELLED;
        } else if (cause instanceof TimeoutException) {
            // local timeout
            statusCode = GrpcStatusCode.DEADLINE_EXCEEDED;
        } else {
            // something else
            statusCode = GrpcStatusCode.UNKNOWN;
        }

        return new GrpcStatusException(new GrpcStatus(statusCode, cause), () -> null);
    }

    @Override
    public <Req, Resp> StreamingClientCall<Req, Resp>
    newStreamingCall(final GrpcSerializationProvider serializationProvider, final Class<Req> requestClass,
                     final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        final List<ContentCodec> supportedCodings = serializationProvider.supportedMessageCodings();
        return (metadata, request) -> {
            final StreamingHttpRequest httpRequest = streamingHttpClient.post(metadata.path());
            initRequest(httpRequest, supportedCodings, timeoutForRequest(metadata.timeout()));
            httpRequest.payloadBody(request.map(GrpcUtils::uncheckedCast),
                    serializationProvider.serializerFor(metadata.requestEncoding(), requestClass));
            @Nullable
            final GrpcExecutionStrategy strategy = metadata.strategy();
            return (strategy == null ? streamingHttpClient.request(httpRequest) :
                    streamingHttpClient.request(strategy, httpRequest))
                    .flatMapPublisher(response -> validateResponseAndGetPayload(response,
                            serializationProvider.deserializerFor(
                                    readGrpcMessageEncoding(response, supportedCodings), responseClass)))
                    .onErrorResume(t -> Publisher.failed(toGrpcException(t)));
        };
    }

    @Override
    public <Req, Resp> RequestStreamingClientCall<Req, Resp>
    newRequestStreamingCall(final GrpcSerializationProvider serializationProvider,
                            final Class<Req> requestClass, final Class<Resp> responseClass) {
        final StreamingClientCall<Req, Resp> streamingClientCall =
                newStreamingCall(serializationProvider, requestClass, responseClass);
        return (metadata, request) -> streamingClientCall.request(metadata, request).firstOrError();
    }

    @Override
    public <Req, Resp> ResponseStreamingClientCall<Req, Resp>
    newResponseStreamingCall(final GrpcSerializationProvider serializationProvider,
                             final Class<Req> requestClass, final Class<Resp> responseClass) {
        final StreamingClientCall<Req, Resp> streamingClientCall =
                newStreamingCall(serializationProvider, requestClass, responseClass);
        return (metadata, request) -> streamingClientCall.request(metadata, Publisher.from(request));
    }

    @Override
    public <Req, Resp> BlockingClientCall<Req, Resp>
    newBlockingCall(final GrpcSerializationProvider serializationProvider,
                    final Class<Req> requestClass, final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        final List<ContentCodec> supportedCodings = serializationProvider.supportedMessageCodings();
        final BlockingHttpClient client = streamingHttpClient.asBlockingClient();
        return (metadata, request) -> {
            final HttpRequest httpRequest = newAggregatedRequest(metadata, request, client,
                    serializationProvider, supportedCodings, requestClass);
            @Nullable
            final GrpcExecutionStrategy strategy = metadata.strategy();
            try {
                final HttpResponse response = strategy == null ? client.request(httpRequest) :
                        client.request(strategy, httpRequest);
                return validateResponseAndGetPayload(response,
                        serializationProvider.deserializerFor(
                                readGrpcMessageEncoding(response, supportedCodings), responseClass));
            } catch (Exception all) {
                throw toGrpcException(all);
            }
        };
    }

    @Override
    public <Req, Resp> BlockingStreamingClientCall<Req, Resp>
    newBlockingStreamingCall(final GrpcSerializationProvider serializationProvider,
                             final Class<Req> requestClass, final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        final BlockingStreamingHttpClient client = streamingHttpClient.asBlockingStreamingClient();
        final List<ContentCodec> supportedCodings = serializationProvider.supportedMessageCodings();
        return (metadata, request) -> {
            final BlockingStreamingHttpRequest httpRequest = client.post(metadata.path());
            initRequest(httpRequest, supportedCodings, timeoutForRequest(metadata.timeout()));
            httpRequest.payloadBody(request, serializationProvider
                    .serializerFor(metadata.requestEncoding(), requestClass));
            @Nullable
            final GrpcExecutionStrategy strategy = metadata.strategy();
            try {
                final BlockingStreamingHttpResponse response = strategy == null ? client.request(httpRequest) :
                        client.request(strategy, httpRequest);
                return validateResponseAndGetPayload(response.toStreamingResponse(),
                        serializationProvider.deserializerFor(
                                readGrpcMessageEncoding(response, supportedCodings), responseClass))
                        .onErrorResume(t -> Publisher.failed(toGrpcException(t))).toIterable();
            } catch (Exception all) {
                throw toGrpcException(all);
            }
        };
    }

    @Override
    public <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp>
    newBlockingRequestStreamingCall(final GrpcSerializationProvider serializationProvider,
                                    final Class<Req> requestClass, final Class<Resp> responseClass) {
        final BlockingStreamingClientCall<Req, Resp> streamingClientCall =
                newBlockingStreamingCall(serializationProvider, requestClass, responseClass);
        return (metadata, request) -> {
            try (BlockingIterator<Resp> iterator = streamingClientCall.request(metadata, request).iterator()) {
                final Resp firstItem = iterator.next();
                assert firstItem != null;
                if (iterator.hasNext()) {
                    iterator.next(); // Consume the next item to make sure it's not a TerminalNotification with an error
                    throw new IllegalArgumentException("More than one response message received");
                }
                return firstItem;
            }
        };
    }

    @Override
    public <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp>
    newBlockingResponseStreamingCall(final GrpcSerializationProvider serializationProvider,
                                     final Class<Req> requestClass, final Class<Resp> responseClass) {
        final BlockingStreamingClientCall<Req, Resp> streamingClientCall =
                newBlockingStreamingCall(serializationProvider, requestClass, responseClass);
        return (metadata, request) -> streamingClientCall.request(metadata, singletonBlockingIterable(request));
    }

    @Override
    public GrpcExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public Completable closeAsync() {
        return streamingHttpClient.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return streamingHttpClient.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return streamingHttpClient.onClose();
    }

    private <Req> HttpRequest newAggregatedRequest(final GrpcClientMetadata metadata, final Req rawReq,
                                                          final HttpRequestFactory requestFactory,
                                                          final GrpcSerializationProvider serializationProvider,
                                                          final List<ContentCodec> supportedCodings,
                                                          final Class<Req> requestClass) {
        final HttpRequest httpRequest = requestFactory.post(metadata.path());
        initRequest(httpRequest, supportedCodings, timeoutForRequest(metadata.timeout()));
        return httpRequest.payloadBody(uncheckedCast(rawReq),
                serializationProvider.serializerFor(metadata.requestEncoding(), requestClass));
    }

    /**
     * Determines the timeout for a new request using three potential sources; the client default, the client metadata
     * or the async context. The lowest timeout will be used.
     *
     * @param metaDataTimeout the timeout specified in client metadata
     * @return The timeout {@link Duration} or null for no timeout
     */
    private Duration timeoutForRequest(Duration metaDataTimeout) {
        Duration timeout = null != defaultTimeout && defaultTimeout.compareTo(metaDataTimeout) < 0 ?
                defaultTimeout : metaDataTimeout;

        Instant deadline = AsyncContext.get(GRPC_DEADLINE_KEY);
        Duration contextTimeout = null != deadline ? Duration.between(Instant.now(), deadline) : timeout;

        return contextTimeout != timeout && contextTimeout.compareTo(timeout) < 0 ? contextTimeout : timeout;
    }
}
