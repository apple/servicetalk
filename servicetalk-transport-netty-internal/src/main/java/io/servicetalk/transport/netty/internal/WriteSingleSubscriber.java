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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

final class WriteSingleSubscriber implements SingleSource.Subscriber<Object>, DefaultNettyConnection.WritableListener {
    private static final AtomicIntegerFieldUpdater<WriteSingleSubscriber> terminatedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(WriteSingleSubscriber.class, "terminated");
    private final Channel channel;
    private final CompletableSource.Subscriber subscriber;
    private final CloseHandler closeHandler;
    private final SequentialCancellable sequentialCancellable;
    @SuppressWarnings("unused")
    private volatile int terminated;

    private static final int UNSET = 0;
    private static final int PENDING = 1;
    private static final int DONE = 2;

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
        if (terminatedUpdater.compareAndSet(this, UNSET, PENDING)) {
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
        }
    }

    @Override
    public void onError(Throwable t) {
        if (terminatedUpdater.compareAndSet(this, UNSET, PENDING)) {
            notifyError(t);
        }
    }

    @Override
    public void channelWritable() {
        // No op.
    }

    @Override
    public void closeGracefully() {
        if (terminated == UNSET) {
            throw new IllegalStateException("Unexpected, closeGracefully() without onSuccess()");
        }
    }

    @Override
    public void close(Throwable closedException) {
        // Because the subscriber is terminated "out of band" make sure we cancel any work which may (at some later
        // time) invoke a write associated with this subscriber.
        sequentialCancellable.cancel();
        notifyError(closedException);
    }

    private void notifyComplete() {
        if (terminatedUpdater.compareAndSet(this, PENDING, DONE)) {
            subscriber.onComplete();
        }
    }

    private void notifyError(Throwable t) {
        if (terminatedUpdater.compareAndSet(this, PENDING, DONE)) {
            closeHandler.closeChannelOutbound(channel);
            subscriber.onError(t);
        }
    }
}
