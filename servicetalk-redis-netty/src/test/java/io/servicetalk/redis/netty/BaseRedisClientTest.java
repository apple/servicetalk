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
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.redis.api.DeferredValue;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.Callable;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;

public abstract class BaseRedisClientTest {
    protected static final int PING_PERIOD_SECONDS = 1;

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Rule
    public MockedSingleListenerRule<Object> singleListenerRule = new MockedSingleListenerRule<>();

    @Nullable
    private static RedisTestEnvironment env;

    @BeforeClass
    public static void startClient() throws Exception {
        env = new RedisTestEnvironment(immediate());
    }

    @AfterClass
    public static void stopClient() throws Exception {
        // @After is run even if assumption in @Before is violated
        if (env == null) {
            return;
        }
        env.close();
    }

    protected static Buffer buf(final CharSequence cs) {
        return getEnv().client.getExecutionContext().getBufferAllocator().fromUtf8(cs);
    }

    protected static RedisTestEnvironment getEnv() {
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
                        ((Buffer) argument).slice(0, buf.getReadableBytes()).equals(buf);
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

    @Nullable
    protected <T> T getEventually(final Callable<DeferredValue<T>> callable) throws Exception {
        final DeferredValue<T> deferredValue = callable.call();
        while (true) {
            try {
                return deferredValue.get();
            } catch (IllegalStateException e) {
                Thread.sleep(10);
            }
        }
    }
}
