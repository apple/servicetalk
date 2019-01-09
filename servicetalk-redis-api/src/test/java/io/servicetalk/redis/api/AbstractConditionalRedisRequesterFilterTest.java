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

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Predicate;

import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.QUIT;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public abstract class AbstractConditionalRedisRequesterFilterTest {
    protected static final Predicate<RedisRequest> TEST_REQ_PREDICATE = req -> req.command().equals(PING);

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    protected abstract RedisRequester predicatedRequester();

    protected abstract RedisRequester requester();

    protected abstract RedisRequester filter();

    @After
    public void verifyMocks() {
        verifyNoMoreInteractions(predicatedRequester(), requester());
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
        final RedisRequest req = newRequest(expectAccepted ? PING : QUIT);
        filter().request(req);

        if (expectAccepted) {
            verify(predicatedRequester()).request(any(RedisExecutionStrategy.class), eq(req));
        } else {
            verify(requester()).request(any(RedisExecutionStrategy.class), eq(req));
        }
    }
}
