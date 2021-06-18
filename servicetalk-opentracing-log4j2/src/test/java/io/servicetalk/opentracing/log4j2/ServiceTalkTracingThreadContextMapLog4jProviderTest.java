/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.opentracing.log4j2;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServiceTalkTracingThreadContextMapLog4jProviderTest {
    @Test
    void testProviderLoadsClass() {
        assertThat(ThreadContext.getThreadContextMap(), is(instanceOf(ServiceTalkTracingThreadContextMap.class)));
    }

    @Test
    void testNullValues() {
        MDC.put("foo", null);
        assertNull(MDC.get("foo"));
    }
}
