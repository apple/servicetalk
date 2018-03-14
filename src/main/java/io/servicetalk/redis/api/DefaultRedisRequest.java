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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisData.RequestRedisData;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.transport.api.FlushStrategy;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

final class DefaultRedisRequest implements RedisRequest {
    private final Command command;
    private final Publisher<RequestRedisData> content;
    private final FlushStrategy flushStrategy;

    DefaultRedisRequest(final Command command, final Publisher<RequestRedisData> content, final FlushStrategy flushStrategy) {
        this.command = requireNonNull(command);
        this.content = requireNonNull(content);
        this.flushStrategy = requireNonNull(flushStrategy);
    }

    @Override
    public Command getCommand() {
        return command;
    }

    @Override
    public Publisher<RequestRedisData> getContent() {
        return content;
    }

    @Override
    public FlushStrategy getFlushStrategy() {
        return flushStrategy;
    }

    @Override
    public RedisRequest composeContent(final Function<Publisher<RequestRedisData>, Publisher<RequestRedisData>> composer) {
        return new DefaultRedisRequest(command, composer.apply(content), flushStrategy);
    }

    @Override
    public String toString() {
        return new StringBuilder(256)
                .append(getClass().getSimpleName())
                .append('(')
                .append(command).append(' ')
                .append(content)
                .append(')').toString();
    }
}
