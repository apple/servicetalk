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
package io.servicetalk.redis.netty;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.DefaultPipelinedConnection;
import io.servicetalk.transport.netty.internal.PipelinedConnection;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.ThrowableUtil.matches;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.DISCARD;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.EXEC;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.MONITOR;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.MULTI;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PSUBSCRIBE;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.QUIT;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.SUBSCRIBE;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.internal.RedisUtils.newRequestCompositeBuffer;
import static io.servicetalk.redis.netty.RedisUtils.encodeRequestContent;
import static io.servicetalk.redis.netty.TerminalMessagePredicates.forCommand;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class PipelinedRedisConnection extends AbstractRedisConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelinedRedisConnection.class);

    private static final AtomicIntegerFieldUpdater<PipelinedRedisConnection> skipQuitWhenClosedUpdater =
            newUpdater(PipelinedRedisConnection.class, "skipQuitWhenClosed");
    private final PipelinedConnection<ByteBuf, RedisData> connection;
    private final Connection<RedisData, ByteBuf> rawConnection;

    /**
     This is only used within the Writer while writing a request on the connection.
     Since, Write guarantees sequential access and visibility, we do not need this field to be volatile/atomic.
     */
    @Nullable
    private RedisProtocolSupport.Command potentiallyConflictingCommand;

    /**
     In case we are running a long running command like MONITOR, (P)SUBSCRIBE, issuing QUIT may never complete
     as QUIT response will only be read from the pipelined connection once the previous command has completed.
     NOTE that this is a limitation of pipelining that the responses are read sequentially.
     In such cases, we do not issue a QUIT from closeAsync.
     */
    @SuppressWarnings("unused")
    private volatile int skipQuitWhenClosed;

    @SuppressWarnings("unchecked")
    private PipelinedRedisConnection(Connection<RedisData, ByteBuf> connection,
                                     ExecutionContext executionContext,
                                     ReadOnlyRedisClientConfig roConfig) {
        super(toNettyIoExecutor(connection.executionContext().ioExecutor()).asExecutor(), connection.onClosing(),
                executionContext, roConfig);
        this.connection = new DefaultPipelinedConnection<>(connection, maxPendingRequests);
        rawConnection = connection;
    }

    @SuppressWarnings("unchecked")
    public Completable doClose() {
        return request0(newRequest(QUIT, newRequestCompositeBuffer(1, QUIT.toRESPArgument(
                connection.executionContext().bufferAllocator()),
                connection.executionContext().bufferAllocator())), true, false)
                .ignoreElements()
                .onErrorResume(th -> matches(th, ClosedChannelException.class) ? completed() :
                        connection.closeAsync().andThen(error(th)))
                .andThen(connection.closeAsync());
    }

    @Override
    Completable sendPing() {
        // We send a PING with no payload so the response is a simple string PONG with no payload.
        // So issuing a single request(1) followed by a cancel is enough to consume to overall response,
        // thus the usage of first() and ignoreResult() below.
        return request0(newRequest(PING), false, true).first().ignoreResult();
    }

    @Override
    Logger getLogger() {
        return LOGGER;
    }

    @Override
    public Completable onClose() {
        return connection.onClose();
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return connection;
    }

    @Override
    Publisher<RedisData> handleRequest(final RedisRequest request) {
        return request0(request, false, false);
    }

    static PipelinedRedisConnection newPipelinedConnection(Connection<RedisData, ByteBuf> connection,
                                                           ExecutionContext executionContext,
                                                           ReadOnlyRedisClientConfig roConfig) {
        PipelinedRedisConnection toReturn = new PipelinedRedisConnection(connection, executionContext, roConfig);
        toReturn.startPings();
        return toReturn;
    }

    private Publisher<RedisData> request0(final RedisRequest request, boolean fromClose, boolean internalPing) {
        return new Publisher<RedisData>() {
            @Override
            protected void handleSubscribe(Subscriber<? super RedisData> subscriber) {
                final RedisProtocolSupport.Command cmd = request.command();
                boolean flaggedSkipQuit = cmd == MONITOR || cmd == SUBSCRIBE || cmd == PSUBSCRIBE;
                if (flaggedSkipQuit) {
                    skipQuitWhenClosedUpdater.incrementAndGet(PipelinedRedisConnection.this);
                }
                if (cmd == QUIT && fromClose && skipQuitWhenClosed > 0) {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onComplete();
                    return;
                }
                //Since we do not re-subscribe to the same publisher returned by connection.request,
                // we can create the predicate here and return the same instance from the supplier.
                TerminalMessagePredicates.TerminalMessagePredicate predicate = forCommand(cmd);
                connection.request(() -> {
                    if (cmd == MONITOR || cmd == MULTI) {
                        potentiallyConflictingCommand = cmd;
                    } else if (potentiallyConflictingCommand == MULTI && (cmd == EXEC || cmd == DISCARD)) {
                        // End of MULTI so no more conflicting command.
                        potentiallyConflictingCommand = null;
                    }
                    if (internalPing && potentiallyConflictingCommand != null) {
                        return Completable.error(new PingRejectedException(potentiallyConflictingCommand));
                    }
                    return rawConnection.write(encodeRequestContent(request,
                            connection.executionContext().bufferAllocator()));
                }, () -> predicate)
                        .doBeforeNext(predicate::trackMessage)
                        .doBeforeFinally(() -> {
                            if (flaggedSkipQuit) {
                                skipQuitWhenClosedUpdater.decrementAndGet(PipelinedRedisConnection.this);
                            }
                        })
                        .subscribe(subscriber);
            }
        };
    }

    @Override
    public String toString() {
        return PipelinedRedisConnection.class.getSimpleName() + "(" + connection + ")";
    }
}
