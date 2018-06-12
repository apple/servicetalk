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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.MockedSubscriberRule;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.NettyIoExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.redis.api.RedisData.NULL;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
    private NettyIoExecutor ioExecutor;

    @Mock
    private Executor mockExecutor;

    @Mock
    private RedisConnection delegateConnection;

    @Mock
    private ConnectionContext connectionContext;

    private CompletableProcessor delegateConnectionOnCloseCompletable;

    private final AtomicReference<Runnable> timerRunnableRef = new AtomicReference<>();

    private final AtomicBoolean timerCancelled = new AtomicBoolean();
    private final AtomicInteger timerSubscribed = new AtomicInteger();

    private RedisConnection idleAwareConnection;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        delegateConnectionOnCloseCompletable = new CompletableProcessor();
        when(delegateConnection.closeAsync()).thenReturn(delegateConnectionOnCloseCompletable);
        when(delegateConnection.onClose()).thenReturn(delegateConnectionOnCloseCompletable);
        when(delegateConnection.request(any(RedisRequest.class))).thenReturn(just(NULL));
        when(delegateConnection.getConnectionContext()).thenReturn(connectionContext);
        when(delegateConnection.getExecutionContext()).thenReturn(executionContext);
        when(executionContext.getBufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(connectionContext.getIoExecutor()).thenReturn(ioExecutor);
        when(ioExecutor.asExecutor()).thenReturn(mockExecutor);
        doAnswer((Answer<Cancellable>) invocationOnMock -> {
            timerRunnableRef.set(invocationOnMock.getArgument(0));
            timerSubscribed.incrementAndGet();
            timerCancelled.set(false);
            return () -> timerCancelled.set(true);
        }).when(mockExecutor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        when(mockExecutor.timer(anyLong(), any(TimeUnit.class))).thenCallRealMethod();

        idleAwareConnection = new RedisIdleConnectionReaper(Duration.ofSeconds(1)).apply(delegateConnection);

        verify(delegateConnection).onClose();
        verify(mockExecutor).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection).getConnectionContext();
        verify(connectionContext).getIoExecutor();
    }

    @After
    public void verifyMocks() {
        verify(ioExecutor).asExecutor();
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
        verify(mockExecutor).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection).closeAsync();
    }

    @Test
    public void connectionWithActiveRequestNeverIdles() {
        requestSubscriber.subscribe(idleAwareConnection.request(newRequest(PING)))
                .verifySubscribe()
                .verifyNoEmissions();

        completeTimer();
        completeTimer();
        verify(mockExecutor, times(3)).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection, times(1)).getConnectionContext();
        verify(connectionContext, times(1)).getIoExecutor();
        assertThat("Unexpected timer subscriptions.", timerSubscribed.get(), is(3));
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
        verify(mockExecutor, times(2)).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection, times(1)).getConnectionContext();
        verify(connectionContext, times(1)).getIoExecutor();
        assertThat("Unexpected timer subscriptions.", timerSubscribed.get(), is(2));
        verify(delegateConnection).request(any(RedisRequest.class));
        verify(delegateConnection).closeAsync();
    }

    @Test
    public void commanderRequestsAreInstrumented() {
        when(delegateConnection.getConnectionContext().getBufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(delegateConnection.request(any(RedisRequest.class), eq(String.class))).thenReturn(success("pong"));

        commandSubscriber.listen(idleAwareConnection.asCommander().ping())
                .verifySuccess("pong");

        completeTimer();
        verify(mockExecutor, times(2)).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection, times(1)).getExecutionContext();
        verify(delegateConnection, times(2)).getConnectionContext();
        verify(connectionContext, times(1)).getIoExecutor();
        assertThat("Unexpected timer subscriptions.", timerSubscribed.get(), is(2));
        verify(delegateConnection).request(any(RedisRequest.class), eq(String.class));
    }

    private void completeTimer() {
        timerRunnableRef.get().run();
    }
}
