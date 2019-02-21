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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A set of adapter methods to convert an asynchronous source in this module to a corresponding source in
 * {@link io.servicetalk.concurrent} module.
 */
public final class SourceAdapters {

    private SourceAdapters() {
        // No instances.
    }

    /**
     * Converts the passed {@link Publisher} to a {@link PublisherSource}.
     *
     * @param publisher {@link Publisher} to convert to a {@link PublisherSource}.
     * @param <T> Type of items emitted from the {@code publisher} and the returned {@link PublisherSource}.
     * @return A {@link PublisherSource} representation of the passed {@link Publisher}.
     */
    public static <T> PublisherSource<T> toSource(Publisher<T> publisher) {
        requireNonNull(publisher);
        if (publisher instanceof PublisherSource) {
            return uncheckCast(publisher);
        }
        return subscriber -> publisher.subscribe(new PublisherSource.Subscriber<T>() {
            @Override
            public void onSubscribe(final Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(@Nullable final T t) {
                subscriber.onNext(t);
            }

            @Override
            public void onError(final Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    /**
     * Converts the passed {@link Single} to a {@link SingleSource}.
     *
     * @param single {@link Single} to convert to a {@link SingleSource}.
     * @param <T> Type of items emitted from the {@code single} and the returned {@link SingleSource}.
     * @return A {@link SingleSource} representation of the passed {@link Single}.
     */
    public static <T> SingleSource<T> toSource(Single<T> single) {
        requireNonNull(single);
        if (single instanceof SingleSource) {
            return uncheckCast(single);
        }
        return subscriber -> single.subscribe(new SingleSource.Subscriber<T>() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                subscriber.onSubscribe(cancellable);
            }

            @Override
            public void onSuccess(@Nullable final T result) {
                subscriber.onSuccess(result);
            }

            @Override
            public void onError(final Throwable t) {
                subscriber.onError(t);
            }
        });
    }

    /**
     * Converts the passed {@link Completable} to a {@link CompletableSource}.
     *
     * @param completable {@link Completable} to convert to a {@link CompletableSource}.
     * @return A {@link CompletableSource} representation of the passed {@link Completable}.
     */
    public static CompletableSource toSource(Completable completable) {
        requireNonNull(completable);
        if (completable instanceof CompletableSource) {
            return uncheckCast(completable);
        }
        return subscriber -> completable.subscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                subscriber.onSubscribe(cancellable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }

            @Override
            public void onError(final Throwable t) {
                subscriber.onError(t);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> PublisherSource<T> uncheckCast(final Publisher<T> publisher) {
        return (PublisherSource<T>) publisher;
    }

    @SuppressWarnings("unchecked")
    private static <T> SingleSource<T> uncheckCast(final Single<T> single) {
        return (SingleSource<T>) single;
    }

    private static CompletableSource uncheckCast(final Completable completable) {
        return (CompletableSource) completable;
    }
}
