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
package io.servicetalk.dns.discovery.netty;

import io.netty.channel.EventLoop;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DnsResolverTest {

    private static final int BACKUP_DELAY = 20;

    private final DnsNameResolver primaryResolver = mock(DnsNameResolver.class);
    private final DnsNameResolver backupResolver = mock(DnsNameResolver.class);
    private final EventLoop eventLoop = mock(EventLoop.class);

    private final Promise<List<InetAddress>> primaryPromise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
    private final Promise<List<InetAddress>> backupPromise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
    private final ScheduledFuture<Void> scheduledTaskFuture = mock(ScheduledFuture.class);

    private final DnsResolver resolver = new DnsResolver.BackupRequestResolver(
            "TheResolver", primaryResolver, backupResolver, eventLoop, () -> BACKUP_DELAY);

    private final ArgumentCaptor<Runnable> scheduledTaskCaptor = ArgumentCaptor.forClass(Runnable.class);

    @BeforeEach
    void setup() {
        when(primaryResolver.resolveAll("foo")).thenReturn(primaryPromise);
        when(backupResolver.resolveAll("foo")).thenReturn(backupPromise);
        when(eventLoop.newPromise()).thenAnswer(invocation -> new DefaultPromise<>(ImmediateEventExecutor.INSTANCE));
        when(eventLoop.schedule(scheduledTaskCaptor.capture(), eq((long) BACKUP_DELAY), eq(TimeUnit.MILLISECONDS)))
            .thenReturn((ScheduledFuture) scheduledTaskFuture);
        when(scheduledTaskFuture.cancel(true)).thenReturn(true);
    }

    private void fireTimerTask() {
        List<Runnable> capturedTasks = scheduledTaskCaptor.getAllValues();
        assertThat(capturedTasks, hasSize(1));
        capturedTasks.get(0).run();
    }

    @ParameterizedTest(name = "{displayName} [{index}]: firstResolveWins={0}")
    @ValueSource(booleans = {true, false})
    void backupRequest(boolean firstResolveWins) throws Exception {
        Future<List<InetAddress>> resolveFuture = resolver.resolveAll("ignored", "foo");
        assertThat(resolveFuture.isDone(), equalTo(false));

        // Verify initial state: primary resolver called, backup not called yet
        verify(primaryResolver, times(1)).resolveAll("foo");
        verify(backupResolver, times(0)).resolveAll("foo");

        fireTimerTask();

        // Verify backup resolver was called
        verify(primaryResolver, times(1)).resolveAll("foo"); // Should still be 1
        verify(backupResolver, times(1)).resolveAll("foo");

        // Complete the winning resolver with real promise behavior
        List<InetAddress> result = new ArrayList<>();
        if (firstResolveWins) {
            primaryPromise.setSuccess(result);
        } else {
            backupPromise.setSuccess(result);
        }

        assertThat(result, sameInstance(resolveFuture.get(1, SECONDS)));
    }

    @Test
    void originalRequestFailureAfterBackupFires() throws Exception {
        Future<List<InetAddress>> resolveFuture = resolver.resolveAll("ignored", "foo");
        assertThat(resolveFuture.isDone(), equalTo(false));

        verify(primaryResolver, times(1)).resolveAll("foo");
        verify(backupResolver, times(0)).resolveAll("foo");

        fireTimerTask();

        verify(backupResolver, times(1)).resolveAll("foo");
        primaryPromise.setFailure(DELIBERATE_EXCEPTION);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> resolveFuture.get(1, SECONDS));
        assertThat(ex.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: originalIsFailure={0}")
    @ValueSource(booleans = {true, false})
    void noBackupRequestIfOriginalIsAlreadyCompleted(boolean originalIsFailure) throws Exception {
        List<InetAddress> result = new ArrayList<>();
        if (originalIsFailure) {
            primaryPromise.setFailure(DELIBERATE_EXCEPTION);
        } else {
            primaryPromise.setSuccess(result);
        }

        Future<List<InetAddress>> resolveFuture = resolver.resolveAll("ignored", "foo");
        verify(primaryResolver, times(1)).resolveAll("foo");

        if (originalIsFailure) {
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> resolveFuture.get(1, SECONDS));
            assertThat(ex.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            assertThat(result, sameInstance(resolveFuture.get(1, SECONDS)));
        }

        // Since the primary promise completed immediately, no backup request should be scheduled
        verify(eventLoop, times(0)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        verify(backupResolver, times(0)).resolveAll("foo");
    }

    @ParameterizedTest(name = "{displayName} [{index}]: originalIsFailure={0}")
    @ValueSource(booleans = {true, false})
    void noBackupRequestIfOriginalCompletesBeforeTimer(boolean originalIsFailure) throws Exception {
        // Primary promise starts incomplete, so timer will be scheduled
        Future<List<InetAddress>> resolveFuture = resolver.resolveAll("ignored", "foo");
        verify(primaryResolver, times(1)).resolveAll("foo");
        assertThat(resolveFuture.isDone(), equalTo(false));

        // Verify that the timer was scheduled
        verify(eventLoop, times(1)).schedule(any(Runnable.class), eq((long) BACKUP_DELAY), eq(TimeUnit.MILLISECONDS));
        verify(backupResolver, times(0)).resolveAll("foo"); // No backup request yet

        // Now complete the primary promise before the timer fires
        List<InetAddress> result = new ArrayList<>();
        if (originalIsFailure) {
            primaryPromise.setFailure(DELIBERATE_EXCEPTION);
        } else {
            primaryPromise.setSuccess(result);
        }

        // Verify the timer was cancelled due to primary completion
        verify(scheduledTaskFuture, times(1)).cancel(true);

        // Verify result
        if (originalIsFailure) {
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> resolveFuture.get(1, SECONDS));
            assertThat(ex.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            assertThat(result, sameInstance(resolveFuture.get(1, SECONDS)));
        }

        // Backup resolver should never be called since timer was cancelled
        verify(backupResolver, times(0)).resolveAll("foo");
    }
}
