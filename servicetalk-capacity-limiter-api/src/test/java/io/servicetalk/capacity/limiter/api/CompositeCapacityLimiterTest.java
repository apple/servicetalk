/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.capacity.limiter.api;

import io.servicetalk.capacity.limiter.api.CapacityLimiter.Ticket;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeCapacityLimiterTest {

    private final CapacityLimiter a = mock(CapacityLimiter.class);
    private final CapacityLimiter b = mock(CapacityLimiter.class);
    private final Ticket ticketA = mock(Ticket.class);
    private final Ticket ticketB = mock(Ticket.class);
    private final CapacityLimiter composite = new CompositeCapacityLimiter(Arrays.asList(a, b));

    @Test
    void allDenyReturnsNull() {
        when(a.tryAcquire(any(), any())).thenReturn(null);
        when(b.tryAcquire(any(), any())).thenReturn(null);
        assertThat(composite.tryAcquire(() -> 0, null), nullValue());
    }

    @Test
    void firstDeniesReturnsNullWithoutConsultingRest() {
        when(a.tryAcquire(any(), any())).thenReturn(null);
        assertThat(composite.tryAcquire(() -> 0, null), nullValue());
        verify(b, never()).tryAcquire(any(), any());
    }

    @Test
    void laterDenialReleasesPriorGrants() {
        when(a.tryAcquire(any(), any())).thenReturn(ticketA);
        when(b.tryAcquire(any(), any())).thenReturn(null);
        assertThat(composite.tryAcquire(() -> 0, null), nullValue());
        verify(ticketA).completed();
    }

    @Test
    void allAllowReturnsComposite() {
        when(a.tryAcquire(any(), any())).thenReturn(ticketA);
        when(b.tryAcquire(any(), any())).thenReturn(ticketB);
        when(ticketB.state()).thenReturn(mock(CapacityLimiter.LimiterState.class));

        Ticket result = composite.tryAcquire(() -> 0, null);
        assertThat(result, notNullValue());
        assertThat(result.state(), notNullValue());
    }
}
