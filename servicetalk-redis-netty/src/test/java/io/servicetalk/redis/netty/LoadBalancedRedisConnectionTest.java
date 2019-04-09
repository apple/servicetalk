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

import io.servicetalk.client.api.internal.IgnoreConsumedEvent;
import io.servicetalk.client.api.internal.ReservableRequestConcurrencyControllers;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnection.SettingKey;
import io.servicetalk.redis.api.RedisRequest;

import org.junit.Test;

import static io.servicetalk.client.api.internal.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.redis.api.RedisData.PONG;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LoadBalancedRedisConnectionTest {
    @Test
    public void singleRequestBringsCountBackToZero() throws Exception {
        final Publisher<Integer> maxRequestsPublisher = from(1);
        RedisConnection delegate = mock(RedisConnection.class);
        when(delegate.onClose()).thenReturn(never());
        when(delegate.closeAsync()).thenReturn(completed());
        when(delegate.settingStream(any(SettingKey.class))).thenReturn(maxRequestsPublisher);
        when(delegate.request(any(), any(RedisRequest.class))).thenReturn(from(PONG));
        LoadBalancedRedisConnection connection = new LoadBalancedRedisConnection(delegate,
                ReservableRequestConcurrencyControllers.newController(from(1).map(IgnoreConsumedEvent::new),
                        never(), 1));
        assertThat(connection.tryRequest(), is(Accepted));
        awaitIndefinitely(connection.request(newRequest(PING)));
        connection.requestFinished();
        assertThat(connection.tryRequest(), is(Accepted));
        connection.closeAsync().subscribe();
    }
}
