/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;

import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http2.Http2StreamChannel;

import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.TransportObserverUtils.connectionError;
import static java.util.Objects.requireNonNull;

final class StreamObserverUtils {

    private StreamObserverUtils() {
        // No instances
    }

    @Nullable
    static StreamObserver registerStreamObserver(final Http2StreamChannel streamChannel,
                                                 @Nullable final MultiplexedObserver multiplexedObserver) {
        if (multiplexedObserver == null) {
            return null;
        }
        final StreamObserver observer = requireNonNull(multiplexedObserver.onNewStream());
        streamChannel.closeFuture().addListener((ChannelFutureListener) future -> {
            Throwable t = connectionError(streamChannel);
            if (t == null) {
                observer.streamClosed();
            } else {
                observer.streamClosed(t);
            }
        });
        return observer;
    }
}
