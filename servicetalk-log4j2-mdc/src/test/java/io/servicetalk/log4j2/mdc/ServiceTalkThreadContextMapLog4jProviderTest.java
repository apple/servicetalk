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
package io.servicetalk.log4j2.mdc;

import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.api.Single;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class ServiceTalkThreadContextMapLog4jProviderTest {

    private static final String FOO_STRING = "foo";
    private static final String BAR_STRING = "bar";
    @Test
    void testProviderLoadsClass() {
        assertThat(ThreadContext.getThreadContextMap(), is(instanceOf(DefaultServiceTalkThreadContextMap.class)));
    }

    @BeforeEach
    void setup() {
        MDC.clear();
    }

    @Test
    void withoutSharingMDCContextIsCopied() throws Exception {
        assertThat(runWithMdc().toFuture().get().fooString, nullValue());
        MDC.put(FOO_STRING, FOO_STRING);
        Result result = runWithMdc().toFuture().get();
        assertThat(result.fooString, equalTo(FOO_STRING));
        assertThat(result.contextInstance, not(sameInstance(currentContext())));
        assertThat(MDC.get(FOO_STRING), equalTo(FOO_STRING));
        assertThat(MDC.get(BAR_STRING), nullValue());
    }

    @Test
    void withSharingMDCContextIsShared() throws Exception {
        assertThat(runWithMdc().toFuture().get().fooString, nullValue());
        MDC.put(FOO_STRING, FOO_STRING);
        Result result = runWithMdc().shareContextOnSubscribe().toFuture().get();
        assertThat(result.fooString, equalTo(FOO_STRING));
        assertThat(result.contextInstance, sameInstance(currentContext()));
        assertThat(MDC.get(FOO_STRING), nullValue());
        assertThat(MDC.get(BAR_STRING), equalTo(BAR_STRING));
    }

    private static Single<Result> runWithMdc() {
        Thread callingThread = Thread.currentThread();
        return Executors.global().submitCallable(() -> () -> {
            assertThat(callingThread, not(sameInstance(Thread.currentThread())));
            Map<?, ?> context = currentContext();
            String result = MDC.get(FOO_STRING);
            MDC.clear();
            MDC.put(BAR_STRING, BAR_STRING);
            return new Result(context, result);
        });
    }

    private static final class Result {
        final Map<?, ?> contextInstance;
        final String fooString;

        Result(Map<?, ?> contextInstance, String fooString) {
            this.contextInstance = contextInstance;
            this.fooString = fooString;
        }
    }

    private static Map<String, String> currentContext() {
        return DefaultServiceTalkThreadContextMap.CONTEXT_STORAGE.get();
    }
}
