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
package io.servicetalk.concurrent.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.servicetalk.concurrent.api.Notification.onCancel;
import static io.servicetalk.concurrent.api.Notification.onError;
import static io.servicetalk.concurrent.api.Notification.onNext;
import static io.servicetalk.concurrent.api.Notification.onRequest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Rule that tracks various subscriber events.
 */
public final class NotificationTracker<T> {

    private List<Notification<T>> nexts;
    private List<Notification<Void>> cancels;
    private List<Notification<Long>> requests;
    private List<Notification<Throwable>> errors;
    private List<Notification<Void>> completes;
    private Publisher<T> source;

    public NotificationTracker(Publisher<T> source) {
        this.source = source;
        nexts = new ArrayList<>();
        cancels = new ArrayList<>();
        requests = new ArrayList<>();
        errors = new ArrayList<>();
        completes = new ArrayList<>();
    }

    public Publisher<T> getSource() {
        return source;
    }

    public Publisher<T> trackComplete() {
        source = source.doBeforeComplete(() -> completes.add(Notification.onComplete()));
        return source;
    }

    public Publisher<T> trackError() {
        source = source.doBeforeError(cause -> errors.add(onError(cause)));
        return source;
    }

    public Publisher<T> trackNext() {
        source = source.doBeforeNext(t -> nexts.add(onNext(t)));
        return source;
    }

    public Publisher<T> trackCancel() {
        source = source.doBeforeCancel(() -> cancels.add(onCancel()));
        return source;
    }

    public Publisher<T> trackRequest() {
        source = source.doBeforeRequest(n -> requests.add(onRequest(n)));
        return source;
    }

    public NotificationTracker<T> resetNotifications() {
        nexts.clear();
        cancels.clear();
        requests.clear();
        errors.clear();
        completes.clear();
        return this;
    }

    public NotificationTracker<T> verifyCancel() {
        assertThat("No cancel received.", cancels, hasSize(1));
        return this;
    }

    public NotificationTracker<T> verifyCancelLax() {
        assertThat("No cancel received.", cancels, hasSize(greaterThanOrEqualTo(1)));
        return this;
    }

    public NotificationTracker<T> verifyRequest(Long... requests) {
        assertThat("Unexpected requests received.", this.requests, hasSize(requests.length));
        List<Long> received = this.requests.stream().map(Notification::getData).map(Optional::get).collect(Collectors.toList());
        assertThat("Unexpected requests received.", received, contains(requests));
        return this;
    }

    @SafeVarargs
    public final NotificationTracker<T> verifyOnNexts(T... nexts) {
        assertThat("Unexpected onNexts received.", this.nexts, hasSize(nexts.length));
        if (nexts.length > 0) {
            List<T> received = this.nexts.stream().map(Notification::getData).map(Optional::get).collect(Collectors.toList());
            assertThat("Unexpected onNexts received.", received, contains(nexts));
        }
        return this;
    }

    public NotificationTracker<T> verifyError(Throwable cause) {
        assertThat("No errors received.", errors, hasSize(1));
        verifyErrorLax(cause);
        return this;
    }

    public NotificationTracker<T> verifyErrorLax(Throwable cause) {
        List<Throwable> received = errors.stream().map(Notification::getData).map(Optional::get).collect(Collectors.toList());
        assertThat("Unexpected error received.", received, contains(cause));
        return this;
    }

    public NotificationTracker<T> verifyComplete() {
        assertThat("No complete notification received.", completes, hasSize(1));
        return this;
    }

    public NotificationTracker<T> verifyCompleteLax() {
        assertThat("No complete notification received.", completes, hasSize(greaterThanOrEqualTo(1)));
        return this;
    }
}
