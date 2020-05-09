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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class DefaultBlockingIterableProcessorTest {
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    private final BlockingIterable.Processor<Integer> processor;

    public DefaultBlockingIterableProcessorTest() {
        processor = Processors.newBlockingIterableProcessor(2);
    }

    @Test
    public void emitBuffersNoIterator() throws Exception {
        processor.next(1);
        processor.next(2);
        BlockingIterator<Integer> iterator = processor.iterator();
        assertThat("Unexpected item received.", iterator.next(), is(1));
        assertThat("Unexpected item received.", iterator.next(), is(2));
        processor.close();
        assertThat("Unexpected hasNext() response.", iterator.hasNext(), is(false));
    }

    @Test
    public void emitBuffersNoDemand() throws Exception {
        BlockingIterator<Integer> iterator = processor.iterator();
        processor.next(1);
        processor.next(2);
        assertThat("Unexpected item received.", iterator.next(), is(1));
        assertThat("Unexpected item received.", iterator.next(), is(2));
        processor.close();
        assertThat("Unexpected hasNext() response.", iterator.hasNext(), is(false));
    }

    @Test
    public void hasNextTimesout() throws TimeoutException {
        BlockingIterator<Integer> iterator = processor.iterator();
        expectedException.expect(instanceOf(TimeoutException.class));
        iterator.hasNext(1, TimeUnit.SECONDS);
    }

    @Test
    public void nextTimesout() throws TimeoutException {
        BlockingIterator<Integer> iterator = processor.iterator();
        expectedException.expect(instanceOf(TimeoutException.class));
        iterator.next(1, TimeUnit.SECONDS);
    }

    @Test
    public void emitNull() throws Exception {
        BlockingIterator<Integer> iterator = processor.iterator();
        processor.next(null);
        assertThat("Unexpected item received.", iterator.next(), is(nullValue()));
    }

    @Test
    public void iteratorCloseAfterProcessorTermination() throws Exception {
        BlockingIterator<Integer> iterator = processor.iterator();
        processor.close();
        iterator.close();
        expectedException.expect(instanceOf(CancellationException.class));
        iterator.hasNext();
    }

    @Test
    public void iteratorCloseAfterProcessorFail() throws Exception {
        BlockingIterator<Integer> iterator = processor.iterator();
        processor.fail(DELIBERATE_EXCEPTION);
        iterator.close();
        expectedException.expect(instanceOf(CancellationException.class));
        iterator.hasNext();
    }

    @Test
    public void postIteratorCloseHasNextThrows() throws Exception {
        BlockingIterator<Integer> iterator = processor.iterator();
        iterator.close();
        expectedException.expect(instanceOf(CancellationException.class));
        iterator.hasNext();
    }

    @Test
    public void postIteratorCloseNextThrows() throws Exception {
        BlockingIterator<Integer> iterator = processor.iterator();
        iterator.close();
        expectedException.expect(instanceOf(CancellationException.class));
        iterator.next();
    }
}
