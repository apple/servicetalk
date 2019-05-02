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
package io.servicetalk.transport.netty.internal;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * Utilities for {@link ChannelPipeline} and SSL/TLS.
 */
public final class NettyPipelineSslUtils {
    private NettyPipelineSslUtils() {
        // no instances.
    }

    /**
     * Determine if the {@link ChannelPipeline} is configured for SSL/TLS.
     *
     * @param pipeline The pipeline to check.
     * @return {@code true} if the pipeline is configured to use SSL/TLS.
     */
    public static boolean isSslEnabled(ChannelPipeline pipeline) {
        return pipeline.get(SslHandler.class) != null || pipeline.get(SniHandler.class) != null;
    }

    /**
     * Extract the {@link SSLSession} from the {@link ChannelPipeline} if the {@link SslHandshakeCompletionEvent}
     * is successful.
     *
     * @param pipeline the {@link ChannelPipeline} which contains handler containing the {@link SSLSession}.
     * @param sslEvent the event indicating a SSL/TLS handshake completed.
     * @param failureConsumer invoked if a failure is encountered.
     * @return The {@link SSLSession} or {@code null} if none can be found.
     */
    @Nullable
    public static SSLSession extractSslSession(ChannelPipeline pipeline,
                                               SslHandshakeCompletionEvent sslEvent,
                                               Consumer<Throwable> failureConsumer) {
        if (sslEvent.isSuccess()) {
            final SslHandler sslHandler = pipeline.get(SslHandler.class);
            if (sslHandler != null) {
                return sslHandler.engine().getSession();
            } else {
                failureConsumer.accept(new IllegalStateException("Unable to find " + SslHandler.class.getName() +
                        " in the pipeline."));
            }
        } else {
            failureConsumer.accept(sslEvent.cause());
        }
        return null;
    }
}
