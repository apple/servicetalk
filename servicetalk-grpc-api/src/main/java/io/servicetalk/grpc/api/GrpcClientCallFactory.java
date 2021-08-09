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
import io.servicetalk.encoding.api.BufferDecoderGroup;
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
     * @deprecated Use {@link #newCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link ClientCall}.
     */
    @Deprecated
    <Req, Resp> ClientCall<Req, Resp> newCall(GrpcSerializationProvider serializationProvider,
                                              Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link ClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link ClientCall}.
     */
    <Req, Resp> ClientCall<Req, Resp> newCall(MethodDescriptor<Req, Resp> methodDescriptor,
                                              BufferDecoderGroup decompressors);

    /**
     * Creates a new {@link StreamingClientCall}.
     * @deprecated Use {@link #newStreamingCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link StreamingClientCall}.
     */
    @Deprecated
    <Req, Resp> StreamingClientCall<Req, Resp> newStreamingCall(GrpcSerializationProvider serializationProvider,
                                                                Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link StreamingClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link StreamingClientCall}.
     */
    <Req, Resp> StreamingClientCall<Req, Resp> newStreamingCall(MethodDescriptor<Req, Resp> methodDescriptor,
                                                                BufferDecoderGroup decompressors);

    /**
     * Creates a new {@link RequestStreamingClientCall}.
     * @deprecated Use {@link #newRequestStreamingCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link RequestStreamingClientCall}.
     */
    @Deprecated
    <Req, Resp> RequestStreamingClientCall<Req, Resp> newRequestStreamingCall(
            GrpcSerializationProvider serializationProvider, Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link RequestStreamingClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link RequestStreamingClientCall}.
     */
    <Req, Resp> RequestStreamingClientCall<Req, Resp> newRequestStreamingCall(
            MethodDescriptor<Req, Resp> methodDescriptor, BufferDecoderGroup decompressors);

    /**
     * Creates a new {@link ResponseStreamingClientCall}.
     * @deprecated Use {@link #newResponseStreamingCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link ResponseStreamingClientCall}.
     */
    @Deprecated
    <Req, Resp> ResponseStreamingClientCall<Req, Resp> newResponseStreamingCall(
            GrpcSerializationProvider serializationProvider, Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link ResponseStreamingClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link ResponseStreamingClientCall}.
     */
    <Req, Resp> ResponseStreamingClientCall<Req, Resp> newResponseStreamingCall(
            MethodDescriptor<Req, Resp> methodDescriptor, BufferDecoderGroup decompressors);

    /**
     * Creates a new {@link BlockingClientCall}.
     * @deprecated use {@link #newBlockingCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingClientCall}.
     */
    @Deprecated
    <Req, Resp> BlockingClientCall<Req, Resp> newBlockingCall(GrpcSerializationProvider serializationProvider,
                                                              Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link BlockingClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingClientCall}.
     */
    <Req, Resp> BlockingClientCall<Req, Resp> newBlockingCall(MethodDescriptor<Req, Resp> methodDescriptor,
                                                              BufferDecoderGroup decompressors);

    /**
     * Creates a new {@link BlockingStreamingClientCall}.
     * @deprecated Use {@link #newBlockingStreamingCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingStreamingClientCall}.
     */
    @Deprecated
    <Req, Resp> BlockingStreamingClientCall<Req, Resp> newBlockingStreamingCall(
            GrpcSerializationProvider serializationProvider, Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link BlockingStreamingClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingStreamingClientCall}.
     */
    <Req, Resp> BlockingStreamingClientCall<Req, Resp> newBlockingStreamingCall(
            MethodDescriptor<Req, Resp> methodDescriptor, BufferDecoderGroup decompressors);

    /**
     * Creates a new {@link BlockingRequestStreamingClientCall}.
     * @deprecated Use {@link #newBlockingRequestStreamingCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingRequestStreamingClientCall}.
     */
    @Deprecated
    <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp> newBlockingRequestStreamingCall(
            GrpcSerializationProvider serializationProvider, Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link BlockingRequestStreamingClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingRequestStreamingClientCall}.
     */
    <Req, Resp> BlockingRequestStreamingClientCall<Req, Resp> newBlockingRequestStreamingCall(
            MethodDescriptor<Req, Resp> methodDescriptor, BufferDecoderGroup decompressors);

    /**
     * Creates a new {@link BlockingResponseStreamingClientCall}.
     * @deprecated Use {@link #newBlockingResponseStreamingCall(MethodDescriptor, BufferDecoderGroup)}.
     * @param serializationProvider {@link GrpcSerializationProvider} to use.
     * @param requestClass {@link Class} object for the request.
     * @param responseClass {@link Class} object for the response.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingResponseStreamingClientCall}.
     */
    @Deprecated
    <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp> newBlockingResponseStreamingCall(
            GrpcSerializationProvider serializationProvider, Class<Req> requestClass, Class<Resp> responseClass);

    /**
     * Create a new {@link BlockingResponseStreamingClientCall}.
     * @param methodDescriptor describes the method characteristics and how to do serialization of individual objects.
     * @param decompressors describes the decompression that is supported for this call.
     * @param <Req> Type of request.
     * @param <Resp> Type of response.
     * @return {@link BlockingResponseStreamingClientCall}.
     */
    <Req, Resp> BlockingResponseStreamingClientCall<Req, Resp> newBlockingResponseStreamingCall(
            MethodDescriptor<Req, Resp> methodDescriptor, BufferDecoderGroup decompressors);

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
    @FunctionalInterface
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
    @FunctionalInterface
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
    @FunctionalInterface
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
    @FunctionalInterface
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
    @FunctionalInterface
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
    @FunctionalInterface
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
    @FunctionalInterface
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
    @FunctionalInterface
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
