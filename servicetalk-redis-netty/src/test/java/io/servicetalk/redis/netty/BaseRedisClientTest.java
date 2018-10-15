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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PUBLISH;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisInteger;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public abstract class BaseRedisClientTest {
    protected static final int PING_PERIOD_SECONDS = 1;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Nullable
    private RedisTestEnvironment env;

    @Before
    public void startClient() throws Exception {
        env = new RedisTestEnvironment(immediate());
    }

    @After
    public void stopClient() throws Exception {
        // @After is run even if assumption in @Before is violated
        if (env == null) {
            return;
        }
        env.close();
    }

    protected Buffer buf(final CharSequence cs) {
        return getEnv().client.executionContext().bufferAllocator().fromUtf8(cs);
    }

    protected RedisTestEnvironment getEnv() {
        if (env == null) {
            throw new IllegalStateException("Environment is not setup.");
        }
        return env;
    }

    protected static Matcher<Buffer> bufStartingWith(final Buffer buf) {
        return new BaseMatcher<Buffer>() {
            @Override
            public boolean matches(final Object argument) {
                return argument instanceof Buffer &&
                        ((Buffer) argument).slice(0, buf.readableBytes()).equals(buf);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText(Buffer.class.getSimpleName())
                        .appendText("{")
                        .appendValue(buf)
                        .appendText("}");
            }
        };
    }

    void publishTestMessage(final String channel) throws Exception {
        publishTestMessage(buf(channel));
    }

    void publishTestMessage(final Buffer channel) throws Exception {
        assertThat(awaitIndefinitely(getEnv().client.request(newRequest(PUBLISH, new CompleteBulkString(channel),
                new CompleteBulkString(buf("test-message"))))),
                contains(redisInteger(either(is(0L)).or(is(1L)))));
    }
}
