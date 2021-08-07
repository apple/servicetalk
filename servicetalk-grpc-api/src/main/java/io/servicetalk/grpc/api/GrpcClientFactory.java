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
import javax.annotation.Nullable;

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
 * @param <Filter> Type for client filter
 * @param <FilterableClient> Type of filterable client.
 * @param <FilterFactory> Type of {@link GrpcClientFilterFactory}
 */
public abstract class GrpcClientFactory<Client extends GrpcClient<BlockingClient>,
        BlockingClient extends BlockingGrpcClient<Client>,
        Filter extends FilterableClient, FilterableClient extends FilterableGrpcClient,
        FilterFactory extends GrpcClientFilterFactory<Filter, FilterableClient>> {

    @Nullable
    private FilterFactory filterFactory;

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
        if (filterFactory == null) {
            return newClient(clientCallFactory);
        }
        return newClient(newFilter(newClient(clientCallFactory), filterFactory));
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
        if (filterFactory == null) {
            return newBlockingClient(clientCallFactory);
        }
        return newClient(newFilter(
                newBlockingClient(clientCallFactory).asClient(), filterFactory))
                .asBlockingClient();
    }

    /**
     * Appends the passed {@link FilterFactory} to this factory.
     *
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     filter1.append(filter2).append(filter3)
     * </pre>
     * making a request to a client wrapped by this filter chain the order of invocation of these filters will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ client
     * </pre>
     *
     * @param before the factory to apply before this factory is applied
     * @return {@code this}
     */
    public GrpcClientFactory<Client, BlockingClient, Filter, FilterableClient, FilterFactory>
    appendClientFilter(FilterFactory before) {
        if (filterFactory == null) {
            filterFactory = before;
        } else {
            this.filterFactory = appendClientFilterFactory(filterFactory, requireNonNull(before));
        }
        return this;
    }

    /**
     * Sets the supported message encodings for this client factory.
     * By default only {@link Identity#identity()} is supported
     * @deprecated Use generated code methods targeting {@link List} of
     * {@link io.servicetalk.encoding.api.BufferEncoder}s and {@link io.servicetalk.encoding.api.BufferDecoderGroup}.
     * @param codings The supported encodings {@link ContentCodec}s for this client.
     * @return {@code this}
     */
    @Deprecated
    public GrpcClientFactory<Client, BlockingClient, Filter, FilterableClient, FilterFactory>
    supportedMessageCodings(List<ContentCodec> codings) {
        this.supportedCodings = unmodifiableList(new ArrayList<>(codings));
        return this;
    }

    /**
     * Return the supported {@link ContentCodec}s for this client factory.
     * @deprecated Use generated code methods targeting {@link List} of
     * {@link io.servicetalk.encoding.api.BufferEncoder}s and {@link io.servicetalk.encoding.api.BufferDecoderGroup}.
     * @return the supported {@link ContentCodec}s for this client factory
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
    public GrpcClientFactory<Client, BlockingClient, Filter, FilterableClient, FilterFactory> bufferDecoderGroup(
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
     * Appends the passed {@link FilterFactory} to this client factory.
     *
     * @param existing Existing {@link FilterFactory}.
     * @param append {@link FilterFactory} to append to {@code existing}.
     * @return a composed factory that first applies the {@code before} factory and then applies {@code existing}
     * factory
     */
    protected abstract FilterFactory appendClientFilterFactory(FilterFactory existing, FilterFactory append);

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
     * Create a new {@link Filter} using the passed {@link Client} and {@link FilterFactory}.
     *
     * @param client {@link Client} to use for creating a {@link Filter} through the {@link FilterFactory}.
     * @param filterFactory {@link FilterFactory}
     * @return A {@link Filter} filtering the passed {@link Client}.
     */
    protected abstract Filter newFilter(Client client, FilterFactory filterFactory);

    /**
     * Create a new {@link Client} using the passed {@link FilterableClient}.
     *
     * @param filterableClient {@link FilterableClient} to create a {@link Client} from.
     * @return A new <a href="https://www.grpc.io">gRPC</a> client following the specified
     * <a href="https://www.grpc.io">gRPC</a> {@link Client} contract.
     */
    protected abstract Client newClient(FilterableClient filterableClient);

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
