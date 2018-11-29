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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SingleProcessor;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.redis.api.CommanderUtils.DiscardSingle;
import io.servicetalk.redis.api.CommanderUtils.ExecCompletable;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.redis.api.CommanderUtils.enqueueForExecute;
import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CommanderUtilsTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Mock
    private Cancellable cancellable;
    @Mock
    private ReservedRedisConnection reservedCnx;

    private final DeliberateException initialException = new DeliberateException();
    private final DeliberateException releaseException = new DeliberateException();
    private final List<SingleProcessor<?>> singles = new ArrayList<>();
    private final Single<String> queued = Single.error(initialException);
    private final Single<List<Object>> results = Single.error(initialException);
    private final TestCommander commander = new TestCommander();

    @Test
    public void testCancelCommandFuture() {
        final Single<String> queued = new Single<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(cancellable);
            }
        };

        Future<Object> future = enqueueForExecute(CommanderUtils.STATE_PENDING, singles, queued);

        future.cancel(true);

        verify(cancellable, never()).cancel();
    }

    @Test
    public void testDiscardError() throws Exception {
        Completable releaseCompletable = Completable.completed();
        when(reservedCnx.releaseAsync()).thenReturn(releaseCompletable);

        DiscardSingle<TestCommander> discardSingle = new DiscardSingle<>(commander, queued, singles,
                TestCommander.stateUpdater, reservedCnx, true);

        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(initialException));
        discardSingle.toFuture().get();
    }

    @Test
    public void testDiscardErrorReleaseError() throws Exception {
        Completable releaseCompletable = Completable.error(releaseException);
        when(reservedCnx.releaseAsync()).thenReturn(releaseCompletable);

        DiscardSingle<TestCommander> discardSingle = new DiscardSingle<>(commander, queued, singles,
                TestCommander.stateUpdater, reservedCnx, true);

        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(initialException));
        thrown.expectCause(hasProperty("suppressed", array(is(releaseException))));
        discardSingle.toFuture().get();
    }

    @Test
    public void testExecError() throws Exception {
        Completable releaseCompletable = Completable.completed();
        when(reservedCnx.releaseAsync()).thenReturn(releaseCompletable);

        ExecCompletable<TestCommander> execCompletable = new ExecCompletable<>(commander, results, singles,
                TestCommander.stateUpdater, reservedCnx, true);

        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(initialException));
        execCompletable.toFuture().get();
    }

    @Test
    public void testExecErrorReleaseError() throws Exception {
        Completable releaseCompletable = Completable.error(releaseException);
        when(reservedCnx.releaseAsync()).thenReturn(releaseCompletable);

        ExecCompletable<TestCommander> execCompletable = new ExecCompletable<>(commander, results, singles,
                TestCommander.stateUpdater, reservedCnx, true);

        thrown.expect(ExecutionException.class);
        thrown.expectCause(is(initialException));
        thrown.expectCause(hasProperty("suppressed", array(is(releaseException))));
        execCompletable.toFuture().get();
    }

    private static final class TestCommander {
        static final AtomicIntegerFieldUpdater<TestCommander> stateUpdater = AtomicIntegerFieldUpdater
                .newUpdater(TestCommander.class, "state");

        @SuppressWarnings("unused")
        volatile int state;
    }
}
