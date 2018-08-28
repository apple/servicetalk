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

import java.util.function.Function;

/**
 * A Redis request.
 */
public interface RedisRequest {
    /**
     * The request command.
     *
     * @return the {@link Command} associated with this request.
     */
    Command getCommand();

    /**
     * The content of the request.
     *
     * @return the content.
     */
    Publisher<RequestRedisData> getContent();

    /**
     * Modifies the content {@link Publisher} of this {@link RedisRequest} preserving other properties.
     *
     * @param transformer {@link Function} to modify the content {@link Publisher}.
     * @return New {@link RedisRequest} modifying the content of {@code this} {@link RedisRequest} as provided by
     * {@code transformer} {@link Function}.
     */
    RedisRequest transformContent(Function<Publisher<RequestRedisData>, Publisher<RequestRedisData>> transformer);
}
