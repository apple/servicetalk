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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestFactory;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.BlockingIterables.singletonBlockingIterable;
import static io.servicetalk.grpc.api.GrpcUtils.initRequest;
import static io.servicetalk.grpc.api.GrpcUtils.validateResponseAndGetPayload;
import static java.util.Objects.requireNonNull;

final class DefaultGrpcClientCallFactory implements GrpcClientCallFactory {
    private final StreamingHttpClient streamingHttpClient;

    DefaultGrpcClientCallFactory(final StreamingHttpClient streamingHttpClient) {
        this.streamingHttpClient = requireNonNull(streamingHttpClient);
    }

    @Override
    public <Req, Resp> ClientCall<Req, Resp>
    newCall(final HttpSerializationProvider serializationProvider,
            final Class<Req> requestClass, final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        HttpClient client = streamingHttpClient.asClient();
        return (metadata, request) -> {
            @Nullable
            GrpcExecutionStrategy strategy = metadata.strategy();
            HttpRequest httpRequest = newAggregatedRequest(metadata, request, client,
                    serializationProvider, requestClass);
            return (strategy == null ? client.request(httpRequest) :
                    client.request(strategy, httpRequest))
                    .map(response -> validateResponseAndGetPayload(response,
                            serializationProvider.deserializerFor(responseClass)));
        };
    }

    @Override
    public <Req, Resp> StreamingClientCall<Req, Resp>
    newStreamingCall(final HttpSerializationProvider serializationProvider, final Class<Req> requestClass,
                     final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        return (metadata, request) -> {
            StreamingHttpRequest httpRequest = streamingHttpClient.post(metadata.path());
            initRequest(httpRequest);
            httpRequest.payloadBody(request.map(DefaultGrpcClientCallFactory::uncheckedCast),
                    serializationProvider.serializerFor(requestClass));
            @Nullable
            GrpcExecutionStrategy strategy = metadata.strategy();
            return (strategy == null ? streamingHttpClient.request(httpRequest) :
                    streamingHttpClient.request(strategy, httpRequest))
                    .flatMapPublisher(response -> validateResponseAndGetPayload(response,
                            serializationProvider.deserializerFor(responseClass)));
        };
    }

    @Override
    public <Req, Resp> RequestStreamingClientCall<Req, Resp>
    newRequestStreamingCall(final HttpSerializationProvider serializationProvider,
                            final Class<Req> requestClass, final Class<Resp> responseClass) {
        StreamingClientCall<Req, Resp> streamingClientCall =
                newStreamingCall(serializationProvider, requestClass, responseClass);
        return (metadata, request) -> streamingClientCall.request(metadata, request).firstOrError();
    }

    @Override
    public <Req, Resp> ResponseStreamingClientCall<Req, Resp>
    newResponseStreamingCall(final HttpSerializationProvider serializationProvider,
                             final Class<Req> requestClass, final Class<Resp> responseClass) {
        StreamingClientCall<Req, Resp> streamingClientCall = newStreamingCall(serializationProvider, requestClass,
                responseClass);
        return (metadata, request) -> streamingClientCall.request(metadata, Publisher.from(request));
    }

    @Override
    public <Req, Resp> BlockingClientCall<Req, Resp>
    newBlockingCall(final HttpSerializationProvider serializationProvider,
                    final Class<Req> requestClass, final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        BlockingHttpClient client = streamingHttpClient.asBlockingClient();
        return (metadata, request) -> {
            @Nullable
            GrpcExecutionStrategy strategy = metadata.strategy();
            HttpRequest httpRequest = newAggregatedRequest(metadata, request, client,
                    serializationProvider, requestClass);
            HttpResponse response;
            response = strategy == null ? client.request(httpRequest) :
                    client.request(strategy, httpRequest);
            return validateResponseAndGetPayload(response,
                    serializationProvider.deserializerFor(responseClass));
        };
    }

    @Override
    public <Req, Resp> BlockingStreamingClientCall<Req, Resp>
    newBlockingStreamingCall(final HttpSerializationProvider serializationProvider,
                             final Class<Req> requestClass, final Class<Resp> responseClass) {
        requireNonNull(serializationProvider);
        requireNonNull(requestClass);
        requireNonNull(responseClass);
        BlockingStreamingHttpClient client = streamingHttpClient.asBlockingStreamingClient();
        return (metadata, request) -> {
            BlockingStreamingHttpRequest httpRequest = client.post(metadata.path());
            initRequest(httpRequest);
            httpRequest.payloadBody(request, serializationProvider.serializerFor(requestClass));
            @Nullable
            GrpcExecutionStrategy strategy = metadata.strategy();
            BlockingStreamingHttpResponse response;
            response = strategy == null ? client.request(httpRequest) :
                    client.request(strategy, httpRequest);
            return response.payloadBody(serializationProvider.deserializerFor(responseClass));
        };
    }

    @Override
    public <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp>
    newBlockingRequestStreamingCall(final HttpSerializationProvider serializationProvider,
                                    final Class<Req> requestClass, final Class<Resp> responseClass) {
        BlockingStreamingClientCall<Req, Resp> streamingClientCall = newBlockingStreamingCall(serializationProvider,
                requestClass, responseClass);
        return (metadata, request) -> requireNonNull(streamingClientCall.request(metadata, request).iterator().next());
    }

    @Override
    public <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp>
    newBlockingResponseStreamingCall(final HttpSerializationProvider serializationProvider,
                                     final Class<Req> requestClass, final Class<Resp> responseClass) {
        BlockingStreamingClientCall<Req, Resp> streamingClientCall =
                newBlockingStreamingCall(serializationProvider, requestClass, responseClass);
        return (metadata, request) -> streamingClientCall.request(metadata, singletonBlockingIterable(request));
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

    @SuppressWarnings("unchecked")
    static <T> T uncheckedCast(Object o) {
        return (T) o;
    }

    private <Req> HttpRequest newAggregatedRequest(final GrpcClientMetadata metadata, final Req rawReq,
                                                   final HttpRequestFactory requestFactory,
                                                   final HttpSerializationProvider serializationProvider,
                                                   final Class<Req> requestClass) {
        HttpRequest httpRequest = requestFactory.post(metadata.path());
        initRequest(httpRequest);
        return httpRequest.payloadBody(uncheckedCast(rawReq), serializationProvider.serializerFor(requestClass));
    }
}
