/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.redis.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.function.Function;

import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.redis.api.RedisData.OK;
import static java.util.Objects.requireNonNull;

/**
 * Create a {@link ConnectionFactory} which will issue a <a href="https://redis.io/commands/auth">AUTH</a> command to initialize.
 * @param <ResolvedAddress> The type of resolved address.
 */
public class RedisAuthConnectionFactory<ResolvedAddress> implements ConnectionFactory<ResolvedAddress, RedisConnection> {
    private final ConnectionFactory<ResolvedAddress, ? extends RedisConnection> delegate;
    private final Function<ConnectionContext, Buffer> addressToPassword;

    /**
     * Create a new instance.
     * @param delegate The {@link ConnectionFactory} to delegate connection events to.
     *                 <p>
     *                 {@link #closeAsync()} ownership will be transferred to the callee.
     * @param addressToPassword A {@link Function} which provides the password for the <a href="https://redis.io/commands/auth">AUTH</a> request.
     */
    public RedisAuthConnectionFactory(ConnectionFactory<ResolvedAddress, ? extends RedisConnection> delegate,
                                      Function<ConnectionContext, Buffer> addressToPassword) {
        this.delegate = requireNonNull(delegate);
        this.addressToPassword = requireNonNull(addressToPassword);
    }

    @Override
    public Single<RedisConnection> newConnection(ResolvedAddress resolvedAddress) {
        return delegate.newConnection(resolvedAddress)
                .flatMap(cnx -> cnx.asBufferCommander().auth(addressToPassword.apply(cnx.getConnectionContext()))
                        .onErrorResume(cause -> {
                            cnx.closeAsync().subscribe();
                            return error(new RedisAuthorizationException("Failed to authenticate on connection " + cnx + " to address " + resolvedAddress, cause));
                        })
                        .flatMap(response -> {
                            if (response.contentEquals(OK.getCharSequenceValue())) {
                                return success(cnx);
                            }
                            cnx.closeAsync().subscribe();
                            return error(new RedisAuthorizationException("Failed to authenticate on connection " + cnx + " to address " + resolvedAddress + ". Response: " + response));
                        })
                );
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }
}
