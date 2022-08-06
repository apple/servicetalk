/*
 * Copyright © 2018-2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.internal;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SpliceFlatStreamToSingleResultTest {
    private final TestSingleSubscriber<Result> resultSubscriber = new TestSingleSubscriber<>();
    private final TestPublisher<Object> upstream = new TestPublisher<>();
    private final First firstElement = new First("foo");
    private final Following one = new Following();
    private final Following two = new Following();
    private final LastFollowing last = new LastFollowing();

    private final TestPublisherSubscriber<Following> followingSubscriber = new TestPublisherSubscriber<>();
    private final TestPublisherSubscriber<Following> dupeFollowingSubscriber = new TestPublisherSubscriber<>();
    private final TestSubscription subscription = new TestSubscription();

    @Test
    void streamWithFirstAndFollowingShouldProduceResultWithEmbeddedFollowing() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        followingSubscriber.awaitSubscription().request(2);
        upstream.onNext(one, last);
        upstream.onComplete();
        assertThat(followingSubscriber.takeOnNext(2), contains(one, last));
        followingSubscriber.awaitOnComplete();
    }

    @Test
    void streamWithFirstAndEmptyFollowingShouldCompleteOnPublisherOnSubscribe() throws Exception {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        upstream.onComplete();
        assertThat(result.getFollowing().toFuture().get(), empty());
    }

    @Test
    void streamWithNullAndEmptyFollowingShouldCompleteOnPublisherOnSubscribe() throws Exception {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(null);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo("null"));
        upstream.onComplete();
        assertThat(result.getFollowing().toFuture().get(), empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyStreamShouldCompleteResultWithNull() {
        BiFunction<First, Publisher<Following>, Result> mock = mock(BiFunction.class);
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(mock));
        toSource(op).subscribe(resultSubscriber);
        upstream.onComplete();
        assertThat(resultSubscriber.awaitOnSuccess(), is(nullValue()));
        verify(mock, never()).apply(any(), any());
    }

    @Test
    void cancelResultRacingWithResultShouldCompleteAndFailPublisherOnSubscribe() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        resultSubscriber.awaitSubscription().cancel();
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        assertThat(followingSubscriber.awaitOnError(), instanceOf(CancellationException.class));
    }

    @Test
    void cancelResultAfterResultCompleteShouldIgnoreCancelAndDeliverPublisherOnComplete() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        resultSubscriber.awaitSubscription().cancel();
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        followingSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(followingSubscriber.takeOnNext(3), contains(one, two, last));
        followingSubscriber.awaitOnComplete();
    }

    @Test
    void cancelResultBeforeResultCompleteShouldDeliverError() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onSubscribe(subscription);
        resultSubscriber.awaitSubscription().cancel();
        assertTrue(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        assertThat(resultSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void streamErrorAfterPublisherSubscribeShouldDeliverError() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        followingSubscriber.awaitSubscription().request(1);
        upstream.onNext(one);
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        assertThat(followingSubscriber.takeOnNext(), is(one));
        assertThat(followingSubscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void streamCompleteAfterPublisherSubscribeShouldDeliverComplete() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        followingSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(followingSubscriber.takeOnNext(3), contains(one, two, last));
        followingSubscriber.awaitOnComplete();
    }

    @Test
    void streamCompleteBeforePublisherSubscribeShouldDeliverCompleteOnSubscribe() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        upstream.onComplete();
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        followingSubscriber.awaitOnComplete();
    }

    @Test
    void streamErrorBeforePublisherSubscribeShouldDeliverErrorOnSubscribe() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onSubscribe(subscription);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        assertFalse(subscription.isCancelled());
        upstream.onError(DELIBERATE_EXCEPTION);
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        assertThat(followingSubscriber.awaitOnError(), sameInstance(DELIBERATE_EXCEPTION));
    }

    @Test
    void publisherSubscribeTwiceShouldFailSecondSubscriber() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        followingSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        toSource(result.getFollowing()).subscribe(dupeFollowingSubscriber);
        assertThat(dupeFollowingSubscriber.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
        upstream.onComplete();
        assertThat(followingSubscriber.takeOnNext(3), contains(one, two, last));
        followingSubscriber.awaitOnComplete();
    }

    @Test
    void publisherSubscribeAgainAfterCompletingInitialSubscriberShouldFailSecondSubscriber() {
        Single<Result> op = upstream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>(Result::new));
        toSource(op).subscribe(resultSubscriber);
        upstream.onNext(firstElement);
        Result result = resultSubscriber.awaitOnSuccess();
        assertThat(result, is(notNullValue()));
        assertThat(result.meta(), equalTo(result.meta()));
        toSource(result.getFollowing()).subscribe(followingSubscriber);
        followingSubscriber.awaitSubscription().request(3);
        upstream.onNext(one, two, last);
        upstream.onComplete();
        assertThat(followingSubscriber.takeOnNext(3), contains(one, two, last));
        followingSubscriber.awaitOnComplete();
        toSource(result.getFollowing()).subscribe(dupeFollowingSubscriber);
        assertThat(dupeFollowingSubscriber.awaitOnError(), instanceOf(DuplicateSubscribeException.class));
    }

    @Test
    void packerThrowsShouldSendErrorToSingle() {
        // We use Publisher.just() here to make sure the Publisher invokes onError when onNext throws.
        // TestPublisher used in other cases, does not show that behavior. Instead it throws from sendItems() which is
        // less obvious failure message than what we get with resultSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
        Publisher<Object> stream = from(firstElement);
        Single<Result> op = stream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>((first, following) -> {
                    throw DELIBERATE_EXCEPTION;
                }));
        toSource(op).subscribe(resultSubscriber);
        assertThat(resultSubscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    void packerReturnsNullShouldSendErrorToSingle() {
        // We use Publisher.just() here to make sure the Publisher invokes onError when onNext throws.
        // TestPublisher used in other cases, does not show that behavior. Instead it throws from sendItems() which is
        // less obvious failure message than what we get with resultSubscriber.verifyFailure(DELIBERATE_EXCEPTION);
        Publisher<Object> stream = from(firstElement);
        Single<Result> op = stream.liftSyncToSingle(new SpliceFlatStreamToSingleResult<>((first, following) -> null));
        toSource(op).subscribe(resultSubscriber);
        assertThat(resultSubscriber.awaitOnError(), is(instanceOf(NullPointerException.class)));
    }

    private static class First {
        private final String meta;

        First(String meta) {
            this.meta = meta;
        }

        String meta() {
            return meta;
        }
    }

    private static final class Result extends First {
        private final Publisher<Following> following;

        Result(@Nullable First first, Publisher<Following> following) {
            super(first == null ? "null" : first.meta());
            this.following = following;
        }

        Publisher<Following> getFollowing() {
            return following;
        }
    }

    private static class Following {
    }

    private static final class LastFollowing extends Following {
    }
}
