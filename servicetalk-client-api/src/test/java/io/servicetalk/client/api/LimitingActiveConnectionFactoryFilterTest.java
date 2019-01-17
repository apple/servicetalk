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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.client.api.LimitingActiveConnectionFactoryFilter.withMaxConnections;
import static io.servicetalk.concurrent.api.Single.never;
import static io.servicetalk.concurrent.api.Single.success;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LimitingActiveConnectionFactoryFilterTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();
    @Rule
    public final MockedSingleListenerRule<ListenableAsyncCloseable> connectlistener = new MockedSingleListenerRule<>();

    private ConnectionFactory<String, ListenableAsyncCloseable> original;
    private BlockingQueue<CompletableProcessor> connectionOnClose;

    @Before
    public void setUp() {
        original = newMockConnectionFactory();
        connectionOnClose = new LinkedBlockingQueue<>();
        when(original.newConnection(any())).thenAnswer(invocation -> {
            ListenableAsyncCloseable conn = mock(ListenableAsyncCloseable.class);
            CompletableProcessor onClose = new CompletableProcessor();
            connectionOnClose.add(onClose);
            when(conn.onClose()).thenReturn(onClose);
            return success(conn);
        });
    }

    @Test
    public void enforceMaxConnections() throws Exception {
        ConnectionFactory<String, ListenableAsyncCloseable> cf = withMaxConnections(original, 1);
        cf.newConnection("c1").toFuture().get();
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(instanceOf(RetryableConnectException.class));
        cf.newConnection("c2").toFuture().get();
    }

    @Test
    public void onCloseReleasesPermit() throws Exception {
        ConnectionFactory<String, ListenableAsyncCloseable> cf = withMaxConnections(original, 1);
        cf.newConnection("c1").toFuture().get();
        connectAndVerifyFailed(cf);
        connectionOnClose.take().onComplete();
        cf.newConnection("c3").toFuture().get();
    }

    @Test
    public void cancelReleasesPermit() throws Exception {
        ConnectionFactory<String, ListenableAsyncCloseable> o = newMockConnectionFactory();
        when(o.newConnection(any())).thenReturn(never());
        ConnectionFactory<String, ListenableAsyncCloseable> cf = withMaxConnections(o, 1);
        connectlistener.listen(cf.newConnection("c1")).verifyNoEmissions();
        connectAndVerifyFailed(cf);
        connectlistener.cancel();

        ListenableAsyncCloseable c = mock(ListenableAsyncCloseable.class);
        when(c.onClose()).thenReturn(Completable.never());
        when(o.newConnection(any())).thenReturn(success(c));
        cf.newConnection("c2").toFuture().get();
    }

    private static void connectAndVerifyFailed(final ConnectionFactory<String, ListenableAsyncCloseable> cf)
            throws Exception {
        try {
            cf.newConnection("c-fail").toFuture().get();
            fail("Connect expected to fail.");
        } catch (ExecutionException e) {
            assertThat("Unexpected exception.", e.getCause(), instanceOf(RetryableConnectException.class));
        }
    }

    @SuppressWarnings("unchecked")
    private static ConnectionFactory<String, ListenableAsyncCloseable> newMockConnectionFactory() {
        return (ConnectionFactory<String, ListenableAsyncCloseable>) mock(ConnectionFactory.class);
    }
}
