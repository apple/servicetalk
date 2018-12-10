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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.Single;
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
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConnectionAcceptorTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final MockedSingleListenerRule<Boolean> listener = new MockedSingleListenerRule<>();

    @Mock
    private ConnectionContext context;

    @Spy
    private ConnectionAcceptor first;
    @Spy
    private ConnectionAcceptor second;

    @Test
    public void factoryAppend() throws Exception {
        ConnectionAcceptorFilterFactory f = ConnectionAcceptorFilterFactory.identity();
        ConcurrentLinkedQueue<Integer> order = new ConcurrentLinkedQueue<>();
        f.append(original -> new OrderVerifyingConnectionAcceptorFilter(original, order, 1))
                .append(original -> new OrderVerifyingConnectionAcceptorFilter(original, order, 2))
                .append(original -> new OrderVerifyingConnectionAcceptorFilter(original, order, 3))
                .apply(ACCEPT_ALL).accept(context).toFuture().get();
        assertThat("Unexpected filter order.", order, contains(1, 2, 3));
    }

    @Test
    public void chainingTrueThenTrueShouldReturnTrue() {
        setFilterResult(first, Single.success(true));
        setFilterResult(second, Single.success(true));

        applyFilters();
        listener.verifySuccess(TRUE);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingTrueThenFalseShouldReturnFalse() {
        setFilterResult(first, Single.success(true));
        setFilterResult(second, Single.success(false));

        applyFilters();
        listener.verifySuccess(FALSE);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingTrueThenErrorShouldReturnError() {
        setFilterResult(first, Single.success(true));
        setFilterResult(second, Single.error(DELIBERATE_EXCEPTION));

        applyFilters();
        listener.verifyFailure(DeliberateException.class);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingAfterFalseShouldCallNextFilter() {
        setFilterResult(first, Single.success(false));
        setFilterResult(second, Single.success(true));

        applyFilters();
        listener.verifySuccess(TRUE);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingAfterNullShouldCallNextFilter() {
        setFilterResult(first, Single.success(null));
        setFilterResult(second, Single.success(true));

        applyFilters();
        listener.verifySuccess(TRUE);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingAfterErrorShouldNotCallNextFilter() {
        setFilterResult(first, Single.error(DELIBERATE_EXCEPTION));

        applyFilters();
        listener.verifyFailure(DeliberateException.class);

        verify(first).accept(context);
        verify(second, never()).accept(any(ConnectionContext.class));
    }

    private void setFilterResult(final ConnectionAcceptor filter, final Single<Boolean> resultSingle) {
        when(filter.accept(context)).thenReturn(resultSingle);
    }

    @Nonnull
    protected void applyFilters() {
        ConnectionAcceptorFilterFactory f = (original -> new ConnectionAcceptorFilter(original, (ctx, prevResult) -> second.accept(ctx)));
        f = f.append(original -> new ConnectionAcceptorFilter(original, (ctx, prevResult) -> first.accept(ctx)));
        listener.listen(f.apply(ACCEPT_ALL).accept(context));
    }

    private static class OrderVerifyingConnectionAcceptorFilter extends ConnectionAcceptorFilter {
        private final ConcurrentLinkedQueue<Integer> order;
        private final int index;

        OrderVerifyingConnectionAcceptorFilter(final ConnectionAcceptor original, final ConcurrentLinkedQueue<Integer> order,
                                               final int index) {
            super(original);
            this.order = order;
            this.index = index;
        }

        @Override
        public Single<Boolean> accept(final ConnectionContext context) {
            order.add(index);
            return super.accept(context);
        }
    }
}
