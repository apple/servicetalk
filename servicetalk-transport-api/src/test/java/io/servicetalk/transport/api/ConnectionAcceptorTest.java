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

import static io.servicetalk.concurrent.api.Single.success;
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
    public final MockitoRule rule = MockitoJUnit.rule();
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
        ConcurrentLinkedQueue<Integer> order = new ConcurrentLinkedQueue<>();
        ACCEPT_ALL.append(new OrderVerifyingConnectionAcceptor(order, 1))
                  .append(new OrderVerifyingConnectionAcceptor(order, 2))
                  .append(new OrderVerifyingConnectionAcceptor(order, 3))
                  .accept(context).toFuture().get();
        assertThat("Unexpected filter order.", order, contains(1, 2, 3));
    }

    @Test
    public void chainingTrueThenTrueShouldReturnTrue() {
        setFilterResult(first, success(true));
        setFilterResult(second, success(true));

        applyFilters();
        listener.verifySuccess(TRUE);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingTrueThenFalseShouldReturnFalse() {
        setFilterResult(first, success(true));
        setFilterResult(second, success(false));

        applyFilters();
        listener.verifySuccess(FALSE);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingTrueThenErrorShouldReturnError() {
        setFilterResult(first, success(true));
        setFilterResult(second, Single.error(DELIBERATE_EXCEPTION));

        applyFilters();
        listener.verifyFailure(DeliberateException.class);

        verify(first).accept(context);
        verify(second).accept(context);
    }

    @Test
    public void chainingAfterFalseShouldNotCallNextFilter() {
        setFilterResult(first, success(false));
        setFilterResult(second, success(true));

        applyFilters();
        listener.verifySuccess(FALSE);

        verify(first).accept(context);
        verify(second, never()).accept(context);
    }

    @Test
    public void chainingAfterNullShouldNotCallNextFilter() {
        setFilterResult(first, success(null));
        setFilterResult(second, success(true));

        applyFilters();
        listener.verifySuccess(null);

        verify(first).accept(context);
        verify(second, never()).accept(context);
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

    protected void applyFilters() {
        listener.listen(ACCEPT_ALL.append(first).append(second).accept(context));
    }

    private static final class OrderVerifyingConnectionAcceptor implements ConnectionAcceptor {
        private final ConcurrentLinkedQueue<Integer> order;
        private final int index;

        OrderVerifyingConnectionAcceptor(final ConcurrentLinkedQueue<Integer> order, final int index) {
            this.order = order;
            this.index = index;
        }

        @Override
        public Single<Boolean> accept(final ConnectionContext context) {
            order.add(index);
            return success(true);
        }
    }
}
