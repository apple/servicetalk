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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnection.SettingKey;
import io.servicetalk.redis.api.RedisRequest;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.redis.api.RedisData.PONG;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LoadBalancedRedisConnectionTest {
    @Test
    public void singleRequestBringsCountBackToZero() throws ExecutionException, InterruptedException {
        final Publisher<Integer> maxRequestsPublisher = just(1);
        RedisConnection delegate = mock(RedisConnection.class);
        when(delegate.onClose()).thenReturn(never());
        when(delegate.closeAsync()).thenReturn(completed());
        when(delegate.getSettingStream(any(SettingKey.class))).thenReturn(maxRequestsPublisher);
        when(delegate.request(any(RedisRequest.class))).thenReturn(just(PONG));
        LoadBalancedRedisConnection connection = new LoadBalancedRedisConnection(delegate);
        assertTrue(connection.reserveForRequest());
        awaitIndefinitely(connection.request(newRequest(PING)));
        assertTrue(connection.reserveForRequest());
        connection.closeAsync().subscribe();
    }
}
