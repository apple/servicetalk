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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.IoExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.redis.api.RedisData.NULL;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class RedisIdleConnectionReaperTest {
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public final MockedSubscriberRule<RedisData> requestSubscriber = new MockedSubscriberRule<>();

    @Rule
    public final MockedSingleListenerRule<String> commandSubscriber = new MockedSingleListenerRule<>();

    @Mock
    private IoExecutor ioExecutor;

    @Mock
    private RedisConnection delegateConnection;

    @Mock
    private ConnectionContext connectionContext;

    private CompletableProcessor delegateConnectionOnCloseCompletable;

    private final AtomicReference<CompletableProcessor> timerCompletableRef = new AtomicReference<>();

    private final AtomicBoolean timerCancelled = new AtomicBoolean();

    private RedisConnection idleAwareConnection;

    @Before
    public void setup() {
        reset(delegateConnection);
        reset(connectionContext);
        delegateConnectionOnCloseCompletable = new CompletableProcessor();
        when(delegateConnection.closeAsync()).thenReturn(delegateConnectionOnCloseCompletable);
        when(delegateConnection.onClose()).thenReturn(delegateConnectionOnCloseCompletable);
        when(delegateConnection.request(any(RedisRequest.class))).thenReturn(just(NULL));
        when(delegateConnection.getConnectionContext()).thenReturn(connectionContext);
        when(connectionContext.getIoExecutor()).thenReturn(ioExecutor);
        when(ioExecutor.timer(any(Long.class), any(TimeUnit.class))).then($ -> {
            final CompletableProcessor timerCompletable = new CompletableProcessor();
            timerCancelled.set(false);
            timerCompletableRef.set(timerCompletable);
            return timerCompletable.doBeforeCancel(() -> timerCancelled.set(true));
        });

        idleAwareConnection = new RedisIdleConnectionReaper(Duration.ofSeconds(1)).apply(delegateConnection);

        verify(delegateConnection).onClose();
        verify(ioExecutor).timer(1_000_000_000L, NANOSECONDS);
        verify(delegateConnection).getConnectionContext();
        verify(connectionContext).getIoExecutor();
    }

    @After
    public void verifyMocks() {
        verifyNoMoreInteractions(ioExecutor);
        verifyNoMoreInteractions(delegateConnection);
        verifyNoMoreInteractions(connectionContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void lessThanOneSecondTimeoutIsRejected() {
        new RedisIdleConnectionReaper(Duration.ofMillis(100));
    }

    @Test
    public void connectionCloseCancelsTimer() {
        delegateConnectionOnCloseCompletable.onComplete();
        assertThat(timerCancelled.get(), is(true));
    }

    @Test
    public void neverUsedConnectionIdles() {
        completeTimer();
        verify(ioExecutor).timer(1_000_000_000L, NANOSECONDS);
        verify(delegateConnection).closeAsync();
    }

    @Test
    public void connectionWithActiveRequestNeverIdles() {
        requestSubscriber.subscribe(idleAwareConnection.request(newRequest(PING)))
                .verifySubscribe()
                .verifyNoEmissions();

        completeTimer();
        completeTimer();
        verify(ioExecutor, times(3)).timer(1_000_000_000L, NANOSECONDS);
        verify(delegateConnection, times(3)).getConnectionContext();
        verify(connectionContext, times(3)).getIoExecutor();
        verify(delegateConnection).request(any(RedisRequest.class));
    }

    @Test
    public void connectionIdlesAfterFinishedRequest() {
        requestSubscriber.subscribe(idleAwareConnection.request(newRequest(PING)))
                .verifySubscribe()
                .request(1)
                .verifySuccess();

        completeTimer();
        completeTimer();
        verify(ioExecutor, times(2)).timer(1_000_000_000L, NANOSECONDS);
        verify(delegateConnection, times(2)).getConnectionContext();
        verify(connectionContext, times(2)).getIoExecutor();
        verify(delegateConnection).request(any(RedisRequest.class));
        verify(delegateConnection).closeAsync();
    }

    @Test
    public void commanderRequestsAreInstrumented() {
        when(delegateConnection.getBufferAllocator()).thenReturn(BufferAllocators.DEFAULT.getAllocator());
        when(delegateConnection.request(any(RedisRequest.class), eq(String.class))).thenReturn(success("pong"));

        commandSubscriber.listen(idleAwareConnection.asCommander().ping())
            .verifySuccess("pong");

        completeTimer();
        verify(ioExecutor, times(2)).timer(1_000_000_000L, NANOSECONDS);
        verify(delegateConnection, times(2)).getConnectionContext();
        verify(connectionContext, times(2)).getIoExecutor();
        verify(delegateConnection).getBufferAllocator();
        verify(delegateConnection).request(any(RedisRequest.class), eq(String.class));
    }

    private void completeTimer() {
        timerCompletableRef.get().onComplete();
    }
}
