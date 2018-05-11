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
import io.servicetalk.transport.api.FlushStrategyHolder;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.batchFlush;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.flushBeforeEnd;
import static io.servicetalk.transport.netty.internal.FlushStrategyAndVerifier.flushOnEach;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Ignore("TODO: Re-enable once we can create an offloading Publisher")
@RunWith(Parameterized.class)
public class FlushStrategiesWithExecutorTest {

    private static final String[] data = new String[]{"1", "2", "3", "4"};
    private static final String FLUSHED = "flushed";

    @Rule
    public final ExecutionContextRule contextRule = cached();

    private FlushStrategyAndVerifier flushStrategyAndVerifier;

    public FlushStrategiesWithExecutorTest(final FlushStrategyAndVerifier flushStrategyAndVerifier) {
        this.flushStrategyAndVerifier = flushStrategyAndVerifier;
    }

    @Parameterized.Parameters(name = "{index}: flushStrategy = {0}")
    public static Collection<FlushStrategyAndVerifier> data() {
        List<FlushStrategyAndVerifier> params = new ArrayList<>();
        params.add(flushOnEach());
        params.add(flushBeforeEnd(data.length));
        params.add(batchFlush(2));
        return params;
    }

    @Test
    public void flushIsOrdered() throws InterruptedException {
        Publisher<String> source = from(contextRule.getExecutor(), 1, 2, 3, 4).map(String::valueOf);
        FlushStrategyHolder<String> holder = flushStrategyAndVerifier.getFlushStrategy().apply(source);
        CountDownLatch awaitTerminal = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> items = new ConcurrentLinkedQueue<>();
        holder.getSource().doBeforeNext(items::add).ignoreElements().doAfterFinally(awaitTerminal::countDown).subscribe();
        holder.getFlushSignals().listen(() -> items.add(FLUSHED));
        awaitTerminal.await();
        int index = 0;
        int dataIndex = 0;
        for (String item : items) {
            assertThat("Unexpected item at index: " + index + " for items: " + items, item,
                    is(flushStrategyAndVerifier.getExpectFlushAtIndex().test(index) ? FLUSHED : data[dataIndex++]));
            index++;
        }
    }
}
