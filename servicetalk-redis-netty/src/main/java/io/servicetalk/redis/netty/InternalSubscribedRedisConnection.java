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

import io.servicetalk.client.api.RetryableException;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisProtocolSupport;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.SequentialTaskQueue;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.ThrowableUtil.matches;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.AUTH;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PSUBSCRIBE;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.QUIT;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.SUBSCRIBE;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.internal.RedisUtils.newRequestCompositeBuffer;
import static io.servicetalk.redis.netty.RedisUtils.isSubscribeModeCommand;
import static io.servicetalk.redis.netty.SubscribedChannelReadStream.PubSubChannelMessage.KeyType.SimpleString;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * An implementation of {@link RedisConnection} that can only be used for the subscribe mode of Redis.
 */
final class InternalSubscribedRedisConnection extends AbstractRedisConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalSubscribedRedisConnection.class);
    protected final Connection<RedisData, ByteBuf> connection;

    private final ReadStreamSplitter readStreamSplitter;
    private final WriteQueue writeQueue;
    private final boolean deferSubscribeTillConnect;

    private InternalSubscribedRedisConnection(Connection<RedisData, ByteBuf> connection,
                                              ExecutionContext executionContext,
                                              ReadOnlyRedisClientConfig roConfig, int initialQueueCapacity,
                                              int maxBufferPerGroup) {
        super(toNettyIoExecutor(connection.getExecutionContext().getIoExecutor()).asExecutor(), connection.onClosing(),
                executionContext, roConfig);
        this.connection = connection;
        this.deferSubscribeTillConnect = roConfig.isDeferSubscribeTillConnect();
        writeQueue = new WriteQueue(maxPendingRequests, initialQueueCapacity);
        this.readStreamSplitter = new ReadStreamSplitter(connection, maxPendingRequests, maxBufferPerGroup,
                redisRequest -> request0(redisRequest).ignoreElements());
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return connection;
    }

    @Override
    Publisher<RedisData> handleRequest(RedisRequest request) {
        final RedisProtocolSupport.Command command = request.getCommand();
        if (!isSubscribeModeCommand(command) && command != PING && command != QUIT && command != AUTH) {
            return Publisher.error(new IllegalArgumentException("Invalid command: " + command
                    + ". This command is not allowed in subscribe mode."));
        }

        return request0(request);
    }

    private Publisher<RedisData> request0(RedisRequest request) {
        final RedisProtocolSupport.Command command = request.getCommand();
        final Publisher<ByteBuf> reqContent = RedisUtils.encodeRequestContent(request,
                connection.getExecutionContext().getBufferAllocator());
        return new Publisher<RedisData>() {
            @SuppressWarnings("unchecked")
            @Override
            protected void handleSubscribe(Subscriber<? super RedisData> subscriber) {
                Completable write;
                if (command == QUIT) {
                    write = writeQueue.quit(connection.write(reqContent, request.getFlushStrategy()));
                } else {
                    write = writeQueue.write(connection.write(reqContent, request.getFlushStrategy()), command);
                }
                /*
                 We register a new command after we have written the request completely. Following is the reason:
                 - Since we correlate a response to a request using a queue, we need to make sure that the commands are
                 registered in the same order as they are written.
                 - writeQueue ensures that writes are properly ordered and onComplete is invoked before any other write is started.
                 - ReadStreamSplitter subscribes synchronously hence registering the command in the same order as it was written.

                 The above makes sure that we do not mix the order of writes and subscribers for a command.

                 Above has an implicit assumption that redis does not start sending the response before write is completed.
                 If it does, we get into issues in cases where a response is received on a pre-existing group.
                 These cases are the following:
                 - Duplicate (P)Subscribe
                 - Unsubscribe

                 Since, the above commands are not streaming in nature i.e. they do not have data associated which can be chunked, we
                 can be sure that our assumption is not violated.

                 In any other case, a command will emit a new group (in ReadStreamSplitter). Since we only request for a new
                 group, after we register the Subscriber for a command, we are OK even if response is received before request completes.
                 Since, in such a case groupBy will buffer the group and we will not see the new group in ReadStreamSplitter.
                 */
                final Publisher<PubSubChannelMessage> response;
                if (deferSubscribeTillConnect) {
                    response = concatDeferOnSubscribe(write, readStreamSplitter.registerNewCommand(command),
                            connection.getExecutionContext().getExecutor());
                } else {
                    response = write.andThen(readStreamSplitter.registerNewCommand(command));
                }
                // Unwrap PubSubChannelMessage if it wraps an SimpleString response
                response.map(m -> m.getKeyType() == SimpleString ? m.getData() : m).subscribe(subscriber);
            }
        };
    }

    @Override
    public Completable onClose() {
        return connection.onClose();
    }

    @Override
    Completable doClose() {
        return writeQueue.quit(request(newRequest(QUIT, newRequestCompositeBuffer(1, QUIT.toRESPArgument(
                connection.getExecutionContext().getBufferAllocator()),
                connection.getExecutionContext().getBufferAllocator()))).ignoreElements())
                .onErrorResume(th -> matches(th, ClosedChannelException.class) ? completed() :
                        connection.closeAsync().andThen(error(th)))
                .andThen(connection.closeAsync());
    }

    @Override
    public String toString() {
        return InternalSubscribedRedisConnection.class.getSimpleName() + "(" + connection + ")";
    }

    @Override
    Completable sendPing() {
        if (!writeQueue.subscribed) {
            // PING response is different (simple string) before we subscribe and hence it isn't parsed correctly.
            // So reject internal PINGs till we have subscribed.
            // This is pessimistic as if there is a concurrent SUBSCRIBE command, we may be lucky and our PING lands after
            // SUBSCRIBE. However, with this approach we just wait for the next ping cycle. Since, the ping frequency is not
            // strictly defined and expected, this is an acceptable approach which reduces work done inside command execution
            // to detect this case.
            return error(new PingRejectedException());
        }
        // We send a PING with no payload so the response is a fully aggregated PubSubChannelMessage.
        // So, issuing a single request(1) followed by a cancel is enough to consume to overall response,
        // thus the usage of first() and ignoreResult() below.
        return request0(newRequest(PING)).first().ignoreResult();
    }

    @Override
    Logger getLogger() {
        return LOGGER;
    }

    static RedisConnection newSubscribedConnection(Connection<RedisData, ByteBuf> connection,
                                                   ExecutionContext executionContext,
                                                   ReadOnlyRedisClientConfig roConfig) {
        return newSubscribedConnection(connection, executionContext, roConfig, 2, 256);
    }

    static RedisConnection newSubscribedConnection(Connection<RedisData, ByteBuf> connection,
                                                   ExecutionContext executionContext,
                                                   ReadOnlyRedisClientConfig roConfig,
                                                   int initialQueueCapacity,
                                                   int maxBufferPerGroup) {
        InternalSubscribedRedisConnection toReturn = new InternalSubscribedRedisConnection(connection, executionContext,
                roConfig, initialQueueCapacity, maxBufferPerGroup);
        toReturn.startPings();
        return toReturn;
    }

    private static final class WriteQueue extends SequentialTaskQueue<WriteQueue.WriteTask> {

        private static final RetryableException CONNECTION_IS_CLOSED_WRITE =
                unknownStackTrace(new RetryableException(new ClosedChannelException()), WriteQueue.class, "write(..)");
        private static final RetryableException CONNECTION_IS_CLOSED_QUIT =
                unknownStackTrace(new RetryableException(new ClosedChannelException()), WriteQueue.class, "quit(..)");

        private static final AtomicIntegerFieldUpdater<WriteTask> taskCalledPostTermUpdater = newUpdater(WriteTask.class, "taskCalledPostTerm");
        private static final AtomicIntegerFieldUpdater<WriteQueue> closedUpdater = newUpdater(WriteQueue.class, "closed");
        private final int maxPendingWrites;
        // Volatile for visibility, accessed from sendPing()
        volatile boolean subscribed;

        @SuppressWarnings("unused")
        private volatile int closed;

        WriteQueue(int maxPendingWrites, int initialQueueCapacity) {
            super(initialQueueCapacity, maxPendingWrites);
            this.maxPendingWrites = maxPendingWrites;
        }

        Completable write(Completable toWrite, RedisProtocolSupport.Command command) {
            return new Completable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    // Don't add more items to the queue if the connection is closed already.
                    if (closed != 0) {
                        subscriber.onSubscribe(IGNORE_CANCEL);
                        subscriber.onError(CONNECTION_IS_CLOSED_WRITE);
                        return;
                    }
                    WriteTask task = new WriteTask(command, toWrite, subscriber);
                    if (!offerAndTryExecute(task)) {
                        task.fail(new QueueFullException("write-queue", maxPendingWrites));
                    }
                }
            };
        }

        @Override
        protected void execute(WriteTask toExecute) {
            toExecute.doWork();
        }

        Completable quit(Completable quitRequestWrite) {
            return new Completable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    if (closedUpdater.compareAndSet(WriteQueue.this, 0, 1)) {
                        WriteTask task = new WriteTask(QUIT, quitRequestWrite, subscriber);
                        if (!offerAndTryExecute(task)) {
                            task.fail(new QueueFullException("write-queue", maxPendingWrites));
                        }
                        return;
                    }
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onError(CONNECTION_IS_CLOSED_QUIT);
                }
            };
        }

        private void safePostTaskTermination() {
            try {
                postTaskTermination();
            } catch (Throwable throwable) {
                LOGGER.error("Unexpected error invoking post task termination.", throwable);
            }
        }

        final class WriteTask {

            private final boolean isSubscribedCommand;
            private final Completable write;
            private final Completable.Subscriber subscriber;
            @SuppressWarnings("unused")
            volatile int taskCalledPostTerm;

            WriteTask(RedisProtocolSupport.Command command, Completable write, Completable.Subscriber subscriber) {
                this.isSubscribedCommand = command == PSUBSCRIBE || command == SUBSCRIBE;
                this.write = write;
                this.subscriber = subscriber;
            }

            void doWork() {
                if (isSubscribedCommand && !subscribed) {
                    subscribed = true;
                }
                write.subscribe(new Completable.Subscriber() {
                    @Override
                    public void onSubscribe(Cancellable cancellable) {
                        subscriber.onSubscribe(() -> {
                            try {
                                cancellable.cancel();
                            } finally {
                                invokePostTaskTermination();
                            }
                        });
                    }

                    @Override
                    public void onComplete() {
                        try {
                            subscriber.onComplete();
                        } finally {
                            invokePostTaskTermination();
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        try {
                            subscriber.onError(t);
                        } finally {
                            invokePostTaskTermination();
                        }
                    }
                });
            }

            void fail(Throwable throwable) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                subscriber.onError(throwable);
            }

            private void invokePostTaskTermination() {
                if (taskCalledPostTermUpdater.compareAndSet(this, 0, 1)) {
                    // postTaskTermination() assumes that the caller is holding the lock, so we need to avoid duplicate calls to it.
                    // Since, this is called from cancel as well as onError/Complete, we need to make sure, it is invoked once.
                    safePostTaskTermination();
                }
            }
        }
    }

    /**
     * Defers the {@link Subscriber#onSubscribe(Subscription)} (Subscription)} signal to the {@link Subscriber} of the returned
     * {@link Publisher} till {@code next} {@link Publisher} sends an {@link Subscriber#onSubscribe(Subscription)}.
     *
     * This operator is required for in-process publisher-subscriber coordination. As a consequence a queued subscription
     * command can't be cancelled before writing to Redis.
     *
     * @param queuedWrite the {@link Completable} tracking writing the enqueued
     *                    {@link RedisProtocolSupport.Command#SUBSCRIBE} or
     *                    {@link RedisProtocolSupport.Command#PSUBSCRIBE} commands to Redis
     * @param next        the {@link PubSubChannelMessage} producer to subscribe to after completing the original {@link Completable}
     * @param executor {@link Executor} used to create the returned {@link Publisher}.
     * @return the composite operator
     */
    private static Publisher<PubSubChannelMessage> concatDeferOnSubscribe(Completable queuedWrite,
                                                                          Publisher<PubSubChannelMessage> next,
                                                                          Executor executor) {

        return new Publisher<PubSubChannelMessage>() {
            @Override
            protected void handleSubscribe(org.reactivestreams.Subscriber<? super PubSubChannelMessage> subscriber) {
                queuedWrite.subscribe(new io.servicetalk.concurrent.Completable.Subscriber() {

                    @Override
                    public void onSubscribe(Cancellable cancellable) {
                        // Ignore onSubscribe as we are deferring the signal till we get the same from the next Publisher.
                    }

                    @Override
                    public void onComplete() {
                        next.subscribe(subscriber);
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                        subscriber.onError(t);
                    }
                });
            }
        };
    }
}
