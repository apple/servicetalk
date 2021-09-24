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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ObjLongConsumer;

import static io.servicetalk.transport.netty.internal.OverlappingCapacityAwareEstimator.SizeEstimator.defaultEstimator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class OverlappingCapacityAwareEstimatorTest {
    @Test
    void testRequestNNoItemWrite() {
        LongFunc longFunc = mock(LongFunc.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> 100L);
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc);
        requestNAndVerify(supplier, 10, 100L);
        verify(longFunc).apply(10);
        verifyNoMoreInteractions(longFunc);
    }

    @Test
    void testSupplyLessThanDemandNoCapacityChange() {
        LongFunc longFunc = mock(LongFunc.class);
        @SuppressWarnings("unchecked")
        ObjLongConsumer<Object> longConsume = mock(ObjLongConsumer.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> 100L);
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc, longConsume);
        requestNAndVerify(supplier, 10, 100L);
        verify(longFunc).apply(10);
        supplier.onItemWrite(1, 10, 5);
        verify(longConsume).accept(1, 5);
        requestNAndVerify(supplier, 10, 0L);
        verifyNoMoreInteractions(longFunc);
        verifyNoMoreInteractions(longConsume);
    }

    @Test
    void testSupplyLessThanDemandCapacityIncrease() {
        AtomicLong toRequest = new AtomicLong(2);
        LongFunc longFunc = mock(LongFunc.class);
        @SuppressWarnings("unchecked")
        ObjLongConsumer<Object> longConsume = mock(ObjLongConsumer.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> toRequest.get());
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc, longConsume);
        requestNAndVerify(supplier, 10, 2L);
        verify(longFunc).apply(10);
        supplier.onItemWrite(1, 10, 5);
        verify(longConsume).accept(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 15, 1L);
        verify(longFunc).apply(5);
        verifyNoMoreInteractions(longFunc);
        verifyNoMoreInteractions(longConsume);
    }

    @Test
    void testSupplyLessThanDemandCapacityDecrease() {
        AtomicLong toRequest = new AtomicLong(2);
        LongFunc longFunc = mock(LongFunc.class);
        @SuppressWarnings("unchecked")
        ObjLongConsumer<Object> longConsume = mock(ObjLongConsumer.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> toRequest.get());
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc, longConsume);
        requestNAndVerify(supplier, 10, 2L);
        verify(longFunc).apply(10);
        supplier.onItemWrite(1, 10, 5);
        verify(longConsume).accept(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 5, 0L);
        verifyNoMoreInteractions(longFunc);
        verifyNoMoreInteractions(longConsume);
    }

    @Test
    void testSupplyEqualsThanDemandCapacityIncrease() {
        AtomicLong toRequest = new AtomicLong(1);
        LongFunc longFunc = mock(LongFunc.class);
        @SuppressWarnings("unchecked")
        ObjLongConsumer<Object> longConsume = mock(ObjLongConsumer.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> toRequest.get());
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc, longConsume);
        requestNAndVerify(supplier, 10, 1L);
        verify(longFunc).apply(10);
        supplier.onItemWrite(1, 10, 5);
        verify(longConsume).accept(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 15, 1L);
        verify(longFunc).apply(15);
        verifyNoMoreInteractions(longFunc);
        verifyNoMoreInteractions(longConsume);
    }

    @Test
    void testSupplyEqualsThanDemandCapacityDecrease() {
        AtomicLong toRequest = new AtomicLong(1);
        LongFunc longFunc = mock(LongFunc.class);
        @SuppressWarnings("unchecked")
        ObjLongConsumer<Object> longConsume = mock(ObjLongConsumer.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> toRequest.get());
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc, longConsume);
        requestNAndVerify(supplier, 10, 1L);
        verify(longFunc).apply(10);
        supplier.onItemWrite(1, 10, 5);
        verify(longConsume).accept(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 5, 1L);
        verify(longFunc).apply(5);
        verifyNoMoreInteractions(longFunc);
        verifyNoMoreInteractions(longConsume);
    }

    @Test
    void testSupplyEqualsDemandNoCapacityChange() {
        LongFunc longFunc = mock(LongFunc.class);
        @SuppressWarnings("unchecked")
        ObjLongConsumer<Object> longConsume = mock(ObjLongConsumer.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> 1L);
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc, longConsume);
        requestNAndVerify(supplier, 10, 1L);
        verify(longFunc).apply(10);
        supplier.onItemWrite(1, 10, 5);
        verify(longConsume).accept(1, 5);
        requestNAndVerify(supplier, 10, 1L);
        verify(longFunc, times(2)).apply(10);
        verifyNoMoreInteractions(longFunc);
        verifyNoMoreInteractions(longConsume);
    }

    @Test
    void testNegativeSize() {
        LongFunc longFunc = mock(LongFunc.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> 2L);
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc);
        requestNAndVerify(supplier, 10, 2L);
        verify(longFunc).apply(10);
        supplier.onItemWrite(1, 10, 11);
        verifyNoMoreInteractions(longFunc);
        requestNAndVerify(supplier, 10, 0L);
    }

    @Test
    void testPredictionZeroAndNoOutstanding() {
        AtomicLong toRequest = new AtomicLong(0);
        LongFunc longFunc = mock(LongFunc.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> toRequest.get());
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc);
        requestNAndVerify(supplier, 10, 1L);
        verify(longFunc).apply(10);
        verifyNoMoreInteractions(longFunc);
    }

    @Test
    void testPredictionZeroAndSomeOutstanding() {
        AtomicLong toRequest = new AtomicLong(5);
        LongFunc longFunc = mock(LongFunc.class);
        when(longFunc.apply(anyLong())).thenAnswer(invocation -> toRequest.get());
        OverlappingCapacityAwareEstimator supplier = new TestOverlappingCapacityAwareEstimator(longFunc);
        requestNAndVerify(supplier, 10, 5L);
        verify(longFunc).apply(10);

        toRequest.set(0);
        requestNAndVerify(supplier, 10, 0L);
        verifyNoMoreInteractions(longFunc);
    }

    private static void requestNAndVerify(OverlappingCapacityAwareEstimator supplier, int writeBufferCapacityInBytes,
                                          long expectedRequestN) {
        long requestN = supplier.estimateRequestN(writeBufferCapacityInBytes);
        assertThat("Unexpected requestN", requestN, is(expectedRequestN));
    }

    private interface LongFunc {
        long apply(long foo);
    }

    private static final class TestOverlappingCapacityAwareEstimator extends OverlappingCapacityAwareEstimator {
        private final LongFunc func;
        private final ObjLongConsumer<Object> recordSize;

        TestOverlappingCapacityAwareEstimator(final LongFunc func) {
            this(func, (o, s) -> { });
        }

        TestOverlappingCapacityAwareEstimator(final LongFunc func,
                                              final ObjLongConsumer<Object> recordSize) {
            super(defaultEstimator());
            this.func = func;
            this.recordSize = recordSize;
        }

        @Override
        protected void recordSize(final Object written, final long sizeInBytes) {
            recordSize.accept(written, sizeInBytes);
        }

        @Override
        protected long getRequestNForCapacity(final long capacityToFill) {
            return func.apply(capacityToFill);
        }
    }
}
