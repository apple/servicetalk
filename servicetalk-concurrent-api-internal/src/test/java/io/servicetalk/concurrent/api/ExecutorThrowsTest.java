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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.internal.OffloaderAwareExecutor;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SignalOffloaders.defaultOffloaderFactory;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;

@RunWith(Parameterized.class)
public class ExecutorThrowsTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final LinkedBlockingQueue<Throwable> errors;

    public ExecutorThrowsTest(@SuppressWarnings("unused") final boolean threadBased) {
        errors = new LinkedBlockingQueue<>();
    }

    @Parameterized.Parameters(name = " {index} thread based? {0}")
    public static Object[] params() {
        return new Object[]{true, false};
    }

    @Test
    public void publisherExecutorThrows() throws Throwable {
        Publisher<String> p = new Publisher<String>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super String> subscriber) {
                try {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                } catch (DeliberateException de) {
                    errors.add(de);
                    return;
                }
                subscriber.onError(new AssertionError("Offloading failed but onSubscribe passed."));
            }
        }.publishAndSubscribeOn(newAlwaysFailingExecutor());
        SourceAdapters.toSource(p).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
                // Noop
            }

            @Override
            public void onNext(final String s) {
                // Noop
            }

            @Override
            public void onError(final Throwable t) {
                errors.add(t);
            }

            @Override
            public void onComplete() {
                // Noop
            }
        });
        verifyError();
    }

    @Test
    public void singleExecutorThrows() throws Throwable {
        Single<String> s = new Single<String>() {
            @Override
            protected void handleSubscribe(final SingleSource.Subscriber<? super String> subscriber) {
                try {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                } catch (DeliberateException de) {
                    errors.add(de);
                    return;
                }
                subscriber.onError(new AssertionError("Offloading failed but onSubscribe passed."));
            }
        }.publishAndSubscribeOn(newAlwaysFailingExecutor());
        toSource(s).subscribe(new SingleSource.Subscriber<String>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                // Noop
            }

            @Override
            public void onSuccess(@Nullable final String result) {
                // Noop
            }

            @Override
            public void onError(final Throwable t) {
                errors.add(t);
            }
        });
        verifyError();
    }

    @Test
    public void completableExecutorThrows() throws Throwable {
        Completable c = new Completable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                try {
                    subscriber.onSubscribe(IGNORE_CANCEL);
                } catch (DeliberateException de) {
                    errors.add(de);
                    return;
                }
                subscriber.onError(new AssertionError("Offloading failed but onSubscribe passed."));
            }
        }.publishAndSubscribeOn(newAlwaysFailingExecutor());
        toSource(c).subscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                // Noop
            }

            @Override
            public void onComplete() {
                // Noop
            }

            @Override
            public void onError(final Throwable t) {
                errors.add(t);
            }
        });
        verifyError();
    }

    private Executor newAlwaysFailingExecutor() {
        Executor original = from(task -> {
            throw DELIBERATE_EXCEPTION;
        });
        return new OffloaderAwareExecutor(original, defaultOffloaderFactory());
    }

    private void verifyError() throws Throwable {
        Throwable err = errors.peek();
        if (err != DELIBERATE_EXCEPTION) {
            assertNoAsyncErrors(errors);
        }
    }
}
