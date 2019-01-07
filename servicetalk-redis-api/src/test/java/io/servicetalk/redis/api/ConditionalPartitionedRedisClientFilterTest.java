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

import io.servicetalk.client.api.partition.PartitionAttributes;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Predicate;

import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.QUIT;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class ConditionalPartitionedRedisClientFilterTest {
    private static final Predicate<RedisRequest> TEST_REQ_PREDICATE = req -> req.command().equals(PING);

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private PartitionedRedisClient predicatedClient;

    @Mock
    private PartitionedRedisClient client;

    @Mock
    private RedisExecutionStrategy strategy;

    @Mock
    private PartitionAttributes attributes;

    private ConditionalPartitionedRedisClientFilter filter;

    @Before
    public void setup() {
        filter = new ConditionalPartitionedRedisClientFilter(TEST_REQ_PREDICATE, predicatedClient, client);
    }

    @After
    public void verifyMocks() {
        verifyNoMoreInteractions(predicatedClient, client);
    }

    @Test
    public void predicateAccepts() {
        testFilter(true);
    }

    @Test
    public void predicateRejects() {
        testFilter(false);
    }

    private void testFilter(final boolean expectAccepted) {
        RedisRequest req = newRequest(expectAccepted ? PING : QUIT);
        filter.request(strategy, attributes, req);

        if (expectAccepted) {
            verify(predicatedClient).request(eq(strategy), eq(attributes), eq(req));
        } else {
            verify(client).request(eq(strategy), eq(attributes), eq(req));
        }

        req = newRequest(expectAccepted ? PING : QUIT);
        filter.request(strategy, attributes, req, Object.class);

        if (expectAccepted) {
            verify(predicatedClient).request(eq(strategy), eq(attributes), eq(req), eq(Object.class));
        } else {
            verify(client).request(eq(strategy), eq(attributes), eq(req), eq(Object.class));
        }
    }
}
