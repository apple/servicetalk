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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static io.servicetalk.transport.netty.internal.OverlappingCapacityAwareEstimator.SizeEstimator.defaultEstimator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class OverlappingCapacityAwareEstimatorTest {

    @Test
    public void testRequestNNoItemWrite() {
        OverlappingCapacityAwareEstimator supplier = newSupplier(() -> 100);
        requestNAndVerify(supplier, 10, 100L);
        verify(supplier).getRequestNForCapacity(10);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testSupplyLessThanDemandNoCapacityChange() {
        OverlappingCapacityAwareEstimator supplier = newSupplier(() -> 100);
        requestNAndVerify(supplier, 10, 100L);
        verify(supplier).getRequestNForCapacity(10);
        supplier.onItemWrite(1, 10, 5);
        verify(supplier).recordSize(1, 5);
        requestNAndVerify(supplier, 10, 0L);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testSupplyLessThanDemandCapacityIncrease() {
        AtomicLong toRequest = new AtomicLong(2);
        OverlappingCapacityAwareEstimator supplier = newSupplier(toRequest::get);
        requestNAndVerify(supplier, 10, 2L);
        verify(supplier).getRequestNForCapacity(10);
        supplier.onItemWrite(1, 10, 5);
        verify(supplier).recordSize(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 15, 1L);
        verify(supplier).getRequestNForCapacity(5);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testSupplyLessThanDemandCapacityDecrease() {
        AtomicLong toRequest = new AtomicLong(2);
        OverlappingCapacityAwareEstimator supplier = newSupplier(toRequest::get);
        requestNAndVerify(supplier, 10, 2L);
        verify(supplier).getRequestNForCapacity(10);
        supplier.onItemWrite(1, 10, 5);
        verify(supplier).recordSize(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 5, 0L);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testSupplyEqualsThanDemandCapacityIncrease() {
        AtomicLong toRequest = new AtomicLong(1);
        OverlappingCapacityAwareEstimator supplier = newSupplier(toRequest::get);
        requestNAndVerify(supplier, 10, 1L);
        verify(supplier).getRequestNForCapacity(10);
        supplier.onItemWrite(1, 10, 5);
        verify(supplier).recordSize(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 15, 1L);
        verify(supplier).getRequestNForCapacity(15);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testSupplyEqualsThanDemandCapacityDecrease() {
        AtomicLong toRequest = new AtomicLong(1);
        OverlappingCapacityAwareEstimator supplier = newSupplier(toRequest::get);
        requestNAndVerify(supplier, 10, 1L);
        verify(supplier).getRequestNForCapacity(10);
        supplier.onItemWrite(1, 10, 5);
        verify(supplier).recordSize(1, 5);
        toRequest.set(1);
        requestNAndVerify(supplier, 5, 1L);
        verify(supplier).getRequestNForCapacity(5);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testSupplyEqualsDemandNoCapacityChange() {
        OverlappingCapacityAwareEstimator supplier = newSupplier(() -> 1);
        requestNAndVerify(supplier, 10, 1L);
        verify(supplier).getRequestNForCapacity(10);
        supplier.onItemWrite(1, 10, 5);
        verify(supplier).recordSize(1, 5);
        requestNAndVerify(supplier, 10, 1L);
        verify(supplier, times(2)).getRequestNForCapacity(10);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testNegativeSize() {
        OverlappingCapacityAwareEstimator supplier = newSupplier(() -> 2);
        requestNAndVerify(supplier, 10, 2L);
        verify(supplier).getRequestNForCapacity(10);
        supplier.onItemWrite(1, 10, 11);
        verifyNoMoreInteractions(supplier);
        requestNAndVerify(supplier, 10, 0L);
    }

    @Test
    public void testPredictionZeroAndNoOutstanding() {
        AtomicLong toRequest = new AtomicLong(0);
        OverlappingCapacityAwareEstimator supplier = newSupplier(toRequest::get);
        requestNAndVerify(supplier, 10, 1L);
        verify(supplier).getRequestNForCapacity(10);
        verifyNoMoreInteractions(supplier);
    }

    @Test
    public void testPredictionZeroAndSomeOutstanding() {
        AtomicLong toRequest = new AtomicLong(5);
        OverlappingCapacityAwareEstimator supplier = newSupplier(toRequest::get);
        requestNAndVerify(supplier, 10, 5L);
        verify(supplier).getRequestNForCapacity(10);

        toRequest.set(0);
        requestNAndVerify(supplier, 10, 0L);
        verifyNoMoreInteractions(supplier);
    }

    private static void requestNAndVerify(OverlappingCapacityAwareEstimator supplier, int writeBufferCapacityInBytes,
                                          long expectedRequestN) {
        long requestN = supplier.estimateRequestN(writeBufferCapacityInBytes);
        assertThat("Unexpected requestN", requestN, is(expectedRequestN));
    }

    private static OverlappingCapacityAwareEstimator newSupplier(LongSupplier demandEstimator) {
        OverlappingCapacityAwareEstimator mock = mock(OverlappingCapacityAwareEstimator.class,
                withSettings().useConstructor(defaultEstimator()).defaultAnswer(CALLS_REAL_METHODS));
        when(mock.getRequestNForCapacity(anyLong())).thenAnswer(invocation -> demandEstimator.getAsLong());
        return mock;
    }
}
