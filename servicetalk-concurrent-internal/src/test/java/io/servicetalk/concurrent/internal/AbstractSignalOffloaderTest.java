/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public abstract class AbstractSignalOffloaderTest<T extends SignalOffloader> {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    MockExecutor executor;
    T offloader;
    SingleSource.Subscriber<Integer> singleSub;
    CompletableSource.Subscriber completableSub;
    PublisherSource.Subscriber<? super Integer> pubSub;

    protected void doSetup() {
        executor = new MockExecutor();
        offloader = newOffloader(executor);
        singleSub = uncheckedMock(SingleSource.Subscriber.class);
        completableSub = uncheckedMock(CompletableSource.Subscriber.class);
        pubSub = uncheckedMock(PublisherSource.Subscriber.class);
    }

    protected abstract T newOffloader(MockExecutor executor);

    @Test
    public void offloadSingleNoOnSubscribeOnError() {
        offloadSingleNoOnSubscribe(false);
    }

    @Test
    public void offloadSingleNoOnSubscribeOnComplete() {
        offloadSingleNoOnSubscribe(true);
    }

    private void offloadSingleNoOnSubscribe(boolean onComplete) {
        SingleSource.Subscriber<? super Integer> offloadedSingleSub = offloader.offloadSubscriber(singleSub);
        if (onComplete) {
            offloadedSingleSub.onSuccess(1);
        } else {
            offloadedSingleSub.onError(DELIBERATE_EXCEPTION);
        }
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        if (onComplete) {
            verify(singleSub).onSuccess(eq(1));
        } else {
            verify(singleSub).onError(DELIBERATE_EXCEPTION);
        }
    }

    @Test
    public void offloadCompletableNoOnSubscribeOnError() {
        offloadCompletableNoOnSubscribe(false);
    }

    @Test
    public void offloadCompletableNoOnSubscribeOnComplete() {
        offloadCompletableNoOnSubscribe(true);
    }

    private void offloadCompletableNoOnSubscribe(boolean onComplete) {
        CompletableSource.Subscriber offloadedCompletableSub = offloader.offloadSubscriber(completableSub);
        if (onComplete) {
            offloadedCompletableSub.onComplete();
        } else {
            offloadedCompletableSub.onError(DELIBERATE_EXCEPTION);
        }
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        if (onComplete) {
            verify(completableSub).onComplete();
        } else {
            verify(completableSub).onError(DELIBERATE_EXCEPTION);
        }
    }

    @Test
    public void offloadPublisherNoOnSubscribeOnError() {
        offloadPublisherNoOnSubscribe(false);
    }

    @Test
    public void offloadPublisherNoOnSubscribeOnComplete() {
        offloadPublisherNoOnSubscribe(true);
    }

    private void offloadPublisherNoOnSubscribe(boolean onComplete) {
        PublisherSource.Subscriber<? super Integer> offloadedPubSub = offloader.offloadSubscriber(pubSub);
        if (onComplete) {
            offloadedPubSub.onComplete();
        } else {
            offloadedPubSub.onError(DELIBERATE_EXCEPTION);
        }
        assertThat("Unexpected tasks executed.", executor.executeAllTasks(), is(1));
        if (onComplete) {
            verify(pubSub).onComplete();
        } else {
            verify(pubSub).onError(DELIBERATE_EXCEPTION);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T uncheckedMock(Class<?> anyClass) {
        return (T) mock(anyClass);
    }

    static final class MockExecutor implements Executor {
        final ConcurrentLinkedQueue<Runnable> tasks;
        final Executor mock;

        MockExecutor() {
            tasks = new ConcurrentLinkedQueue<>();
            mock = mock(Executor.class);
            doAnswer(invocation -> {
                tasks.offer(invocation.getArgument(0));
                return null;
            }).when(mock).execute(any());
        }

        @Override
        public Cancellable execute(final Runnable runnable) {
            mock.execute(runnable);
            return IGNORE_CANCEL;
        }

        @Override
        public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
                throws RejectedExecutionException {
            throw new UnsupportedOperationException("Schedule not supported for mock.");
        }

        int executeAllTasks() {
            int execCount = 0;
            Runnable task;
            while ((task = tasks.poll()) != null) {
                execCount++;
                task.run();
            }
            return execCount;
        }
    }
}
