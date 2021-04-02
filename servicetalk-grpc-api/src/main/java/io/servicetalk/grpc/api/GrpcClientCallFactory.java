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

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpClient;

import java.time.Duration;
import javax.annotation.Nullable;

/**
 * A factory to create <a href="https://www.grpc.io">gRPC</a> client call objects for different
 * programming models.
 */
public interface GrpcClientCallFactory extends ListenableAsyncCloseable {

    /**
     * Creates a new {@link ClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link ClientCall}.
     */
    <Req, Resp> ClientCall<Req, Resp>
    newCall(GrpcSerializationProvider serializationProvider,
            Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Creates a new {@link StreamingClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link StreamingClientCall}.
     */
    <Req, Resp> StreamingClientCall<Req, Resp>
    newStreamingCall(GrpcSerializationProvider serializationProvider, Class<Req> requestClass,
                     Class<Resp> responseClass);

    /**
     * Creates a new {@link RequestStreamingClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link RequestStreamingClientCall}.
     */
    <Req, Resp> RequestStreamingClientCall<Req, Resp>
    newRequestStreamingCall(GrpcSerializationProvider serializationProvider, Class<Req> requestClass,
                            Class<Resp> responseClass);

    /**
     * Creates a new {@link ResponseStreamingClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link ResponseStreamingClientCall}.
     */
    <Req, Resp> ResponseStreamingClientCall<Req, Resp>
    newResponseStreamingCall(GrpcSerializationProvider serializationProvider, Class<Req> requestClass,
                             Class<Resp> responseClass);

    /**
     * Creates a new {@link BlockingClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingClientCall}.
     */
    <Req, Resp> BlockingClientCall<Req, Resp>
    newBlockingCall(GrpcSerializationProvider serializationProvider,
                    Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Creates a new {@link BlockingStreamingClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingStreamingClientCall}.
     */
    <Req, Resp> BlockingStreamingClientCall<Req, Resp>
    newBlockingStreamingCall(GrpcSerializationProvider serializationProvider, Class<Req> requestClass,
                             Class<Resp> responseClass);

    /**
     * Creates a new {@link BlockingRequestStreamingClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingRequestStreamingClientCall}.
     */
    <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp>
    newBlockingRequestStreamingCall(GrpcSerializationProvider serializationProvider, Class<Req> requestClass,
                                    Class<Resp> responseClass);

    /**
     * Creates a new {@link BlockingResponseStreamingClientCall}.
     *
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingResponseStreamingClientCall}.
     */
    <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp>
    newBlockingResponseStreamingCall(GrpcSerializationProvider serializationProvider, Class<Req> requestClass,
                                     Class<Resp> responseClass);

    /**
     * Get the {@link GrpcExecutionContext} used during construction of this object.
     * <p>
     * Note that the {@link GrpcExecutionContext#ioExecutor()} will not necessarily be associated with a specific thread
     * unless that was how this object was built.
     *
     * @return the {@link GrpcExecutionContext} used during construction of this object.
     */
    GrpcExecutionContext executionContext();

    /**
     * Creates a new {@link GrpcClientCallFactory} using the passed {@link StreamingHttpClient}.
     *
     * @param httpClient {@link StreamingHttpClient} to use. The returned {@link GrpcClientCallFactory} will own the
     * lifecycle of this {@link StreamingHttpClient}.
     * @param defaultTimeout {@link Duration} of default timeout or null for no timeout
     * @return A new {@link GrpcClientCallFactory}.
     */
    static GrpcClientCallFactory from(StreamingHttpClient httpClient, @Nullable Duration defaultTimeout) {
        return new DefaultGrpcClientCallFactory(httpClient, defaultTimeout);
    }

    /**
     * An abstraction to make asynchronous client calls.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface ClientCall<Req, Resp> {

        /**
         * Sends the passed {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link Req} to send.
         * @return {@link Single} containing the response.
         */
        Single<Resp> request(GrpcClientMetadata metadata, Req request);
    }

    /**
     * An abstraction to make asynchronous bi-directional streaming client calls.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface StreamingClientCall<Req, Resp> {

        /**
         * Sends the passed {@link Publisher} of {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link Publisher} of {@link Req} to send.
         * @return {@link Publisher} containing the streaming response.
         */
        Publisher<Resp> request(GrpcClientMetadata metadata, Publisher<Req> request);
    }

    /**
     * An abstraction to make asynchronous client calls where request is streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface RequestStreamingClientCall<Req, Resp> {

        /**
         * Sends the passed {@link Publisher} of {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link Publisher} of {@link Req} to send.
         * @return {@link Single} containing the response.
         */
        Single<Resp> request(GrpcClientMetadata metadata, Publisher<Req> request);
    }

    /**
     * An abstraction to make asynchronous client calls where response is streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface ResponseStreamingClientCall<Req, Resp> {

        /**
         * Sends the passed {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link Req} to send.
         * @return {@link Publisher} containing the streaming response.
         */
        Publisher<Resp> request(GrpcClientMetadata metadata, Req request);
    }

    /**
     * An abstraction to make blocking client calls.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface BlockingClientCall<Req, Resp> {

        /**
         * Sends the passed {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link Req} to send.
         * @return {@link Resp} received.
         * @throws Exception if an exception occurs during the request processing.
         */
        Resp request(GrpcClientMetadata metadata, Req request) throws Exception;
    }

    /**
     * An abstraction to make blocking bi-directional streaming client calls.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface BlockingStreamingClientCall<Req, Resp> {

        /**
         * Sends the passed {@link BlockingIterable} of {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link BlockingIterable} of {@link Req} to send.
         * @return {@link BlockingIterable} containing the streaming response.
         * @throws Exception if an exception occurs during the request processing.
         */
        BlockingIterable<Resp> request(GrpcClientMetadata metadata, Iterable<Req> request) throws Exception;
    }

    /**
     * An abstraction to make blocking client calls where request is streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface BlockingRequestStreamingClientCall<Req, Resp> {

        /**
         * Sends the passed {@link BlockingIterable} of {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link BlockingIterable} of {@link Req} to send.
         * @return {@link Resp} received.
         * @throws Exception if an exception occurs during the request processing.
         */
        Resp request(GrpcClientMetadata metadata, Iterable<Req> request) throws Exception;
    }

    /**
     * An abstraction to make blocking client calls where response is streaming.
     *
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     */
    interface BlockingResponseStreamingClientCall<Req, Resp> {

        /**
         * Sends the passed {@link Req}.
         *
         * @param metadata {@link GrpcClientMetadata} for the request.
         * @param request {@link Req} to send.
         * @return {@link BlockingIterable} containing the streaming response.
         * @throws Exception if an exception occurs during the request processing.
         */
        BlockingIterable<Resp> request(GrpcClientMetadata metadata, Req request) throws Exception;
    }
}
