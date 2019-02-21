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
     * Converts the provided {@link Publisher} into a {@link PublisherSource}.
     *
     * @param publisher {@link Publisher} to convert to a {@link PublisherSource}.
     * @param <T> Type of items emitted from the {@code publisher} and the returned {@link PublisherSource}.
     * @return A {@link PublisherSource} representation of the passed {@link Publisher}.
     */
    public static <T> PublisherSource<T> toSource(Publisher<T> publisher) {
        if (publisher instanceof PublisherSource) {
            return uncheckedCast(publisher);
        }
        requireNonNull(publisher);
        return new PublisherToPublisherSource<>(publisher);
    }

    /**
     * Converts the provided {@link Single} into a {@link SingleSource}.
     *
     * @param single {@link Single} to convert to a {@link SingleSource}.
     * @param <T> Type of items emitted from the {@code single} and the returned {@link SingleSource}.
     * @return A {@link SingleSource} representation of the passed {@link Single}.
     */
    public static <T> SingleSource<T> toSource(Single<T> single) {
        if (single instanceof SingleSource) {
            return uncheckedCast(single);
        }
        requireNonNull(single);
        return new SingleToSingleSource<>(single);
    }

    /**
     * Converts the provided {@link Completable} into a {@link CompletableSource}.
     *
     * @param completable {@link Completable} to convert to a {@link CompletableSource}.
     * @return A {@link CompletableSource} representation of the passed {@link Completable}.
     */
    public static CompletableSource toSource(Completable completable) {
        if (completable instanceof CompletableSource) {
            return (CompletableSource) completable;
        }
        requireNonNull(completable);
        return new CompletableToCompletableSource(completable);
    }

    @SuppressWarnings("unchecked")
    private static <T> PublisherSource<T> uncheckedCast(final Publisher<T> publisher) {
        return (PublisherSource<T>) publisher;
    }

    @SuppressWarnings("unchecked")
    private static <T> SingleSource<T> uncheckedCast(final Single<T> single) {
        return (SingleSource<T>) single;
    }

    private static final class PublisherToPublisherSource<T> implements PublisherSource<T> {
        private final Publisher<T> publisher;

        PublisherToPublisherSource(final Publisher<T> publisher) {
            this.publisher = publisher;
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            publisher.subscribe(new Subscriber<T>() {
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
    }

    private static final class SingleToSingleSource<T> implements SingleSource<T> {
        private final Single<T> single;

        SingleToSingleSource(final Single<T> single) {
            this.single = single;
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            single.subscribe(new Subscriber<T>() {
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
    }

    private static final class CompletableToCompletableSource implements CompletableSource {
        private final Completable completable;

        CompletableToCompletableSource(final Completable completable) {
            this.completable = completable;
        }

        @Override
        public void subscribe(final Subscriber subscriber) {
            completable.subscribe(new Subscriber() {
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
    }
}
