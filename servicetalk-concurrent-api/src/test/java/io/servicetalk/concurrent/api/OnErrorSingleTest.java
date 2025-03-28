/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.context.api.ContextMap;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class OnErrorSingleTest {
    private TestSingleSubscriber<Integer> subscriber;
    private TestSingle<Integer> first;

    @BeforeEach
    void setUp() {
        subscriber = new TestSingleSubscriber<>();
        first = new TestSingle<>();
    }

    @Test
    void onErrorReturnMatch() {
        toSource(first.onErrorReturn(t -> 1)).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnSuccess(), is(1));
    }

    @Test
    void onErrorReturnThrows() {
        toSource(first.onErrorReturn(t -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorReturnClassMatch() {
        toSource(first.onErrorReturn(DeliberateException.class, t -> 1)).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnSuccess(), is(1));
    }

    @Test
    void onErrorReturnClassNoMatch() {
        toSource(first.onErrorReturn(IllegalArgumentException.class, t -> 1)).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorReturnPredicateMatch() {
        toSource(first.onErrorReturn(t -> t == DELIBERATE_EXCEPTION, t -> 1)).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnSuccess(), is(1));
    }

    @Test
    void onErrorReturnPredicateNoMatch() {
        toSource(first.onErrorReturn(t -> false, t -> 1)).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorReturnContextPropagation() {
        Set<ContextMap> contextMapSet = Collections.newSetFromMap(new IdentityHashMap<>());
        toSource(first
                .whenOnError(t -> contextMapSet.add(AsyncContext.context()))
                .onErrorReturn(t -> {   // predicate
                    contextMapSet.add(AsyncContext.context());
                    return true;
                }, t -> {   // item supplier
                    contextMapSet.add(AsyncContext.context());
                    return 1;
                })
                .whenOnSuccess(i -> contextMapSet.add(AsyncContext.context())))
                .subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnSuccess(), is(1));
        assertThat("Unexpected number of different contexts", contextMapSet, Matchers.hasSize(1));
    }

    @Test
    void onErrorMapMatch() {
        toSource(first.onErrorMap(t -> DELIBERATE_EXCEPTION)).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorMapMatchThrows() {
        toSource(first.onErrorMap(t -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorMapClassMatch() {
        toSource(first.onErrorMap(DeliberateException.class, t -> DELIBERATE_EXCEPTION)).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorMapClassNoMatch() {
        toSource(first.onErrorMap(IllegalArgumentException.class, t -> new DeliberateException()))
                .subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorMapPredicateMatch() {
        toSource(first.onErrorMap(t -> t instanceof DeliberateException, t -> DELIBERATE_EXCEPTION))
                .subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorMapPredicateNoMatch() {
        toSource(first.onErrorMap(t -> false, t -> new IllegalStateException())).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorMapContextPropagation() {
        Set<ContextMap> contextMapSet = Collections.newSetFromMap(new IdentityHashMap<>());
        toSource(first
                .whenOnError(t -> contextMapSet.add(AsyncContext.context()))
                .onErrorMap(t -> {   // predicate
                    contextMapSet.add(AsyncContext.context());
                    return true;
                }, t -> {   // item supplier
                    contextMapSet.add(AsyncContext.context());
                    return DELIBERATE_EXCEPTION;
                })
                .whenOnError(i -> contextMapSet.add(AsyncContext.context())))
                .subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
        assertThat("Unexpected number of different contexts", contextMapSet, Matchers.hasSize(1));
    }

    @Test
    void onErrorResumeMatch() {
        toSource(first.onErrorResume(t -> Single.succeeded(1))).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnSuccess(), is(1));
    }

    @Test
    void onErrorResumeThrows() {
        toSource(first.onErrorResume(t -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorResumeClassMatch() {
        toSource(first.onErrorResume(DeliberateException.class, t -> failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorResumeClassNoMatch() {
        toSource(first.onErrorResume(IllegalArgumentException.class, t -> succeeded(1))).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorResumePredicateMatch() {
        toSource(first.onErrorResume(t -> t instanceof DeliberateException, t -> failed(DELIBERATE_EXCEPTION)))
                .subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(new DeliberateException());
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorResumePredicateNoMatch() {
        toSource(first.onErrorResume(t -> false, t -> succeeded(1))).subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void onErrorResumeContextPropagation() {
        Set<ContextMap> contextMapSet = Collections.newSetFromMap(new IdentityHashMap<>());
        toSource(first
                .whenOnError(t -> contextMapSet.add(AsyncContext.context()))
                .onErrorResume(t -> {   // predicate
                    contextMapSet.add(AsyncContext.context());
                    return true;
                }, t -> {   // item supplier
                    contextMapSet.add(AsyncContext.context());
                    return Single.succeeded(1);
                })
                .whenOnSuccess(i -> contextMapSet.add(AsyncContext.context())))
                .subscribe(subscriber);
        subscriber.awaitSubscription();
        first.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnSuccess(), is(1));
        assertThat("Unexpected number of different contexts", contextMapSet, Matchers.hasSize(1));
    }
}
