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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.Publisher;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.cached;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.batchFlush;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.flushBeforeEnd;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.flushOnEach;

class FlushWithExecutorTest extends AbstractFlushTest {

    private static final String[] data = new String[]{"1", "2", "3", "4"};

    @RegisterExtension
    final ExecutionContextExtension contextRule = cached();

    @SuppressWarnings("unused")
    public static Collection<FlushStrategyAndVerifier> flushStrategyAndVerifiers() {
        List<FlushStrategyAndVerifier> params = new ArrayList<>();
        params.add(flushOnEach());
        params.add(flushBeforeEnd(data.length));
        params.add(batchFlush(2));
        return params;
    }

    @ParameterizedTest(name = "{index}: flushStrategy = {0}")
    @MethodSource("flushStrategyAndVerifiers")
    void testFlushBeforeEnd(final FlushStrategyAndVerifier flushStrategyAndVerifier) throws Exception {
        Publisher<String> source = from(data).publishOn(contextRule.executor()).map(String::valueOf);
        Publisher<String> flushSource = setup(source, flushStrategyAndVerifier.flushStrategy());
        flushSource.toFuture().get();
        int index = 0;
        for (String datum : data) {
            verifyWrite(datum);
            if (flushStrategyAndVerifier.expectFlushAtIndex().test(index)) {
                index++;
                verifyFlush();
            }
        }
    }
}
