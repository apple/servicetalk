/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.LegacyMockedCompletableListenerRule;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConnectionAcceptorTest {
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final LegacyMockedCompletableListenerRule listener = new LegacyMockedCompletableListenerRule();

    @Mock
    private ConnectionContext context;

    @Spy
    private ConnectionAcceptor first;
    @Spy
    private ConnectionAcceptor second;

    @Test
    public void factoryAppend() throws Exception {
        ConnectionAcceptorFactory f = ConnectionAcceptorFactory.identity();
        ConcurrentLinkedQueue<Integer> order = new ConcurrentLinkedQueue<>();
        f.append(original -> new OrderVerifyingConnectionAcceptor(original, order, 1))
                .append(original -> new OrderVerifyingConnectionAcceptor(original, order, 2))
                .append(original -> new OrderVerifyingConnectionAcceptor(original, order, 3))
                .create(ACCEPT_ALL).accept(context).toFuture().get();
        assertThat("Unexpected filter order.", order, contains(1, 2, 3));
    }

    @Test
    public void chainingCompletedThenCompletedShouldReturnTrue() {
        setFilterResult(first, Completable.completed());
        setFilterResult(second, Completable.completed());

        applyFilters();
        listener.verifyCompletion();

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingCompletedThenErrorShouldReturnError() {
        setFilterResult(first, Completable.completed());
        setFilterResult(second, Completable.error(DELIBERATE_EXCEPTION));

        applyFilters();
        listener.verifyFailure(DeliberateException.class);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingAfterErrorShouldNotCallNextFilter() {
        setFilterResult(first, Completable.error(DELIBERATE_EXCEPTION));

        applyFilters();
        listener.verifyFailure(DeliberateException.class);

        verify(first).accept(context);
        verify(second, never()).accept(any(ConnectionContext.class));
    }

    private void setFilterResult(final ConnectionAcceptor filter, final Completable resultCompletable) {
        when(filter.accept(context)).thenReturn(resultCompletable);
    }

    @Nonnull
    protected void applyFilters() {
        ConnectionAcceptorFactory f = (original -> original.append(ctx -> second.accept(ctx)));
        f = f.append(original -> original.append(ctx -> first.accept(ctx)));
        listener.listen(f.create(ACCEPT_ALL).accept(context));
    }

    private static class OrderVerifyingConnectionAcceptor extends DelegatingConnectionAcceptor {
        private final ConcurrentLinkedQueue<Integer> order;
        private final int index;

        OrderVerifyingConnectionAcceptor(final ConnectionAcceptor original, final ConcurrentLinkedQueue<Integer> order,
                                         final int index) {
            super(original);
            this.order = order;
            this.index = index;
        }

        @Override
        public Completable accept(final ConnectionContext context) {
            order.add(index);
            return super.accept(context);
        }
    }
}
