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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class PublisherGroupToManyTest {
    private TestPublisher<Integer> source;
    private TestPublisherSubscriber<GroupedPublisher<GroupColor, Integer>> groupSub;
    private TestPublisherSubscriber<Integer> group1Sub;
    private TestPublisherSubscriber<Integer> group2Sub;
    private TestPublisherSubscriber<Integer> group3Sub;
    private TestSubscription subscription;

    private enum GroupColor {
        RED, GREEN, BLUE
    }

    @BeforeEach
    void setUp() {
        subscription = new TestSubscription();
        source = new TestPublisher.Builder<Integer>().disableAutoOnSubscribe().build(subscriber1 -> {
            subscriber1.onSubscribe(subscription);
            return subscriber1;
        });
        groupSub = new TestPublisherSubscriber<>();
        group1Sub = new TestPublisherSubscriber<>();
        group2Sub = new TestPublisherSubscriber<>();
        group3Sub = new TestPublisherSubscriber<>();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void groupToMany(boolean onError) throws InterruptedException {
        toSource(source.groupToMany((Integer integer) -> {
            if (integer == null) {
                return singletonList(GroupColor.RED).iterator();
            } else if ((integer & 0x1) == 0) {
                return asList(GroupColor.RED, GroupColor.GREEN, GroupColor.BLUE).iterator();
            }
            return asList(GroupColor.GREEN, GroupColor.BLUE).iterator();
        }, 10)).subscribe(groupSub);

        PublisherSource.Subscription groupSubscription = groupSub.awaitSubscription();
        groupSubscription.request(3);
        subscription.awaitRequestN(3);
        source.onNext(0);
        GroupedPublisher<GroupColor, Integer> group1 = groupSub.takeOnNext();
        assertThat(group1, notNullValue());
        assertThat(group1.key(), is(GroupColor.RED));
        toSource(group1).subscribe(group1Sub);
        GroupedPublisher<GroupColor, Integer> group2 = groupSub.takeOnNext();
        assertThat(group2, notNullValue());
        assertThat(group2.key(), is(GroupColor.GREEN));
        toSource(group2).subscribe(group2Sub);
        GroupedPublisher<GroupColor, Integer> group3 = groupSub.takeOnNext();
        assertThat(group3, notNullValue());
        assertThat(group3.key(), is(GroupColor.BLUE));
        toSource(group3).subscribe(group3Sub);

        PublisherSource.Subscription subscription1 = group1Sub.awaitSubscription();
        PublisherSource.Subscription subscription2 = group2Sub.awaitSubscription();
        PublisherSource.Subscription subscription3 = group3Sub.awaitSubscription();

        subscription1.request(1);
        assertThat(group1Sub.takeOnNext(), is(0));
        subscription2.request(1);
        assertThat(group2Sub.takeOnNext(), is(0));
        subscription3.request(1);
        assertThat(group3Sub.takeOnNext(), is(0));

        if (onError) {
            source.onError(DELIBERATE_EXCEPTION);
            assertThat(groupSub.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(group1Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(group2Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
            assertThat(group3Sub.awaitOnError(), is(DELIBERATE_EXCEPTION));
        } else {
            source.onComplete();
            groupSub.awaitOnComplete();
            group1Sub.awaitOnComplete();
            group2Sub.awaitOnComplete();
            group3Sub.awaitOnComplete();
        }
    }
}
