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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ConnectionInfo.Protocol;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.join;

/**
 * Utilities to printing additional information of HTTP protocol.
 */
final class HttpDebugUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpDebugUtils.class);

    private HttpDebugUtils() {
        // No instances
    }

    static <T extends NettyConnectionContext> Single<T> showPipeline(final Single<T> contextSingle,
                                                                     final Protocol protocol,
                                                                     final Channel channel) {
        if (LOGGER.isDebugEnabled()) {
            return contextSingle.whenOnSuccess(ctx -> LOGGER.debug("{} {} pipeline initialized: {}",
                    channel, protocol.name(), join(", ", channel.pipeline().names())));
        }
        return contextSingle;
    }
}
