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

import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.channel.EventLoop;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DnsResolverTest {

    private static final int BACKUP_DELAY = CI ? 200 : 20;
    private static final int BACKUP_DELAY_WAIT_TIME = BACKUP_DELAY * 2;

    @RegisterExtension
    static final ExecutorExtension<EventLoopAwareNettyIoExecutor> ioExecutor = ExecutorExtension
            .withExecutor(() -> createIoExecutor(1))
            .setClassLevel(true);

    private final DnsNameResolver primaryResolver = mock(DnsNameResolver.class);
    private final DnsNameResolver backupResolver = mock(DnsNameResolver.class);

    private final EventLoop eventLoop = ioExecutor.executor().eventLoopGroup().next();
    private final Promise<List<InetAddress>> primaryPromise = eventLoop.newPromise();
    private final Promise<List<InetAddress>> backupPromise = eventLoop.newPromise();
    private final DnsResolver resolver = new DnsResolver.BackupRequestResolver(
            "TheResolver", primaryResolver, backupResolver, eventLoop, () -> BACKUP_DELAY);

    @BeforeEach
    void setup() {
        when(primaryResolver.resolveAll("foo")).thenReturn(primaryPromise);
        when(backupResolver.resolveAll("foo")).thenReturn(backupPromise);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: firstResolveWins={0}")
    @ValueSource(booleans = {true, false})
    void backupRequest(boolean firstResolveWins) throws Exception {
        Future<List<InetAddress>> resolveFuture = resolver.resolveAll("ignored", "foo");
        assertThat(resolveFuture.isDone(), equalTo(false));

        verify(primaryResolver, times(1)).resolveAll("foo");
        verify(backupResolver, times(0)).resolveAll("foo");

        // After the delay plus some grace period, we should have a backup request
        verify(primaryResolver, timeout(BACKUP_DELAY_WAIT_TIME).times(1)).resolveAll("foo");
        verify(backupResolver, timeout(BACKUP_DELAY_WAIT_TIME).times(1)).resolveAll("foo");
        List<InetAddress> result = new ArrayList<>();
        (firstResolveWins ? primaryPromise : backupPromise).setSuccess(result);
        assertThat(result, sameInstance(resolveFuture.get(BACKUP_DELAY_WAIT_TIME, SECONDS)));
    }

    @Test
    void originalRequestFailureAfterBackupFires() {
        Future<List<InetAddress>> resolveFuture = resolver.resolveAll("ignored", "foo");
        assertThat(resolveFuture.isDone(), equalTo(false));

        verify(primaryResolver, times(1)).resolveAll("foo");
        verify(backupResolver, times(0)).resolveAll("foo");

        // After the delay plus some grace period, we should have a backup request
        verify(primaryResolver, timeout(BACKUP_DELAY_WAIT_TIME).times(1)).resolveAll("foo");
        verify(backupResolver, timeout(BACKUP_DELAY_WAIT_TIME).times(1)).resolveAll("foo");
        primaryPromise.setFailure(DELIBERATE_EXCEPTION);
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> resolveFuture.get(BACKUP_DELAY_WAIT_TIME, SECONDS));
        assertThat(ex.getCause(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: originalIsFailure={0}")
    @ValueSource(booleans = {true, false})
    void noBackupRequestIfOriginalCompletesBeforeTimer(boolean originalIsFailure) throws Exception {
        Future<List<InetAddress>> resolveFuture = resolver.resolveAll("ignored", "foo");
        verify(primaryResolver, times(1)).resolveAll("foo");
        assertThat(resolveFuture.isDone(), equalTo(false));
        if (originalIsFailure) {
            primaryPromise.tryFailure(DELIBERATE_EXCEPTION);
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> resolveFuture.get(BACKUP_DELAY, SECONDS));
            assertThat(ex.getCause(), sameInstance(DELIBERATE_EXCEPTION));
        } else {
            List<InetAddress> result = new ArrayList<>();
            primaryPromise.trySuccess(result);
            assertThat(result, sameInstance(resolveFuture.get(BACKUP_DELAY, SECONDS)));
        }

        // Wait for the timeout duration to be sure we only get one call.
        eventLoop.schedule(() -> { }, BACKUP_DELAY_WAIT_TIME, MILLISECONDS).get();
        verify(backupResolver, times(0)).resolveAll("foo");
    }
}
