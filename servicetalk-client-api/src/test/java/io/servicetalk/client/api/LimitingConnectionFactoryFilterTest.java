/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.ConnectException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LimitingConnectionFactoryFilterTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();
    @Rule
    public final LegacyMockedSingleListenerRule<ListenableAsyncCloseable> connectlistener =
            new LegacyMockedSingleListenerRule<>();

    private ConnectionFactory<String, ListenableAsyncCloseable> original;
    private BlockingQueue<Processor> connectionOnClose;

    @Before
    public void setUp() {
        original = newMockConnectionFactory();
        connectionOnClose = new LinkedBlockingQueue<>();
        when(original.newConnection(any(), any())).thenAnswer(invocation -> {
            ListenableAsyncCloseable conn = mock(ListenableAsyncCloseable.class);
            Processor onClose = newCompletableProcessor();
            connectionOnClose.add(onClose);
            when(conn.onClose()).thenReturn(fromSource(onClose));
            return succeeded(conn);
        });
    }

    private ConnectionFactory<String, ? extends ListenableAsyncCloseable> makeCF(
            ConnectionFactoryFilter<String, ListenableAsyncCloseable> filter,
            ConnectionFactory<String, ListenableAsyncCloseable> connection) {
        return filter.create(connection);
    }

    @Test
    public void enforceMaxConnections() throws Exception {
        ConnectionFactory<String, ? extends ListenableAsyncCloseable> cf =
                makeCF(LimitingConnectionFactoryFilter.withMax(1), original);
        cf.newConnection("c1", null).toFuture().get();
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(instanceOf(ConnectException.class));
        cf.newConnection("c2", null).toFuture().get();
    }

    @Test
    public void onCloseReleasesPermit() throws Exception {
        ConnectionFactory<String, ? extends ListenableAsyncCloseable> cf =
                makeCF(LimitingConnectionFactoryFilter.withMax(1), original);
        cf.newConnection("c1", null).toFuture().get();
        connectAndVerifyFailed(cf);
        connectionOnClose.take().onComplete();
        cf.newConnection("c3", null).toFuture().get();
    }

    @Test
    public void cancelReleasesPermit() throws Exception {
        ConnectionFactory<String, ListenableAsyncCloseable> o = newMockConnectionFactory();
        when(o.newConnection(any(), any())).thenReturn(never());
        ConnectionFactory<String, ? extends ListenableAsyncCloseable> cf =
                makeCF(LimitingConnectionFactoryFilter.withMax(1), o);
        connectlistener.listen(cf.newConnection("c1", null)).verifyNoEmissions();
        connectAndVerifyFailed(cf);
        connectlistener.cancel();

        ListenableAsyncCloseable c = mock(ListenableAsyncCloseable.class);
        when(c.onClose()).thenReturn(Completable.never());
        when(o.newConnection(any(), any())).thenReturn(succeeded(c));
        cf.newConnection("c2", null).toFuture().get();
    }

    private static void connectAndVerifyFailed(final ConnectionFactory<String, ? extends ListenableAsyncCloseable> cf)
            throws Exception {
        try {
            cf.newConnection("c-fail", null).toFuture().get();
            fail("Connect expected to fail.");
        } catch (ExecutionException e) {
            assertThat("Unexpected exception.", e.getCause(), instanceOf(ConnectException.class));
        }
    }

    @SuppressWarnings("unchecked")
    private static ConnectionFactory<String, ListenableAsyncCloseable> newMockConnectionFactory() {
        return (ConnectionFactory<String, ListenableAsyncCloseable>) mock(ConnectionFactory.class);
    }
}
