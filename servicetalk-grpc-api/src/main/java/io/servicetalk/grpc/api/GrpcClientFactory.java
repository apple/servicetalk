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

import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.EmptyBufferDecoderGroup;
import io.servicetalk.encoding.api.Identity;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * A factory for creating clients that follows the specified <a href="https://www.grpc.io">gRPC</a> {@link Client}
 * contract.
 *
 * @param <Client> <a href="https://www.grpc.io">gRPC</a> service that any client built from this
 * factory represents.
 * @param <BlockingClient> Blocking <a href="https://www.grpc.io">gRPC</a> service that any client
 * built from this builder represents.
 */
public abstract class GrpcClientFactory<Client extends GrpcClient<BlockingClient>,
        BlockingClient extends BlockingGrpcClient<Client>> {

    @Deprecated
    private List<ContentCodec> supportedCodings = emptyList();
    private BufferDecoderGroup bufferDecoderGroup = EmptyBufferDecoderGroup.INSTANCE;

    /**
     * Create a new client that follows the specified <a href="https://www.grpc.io">gRPC</a>
     * {@link Client} contract using the passed {@link GrpcClientCallFactory}.
     *
     * @param clientCallFactory {@link GrpcClientCallFactory} to use for creating client calls.
     * The returned {@link Client} should own the lifecycle of this factory.
     * @return A new <a href="https://www.grpc.io">gRPC</a> client following the specified
     * <a href="https://www.grpc.io">gRPC</a> {@link Client} contract.
     */
    final Client newClientForCallFactory(GrpcClientCallFactory clientCallFactory) {
        return newClient(clientCallFactory);
    }

    /**
     * Create a new client that follows the specified <a href="https://www.grpc.io">gRPC</a>
     * {@link BlockingClient} contract using the passed {@link GrpcClientCallFactory}.
     *
     * @param clientCallFactory {@link GrpcClientCallFactory} to use for creating client calls.
     * The returned {@link Client} should own the lifecycle of this factory.
     * @return A new <a href="https://www.grpc.io">gRPC</a> client following the specified
     * <a href="https://www.grpc.io">gRPC</a> {@link BlockingClient} contract.
     */
    final BlockingClient newBlockingClientForCallFactory(GrpcClientCallFactory clientCallFactory) {
            return newBlockingClient(clientCallFactory);
    }

    /**
     * Sets the supported message encodings for this client factory.
     * By default only {@link Identity#identity()} is supported
     * @param codings The supported encodings {@link ContentCodec}s for this client.
     * @return {@code this}
     * @deprecated Use generated code methods targeting {@link List} of
     * {@link io.servicetalk.encoding.api.BufferEncoder}s and {@link io.servicetalk.encoding.api.BufferDecoderGroup}.
     */
    @Deprecated
    public GrpcClientFactory<Client, BlockingClient>
    supportedMessageCodings(List<ContentCodec> codings) {
        this.supportedCodings = unmodifiableList(new ArrayList<>(codings));
        return this;
    }

    /**
     * Return the supported {@link ContentCodec}s for this client factory.
     * @return the supported {@link ContentCodec}s for this client factory.
     * @deprecated Use generated code methods targeting {@link List} of
     * {@link io.servicetalk.encoding.api.BufferEncoder}s and {@link io.servicetalk.encoding.api.BufferDecoderGroup}.
     */
    @Deprecated
    protected List<ContentCodec> supportedMessageCodings() {
        return supportedCodings;
    }

    /**
     * Sets the supported {@link BufferDecoderGroup} for this client factory.
     * By default only {@link Identity#identityEncoder()} is supported
     *
     * {@link io.servicetalk.encoding.api.BufferDecoderGroup}.
     * @param bufferDecoderGroup The supported {@link BufferDecoderGroup} for this client.
     * @return {@code this}
     */
    public GrpcClientFactory<Client, BlockingClient> bufferDecoderGroup(
            BufferDecoderGroup bufferDecoderGroup) {
        this.bufferDecoderGroup = requireNonNull(bufferDecoderGroup);
        return this;
    }

    /**
     * Get the supported {@link BufferDecoderGroup} for this client factory.
     * @return the supported {@link BufferDecoderGroup} for this client factory.
     */
    protected BufferDecoderGroup bufferDecoderGroup() {
        return bufferDecoderGroup;
    }

    /**
     * Create a new client that follows the specified <a href="https://www.grpc.io">gRPC</a>
     * {@link Client} contract using the passed {@link GrpcClientCallFactory}.
     *
     * @param clientCallFactory {@link GrpcClientCallFactory} to use for creating client calls.
     * The returned {@link Client} should own the lifecycle of this factory.
     * @return A new <a href="https://www.grpc.io">gRPC</a> client following the specified
     * <a href="https://www.grpc.io">gRPC</a> {@link Client} contract.
     */
    protected abstract Client newClient(GrpcClientCallFactory clientCallFactory);

    /**
     * Create a new client that follows the specified <a href="https://www.grpc.io">gRPC</a>
     * {@link BlockingClient} contract using the passed {@link GrpcClientCallFactory}.
     *
     * @param clientCallFactory {@link GrpcClientCallFactory} to use for creating client calls.
     * The returned {@link Client} should own the lifecycle of this factory.
     * @return A new <a href="https://www.grpc.io">gRPC</a> client following the specified
     * <a href="https://www.grpc.io">gRPC</a> {@link BlockingClient} contract.
     */
    protected abstract BlockingClient newBlockingClient(GrpcClientCallFactory clientCallFactory);
}
