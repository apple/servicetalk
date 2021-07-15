/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.close;

abstract class ChannelInitSingle<T> extends SubscribableSingle<T> {
    private final Channel channel;
    private final ChannelInitializer channelInitializer;

    ChannelInitSingle(final Channel channel, final ChannelInitializer channelInitializer) {
        this.channel = channel;
        this.channelInitializer = channelInitializer;
    }

    @Override
    protected final void handleSubscribe(final Subscriber<? super T> subscriber) {
        try {
            channelInitializer.init(channel);
        } catch (Throwable cause) {
            close(channel, cause);
            deliverErrorFromSource(subscriber, cause);
            return;
        }
        subscriber.onSubscribe(channel::close);
        // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
        // callbacks that interact with the subscriber.
        channel.pipeline().addLast(newChannelHandler(subscriber));
    }

    protected abstract ChannelHandler newChannelHandler(Subscriber<? super T> subscriber);
}
