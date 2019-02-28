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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource;

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
        return new CompletableToCompletableSource(completable);
    }

    /**
     * Converts the provided {@link PublisherSource} into a {@link Publisher}.
     *
     * @param source {@link PublisherSource} to convert to a {@link Publisher}.
     * @param <T> Type of items emitted from the {@code source} and the returned {@link Publisher}.
     * @return A {@link Publisher} representation of the passed {@link PublisherSource}.
     */
    public static <T> Publisher<T> fromSource(PublisherSource<T> source) {
        if (source instanceof Publisher) {
            return uncheckedCast(source);
        }
        return new PublisherSourceToPublisher<>(source);
    }

    /**
     * Converts the provided {@link SingleSource} into a {@link Single}.
     *
     * @param source {@link SingleSource} to convert to a {@link Single}.
     * @param <T> Type of items emitted from the {@code source} and the returned {@link Single}.
     * @return A {@link Single} representation of the passed {@link SingleSource}.
     */
    public static <T> Single<T> fromSource(SingleSource<T> source) {
        if (source instanceof Single) {
            return uncheckedCast(source);
        }
        return new SingleSourceToSingle<>(source);
    }

    /**
     * Converts the provided {@link CompletableSource} into a {@link Completable}.
     *
     * @param source {@link CompletableSource} to convert to a {@link Completable}.
     * @return A {@link Completable} representation of the passed {@link CompletableSource}.
     */
    public static Completable fromSource(CompletableSource source) {
        if (source instanceof Completable) {
            return (Completable) source;
        }
        return new CompletableSourceToCompletable(source);
    }

    @SuppressWarnings("unchecked")
    private static <T> Publisher<T> uncheckedCast(final PublisherSource<T> source) {
        return (Publisher<T>) source;
    }

    @SuppressWarnings("unchecked")
    private static <T> Single<T> uncheckedCast(final SingleSource<T> source) {
        return (Single<T>) source;
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
            this.publisher = requireNonNull(publisher);
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            publisher.subscribeInternal(subscriber);
        }
    }

    private static final class SingleToSingleSource<T> implements SingleSource<T> {
        private final Single<T> single;

        SingleToSingleSource(final Single<T> single) {
            this.single = requireNonNull(single);
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            single.subscribeInternal(subscriber);
        }
    }

    private static final class CompletableToCompletableSource implements CompletableSource {
        private final Completable completable;

        CompletableToCompletableSource(final Completable completable) {
            this.completable = requireNonNull(completable);
        }

        @Override
        public void subscribe(final Subscriber subscriber) {
            completable.subscribeInternal(subscriber);
        }
    }

    private static final class PublisherSourceToPublisher<T> extends Publisher<T> implements PublisherSource<T> {
        private final PublisherSource<T> source;

        PublisherSourceToPublisher(final PublisherSource<T> source) {
            this.source = requireNonNull(source);
        }

        @Override
        protected void handleSubscribe(final PublisherSource.Subscriber<? super T> subscriber) {
            source.subscribe(subscriber);
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            source.subscribe(subscriber);
        }
    }

    private static final class SingleSourceToSingle<T> extends Single<T> implements SingleSource<T> {
        private final SingleSource<T> source;

        SingleSourceToSingle(final SingleSource<T> source) {
            this.source = requireNonNull(source);
        }

        @Override
        protected void handleSubscribe(final SingleSource.Subscriber<? super T> subscriber) {
            source.subscribe(subscriber);
        }

        @Override
        public void subscribe(final Subscriber<? super T> subscriber) {
            source.subscribe(subscriber);
        }
    }

    private static final class CompletableSourceToCompletable extends Completable implements CompletableSource {
        private final CompletableSource source;

        CompletableSourceToCompletable(final CompletableSource source) {
            this.source = requireNonNull(source);
        }

        @Override
        protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
            source.subscribe(subscriber);
        }

        @Override
        public void subscribe(final Subscriber subscriber) {
            source.subscribe(subscriber);
        }
    }
}
