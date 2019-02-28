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
package io.servicetalk.redis.netty;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.TestPublisherSubscriber;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisProtocolSupport;
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
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.redis.api.RedisData.NULL;
import static io.servicetalk.redis.api.RedisProtocolSupport.Command.PING;
import static io.servicetalk.redis.api.RedisRequests.newRequest;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RedisIdleConnectionReaperTest {
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

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

    @Mock
    private ExecutionContext mockExecutionCtx;

    private final TestPublisherSubscriber<RedisData> requestSubscriber = new TestPublisherSubscriber<>();

    private CompletableProcessor delegateConnectionOnCloseCompletable;

    private final AtomicReference<Runnable> timerRunnableRef = new AtomicReference<>();

    private final AtomicBoolean timerCancelled = new AtomicBoolean();
    private final AtomicInteger timerSubscribed = new AtomicInteger();

    private RedisConnection idleAwareConnection;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        delegateConnectionOnCloseCompletable = new CompletableProcessor();
        when(delegateConnection.closeAsync()).thenReturn(delegateConnectionOnCloseCompletable);
        when(delegateConnection.onClose()).thenReturn(delegateConnectionOnCloseCompletable);
        when(delegateConnection.request(any(RedisExecutionStrategy.class), any(RedisRequest.class)))
                .thenReturn(just(NULL));
        when(delegateConnection.connectionContext()).thenReturn(connectionContext);
        when(delegateConnection.executionContext()).thenReturn(mockExecutionCtx);
        when(mockExecutionCtx.bufferAllocator()).thenReturn(DEFAULT_ALLOCATOR);
        when(connectionContext.executionContext()).thenReturn(mockExecutionCtx);
        when(mockExecutionCtx.ioExecutor()).thenReturn(ioExecutor);
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
        verify(delegateConnection).connectionContext();
        verify(mockExecutionCtx).ioExecutor();
        verify(connectionContext).executionContext();
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
        toSource(idleAwareConnection.request(newRequest(PING))).subscribe(requestSubscriber);
        assertTrue(requestSubscriber.subscriptionReceived());
        assertTrue(requestSubscriber.subscriptionReceived());
        assertThat(requestSubscriber.items(), hasSize(0));
        assertFalse(requestSubscriber.isTerminated());

        completeTimer();
        completeTimer();
        verify(mockExecutor, times(3)).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection, times(1)).connectionContext();
        verify(mockExecutionCtx, times(1)).ioExecutor();
        assertThat("Unexpected timer subscriptions.", timerSubscribed.get(), is(3));
        verify(delegateConnection).request(any(RedisExecutionStrategy.class), any(RedisRequest.class));
    }

    @Test
    public void connectionIdlesAfterFinishedRequest() {
        toSource(idleAwareConnection.request(newRequest(PING))).subscribe(requestSubscriber);
        assertTrue(requestSubscriber.subscriptionReceived());
        requestSubscriber.request(1);
        assertTrue(requestSubscriber.isCompleted());

        completeTimer();
        completeTimer();
        verify(mockExecutor, times(2)).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection, times(1)).connectionContext();
        verify(mockExecutionCtx, times(1)).ioExecutor();
        assertThat("Unexpected timer subscriptions.", timerSubscribed.get(), is(2));
        verify(delegateConnection).request(any(RedisExecutionStrategy.class), any(RedisRequest.class));
        verify(delegateConnection).closeAsync();
    }

    @Test
    public void commanderRequestsAreInstrumented() {
        when(delegateConnection.request(any(RedisExecutionStrategy.class), any(RedisRequest.class)))
                .thenAnswer(invocation -> {
                    RedisProtocolSupport.Command cmd = ((RedisRequest) invocation.getArgument(1)).command();
                    if ((cmd == PING)) {
                        return just(new RedisData.SimpleString("pong"));
                    }
                    throw new IllegalArgumentException("Unknown command: " + cmd);
                });

        commandSubscriber.listen(idleAwareConnection.asCommander().ping())
                .verifySuccess("pong");

        completeTimer();
        verify(mockExecutor, times(2)).schedule(any(Runnable.class), eq(1_000_000_000L), eq(NANOSECONDS));
        verify(delegateConnection, times(1)).executionContext();
        verify(delegateConnection, times(1)).connectionContext();
        verify(mockExecutionCtx, times(1)).ioExecutor();
        assertThat("Unexpected timer subscriptions.", timerSubscribed.get(), is(2));
        verify(delegateConnection).request(any(RedisExecutionStrategy.class), any(RedisRequest.class));
        verifyNoMoreInteractions(delegateConnection);
    }

    private void completeTimer() {
        timerRunnableRef.get().run();
    }
}
