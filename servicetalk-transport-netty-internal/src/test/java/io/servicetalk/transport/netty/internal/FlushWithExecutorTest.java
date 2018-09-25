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

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.batchFlush;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.flushBeforeEnd;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.flushOnEach;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.flushOnReadComplete;

@Ignore("TODO: Re-enable once we can create an offloading Publisher")
@RunWith(Parameterized.class)
public class FlushWithExecutorTest extends AbstractFlushTest {

    private static final String[] data = new String[]{"1", "2", "3", "4"};

    @Rule
    public final ExecutionContextRule contextRule = cached();

    private FlushStrategyAndVerifier flushStrategyAndVerifier;

    public FlushWithExecutorTest(final FlushStrategyAndVerifier flushStrategyAndVerifier) {
        this.flushStrategyAndVerifier = flushStrategyAndVerifier;
    }

    @Parameterized.Parameters(name = "{index}: flushStrategy = {0}")
    public static Collection<FlushStrategyAndVerifier> data() {
        List<FlushStrategyAndVerifier> params = new ArrayList<>();
        params.add(flushOnEach());
        params.add(flushBeforeEnd(data.length));
        params.add(batchFlush(2));
        params.add(flushOnReadComplete());
        return params;
    }

    @Test
    public void testFlushBeforeEnd() throws ExecutionException, InterruptedException {
        Publisher<String> source = from(contextRule.executor(), data).map(String::valueOf);
        Publisher<String> flushSource = setup(flushStrategyAndVerifier.getFlushStrategy().apply(source));
        awaitIndefinitely(flushSource);
        int index = 0;
        for (String datum : data) {
            verifyWrite(datum);
            if (flushStrategyAndVerifier.getExpectFlushAtIndex().test(index)) {
                index++;
                verifyFlush();
            }
        }
        verifyFlushSignalListenerRemoved();
    }
}
