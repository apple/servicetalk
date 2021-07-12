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
package io.servicetalk.log4j2.mdc.utils;

import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Single;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.log4j2.mdc.utils.ServiceTalkThreadContextMap.getStorage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServiceTalkThreadContextMapTest {
    private static final Logger logger = LoggerFactory.getLogger(ServiceTalkThreadContextMapTest.class);

    @BeforeEach
    void verifyMDCSetup() {
        assumeTrue(ThreadContext.getThreadContextMap() instanceof ServiceTalkThreadContextMap);
    }

    @Test
    void testSimpleExecution() {
        MDC.clear();
        assertEquals(0, MDC.getCopyOfContextMap().size());
        assertEquals(0, getStorage().size(), "unexpected map: " + getStorage());

        MDC.put("aa", "1");
        assertEquals("1", MDC.get("aa"));
        assertEquals(1, MDC.getCopyOfContextMap().size());
        assertEquals(1, getStorage().size(), "unexpected map: " + getStorage());

        MDC.put("bb", "2");
        assertEquals("2", MDC.get("bb"));
        assertEquals(2, MDC.getCopyOfContextMap().size());
        assertEquals(2, getStorage().size());

        logger.info("expected aa=1 bb=2"); // human inspection as sanity check

        MDC.remove("aa");
        assertNull(MDC.get("aa"));
        assertEquals(1, MDC.getCopyOfContextMap().size());
        assertEquals(1, getStorage().size());

        MDC.setContextMap(Collections.singletonMap("cc", "3"));
        assertNull(MDC.get("bb"));
        assertEquals("3", MDC.get("cc"));
        assertEquals(1, MDC.getCopyOfContextMap().size());
        assertEquals(1, getStorage().size());

        logger.info("expected cc=3"); // human inspection as sanity check
    }

    @Test
    void testAsyncExecution() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MDC.clear();
            MDC.put("a", "1");
            MDC.put("b", "2");

            logger.info("expected a=1 b=2"); // human inspection as sanity check

            Thread original = Thread.currentThread();

            Single<String> single = new Single<String>() {
                @Override
                protected void handleSubscribe(Subscriber<? super String> singleSubscriber) {
                    executor.execute(() -> {
                        singleSubscriber.onSubscribe(IGNORE_CANCEL);
                        singleSubscriber.onSuccess("1");
                    });
                }
            }.map(v -> {
                assertNotEquals(Thread.currentThread(), original);
                assertEquals("1", MDC.get("a"));
                assertEquals("2", MDC.get("b"));
                MDC.put("b", "22");
                return v;
            }).beforeFinally(() -> {
                logger.info("expected a=1 b=22"); // human inspection as sanity check
                assertEquals("1", MDC.get("a"));
                assertEquals("22", MDC.get("b"));
            });

            single.toFuture().get();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testGetImmutableMapOrNull() {
        ServiceTalkThreadContextMap map = new ServiceTalkThreadContextMap();
        // The map is backed by thread local storage. So we make sure to clear it so other tests don't interfere.
        map.clear();
        assertNull(map.getImmutableMapOrNull());
        map.put("x", "10");
        Map<String, String> immutableMap = map.getImmutableMapOrNull();
        assertNotNull(immutableMap);
        assertEquals(1, immutableMap.size());
        try {
            immutableMap.put("y", "20");
            fail();
        } catch (UnsupportedOperationException ignored) {
            // expected
        }
    }
}
