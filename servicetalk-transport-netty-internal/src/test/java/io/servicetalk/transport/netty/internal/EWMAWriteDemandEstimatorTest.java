/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class EWMAWriteDemandEstimatorTest {
    private static final String DUMMY_OBJECT = "dummy";
    @Test
    public void testNoRequestIfLowCapacity() {
        EWMAWriteDemandEstimator supplier = new EWMAWriteDemandEstimator();
        assertThat("Unexpected request-n.", supplier.getRequestNForCapacity(2), is(0L));
    }

    @Test
    public void testRequestNNoRecord() {
        EWMAWriteDemandEstimator supplier = new EWMAWriteDemandEstimator();
        assertThat("Unexpected requestN.", supplier.estimateRequestN(8), is(1L));
    }

    @Test
    public void testRequestNWithRecord() {
        EWMAWriteDemandEstimator supplier = new EWMAWriteDemandEstimator();
        supplier.onItemWrite(DUMMY_OBJECT, 1, 2);
        supplier.onItemWrite(DUMMY_OBJECT, 3, 5);
        assertThat("Unexpected requestN.", supplier.estimateRequestN(5), is(1L));
    }

    @Test
    public void testRepeatRequestNSameMaxSize() {
        EWMAWriteDemandEstimator supplier = new EWMAWriteDemandEstimator(5);
        assertThat("Unexpected requestN.", supplier.estimateRequestN(5), is(1L));
        supplier.onItemWrite(DUMMY_OBJECT, 100, 99);
        supplier.onItemWrite(DUMMY_OBJECT, 99, 92);
        assertThat("Unexpected requestN.", supplier.estimateRequestN(5), is(1L));
        assertThat("Unexpected requestN.", supplier.estimateRequestN(5), is(0L));
    }

    @Test
    public void testMultipleOnItemWrittenWithIncreaseInCapacity() {
        EWMAWriteDemandEstimator supplier = new EWMAWriteDemandEstimator(3);
        assertThat("Unexpected requestN.", supplier.estimateRequestN(10), is(3L));
        supplier.onItemWrite(DUMMY_OBJECT, 100, 99);
        supplier.onItemWrite(DUMMY_OBJECT, 99, 92);
        assertThat("Unexpected requestN.", supplier.estimateRequestN(92), is(27L));
    }

    @Test
    public void testZeroSizeWrite() {
        EWMAWriteDemandEstimator supplier = new EWMAWriteDemandEstimator(1);
        assertThat("Unexpected requestN.", supplier.estimateRequestN(10), is(10L));
        for (int i = 0; i < 1000; ++i) {
            supplier.onItemWrite(DUMMY_OBJECT, 100, 100);
        }
        assertThat("Unexpected requestN.", supplier.estimateRequestN(10), is(10L));
    }

    @Test
    public void weightMovesToSteadyState() {
        EWMAWriteDemandEstimator supplier = new EWMAWriteDemandEstimator(10);
        assertThat("Unexpected requestN.", supplier.estimateRequestN(10), is(1L));
        supplier.onItemWrite(DUMMY_OBJECT, 100, 98);
        supplier.onItemWrite(DUMMY_OBJECT, 98, 96);
        supplier.onItemWrite(DUMMY_OBJECT, 96, 94);
        supplier.onItemWrite(DUMMY_OBJECT, 96, 94);
        supplier.onItemWrite(DUMMY_OBJECT, 94, 92);
        supplier.onItemWrite(DUMMY_OBJECT, 92, 90);
        // default memory of 5 so by this time the size should be set to 2
        assertThat("Unexpected requestN.", supplier.estimateRequestN(10), is(5L));
    }
}
