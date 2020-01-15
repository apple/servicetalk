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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

final class WriteSingleSubscriber implements SingleSource.Subscriber<Object>, DefaultNettyConnection.WritableListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteSingleSubscriber.class);
    private static final AtomicIntegerFieldUpdater<WriteSingleSubscriber> terminatedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(WriteSingleSubscriber.class, "terminated");
    private final Channel channel;
    private final CompletableSource.Subscriber subscriber;
    private final CloseHandler closeHandler;
    private final SequentialCancellable sequentialCancellable;
    @SuppressWarnings("unused")
    private volatile int terminated;

    private static final int
            AWAITING_RESULT = 0,
            TERMINATED = 1;

    WriteSingleSubscriber(Channel channel, CompletableSource.Subscriber subscriber,
                          CloseHandler closeHandler) {
        this.channel = channel;
        this.subscriber = subscriber;
        this.closeHandler = closeHandler;
        sequentialCancellable = new SequentialCancellable();
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        sequentialCancellable.nextCancellable(cancellable);
        subscriber.onSubscribe(sequentialCancellable);
    }

    @Override
    public void onSuccess(@Nullable Object result) {
        if (terminatedUpdater.compareAndSet(this, AWAITING_RESULT, TERMINATED)) {
            // If we are not on the EventLoop then both the write and the flush will be enqueued on the EventLoop so
            // ordering should be correct.
            channel.writeAndFlush(result).addListener(future -> {
                Throwable cause = future.cause();
                if (cause == null) {
                    notifyComplete();
                } else {
                    notifyError(cause);
                }
            });
        } else {
            LOGGER.error("Ignoring write {} as the listener is already closed.", result);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (terminatedUpdater.compareAndSet(this, AWAITING_RESULT, TERMINATED)) {
            notifyError(t);
        } else {
            LOGGER.error("Ignoring emitted error as the listener is already closed.", t);
        }
    }

    @Override
    public void channelWritable() {
        // No op.
    }

    @Override
    public void closeGracefully() {
        if (terminatedUpdater.compareAndSet(this, AWAITING_RESULT, TERMINATED)) {
            notifyError(new IllegalStateException("Unexpected, closeGracefully() without onSuccess()"));
        }
    }

    @Override
    public void close(Throwable closedException) {
        // Because the subscriber is terminated "out of band" make sure we cancel any work which may (at some later
        // time) invoke a write associated with this subscriber.
        sequentialCancellable.cancel();
        if (terminatedUpdater.compareAndSet(this, AWAITING_RESULT, TERMINATED)) {
            notifyError(closedException);
        }
    }

    private void notifyComplete() {
        subscriber.onComplete();
    }

    private void notifyError(Throwable t) {
        closeHandler.closeChannelOutbound(channel);
        subscriber.onError(t);
    }
}
