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
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.PlatformDependent.throwException;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PUBLISH;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static io.servicetalk.redis.netty.RedisDataMatcher.redisInteger;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class BaseRedisClientTest {
    protected static final int PING_PERIOD_SECONDS = 1;

    static final String EVAL_SLEEP_SCRIPT = "local key = \"delayKey\"\n" +
            "redis.call(\"SETEX\", key, 1, \"someval\")\n" +
            "while redis.call(\"TTL\", key) > 0 do\n" +
            "end";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Nullable
    private RedisTestEnvironment env;

    final CountDownLatch postReleaseLatch = new CountDownLatch(1);
    final CountDownLatch postCloseLatch = new CountDownLatch(1);

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

    RedisClient getMockRedisClient() {
        RedisClient client = getEnv().client;
        RedisClient mockClient = mock(RedisClient.class, delegatesTo(client));
        doAnswer(invocation -> {
            Command command = invocation.getArgument(0);
            return client.reserveConnection(command).map(rconn -> {
                ReservedRedisConnection mockConnection = mock(ReservedRedisConnection.class, delegatesTo(rconn));
                when(mockConnection.releaseAsync()).then(__ -> {
                    return rconn.releaseAsync().doAfterComplete(() -> {
                        postReleaseLatch.countDown();
                    });
                });
                when(mockConnection.closeAsync()).then(__ -> {
                    return rconn.closeAsync().doAfterComplete(() -> {
                        postCloseLatch.countDown();
                    });
                });
                return mockConnection;
            });
        }).when(mockClient).reserveConnection(any(Command.class));
        return mockClient;
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

    protected interface RunnableCheckedException {
        void run() throws Exception;
    }

    protected static void assertThrowsClosedChannelException(final RunnableCheckedException o) throws Exception {
        try {
            try {
                o.run();
                fail("Expected " + ExecutionException.class.getSimpleName());
            } catch (ExecutionException e) {
                throwException(e.getCause());
            }
        } catch (ClosedChannelException e) {
            // expected
        }
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
