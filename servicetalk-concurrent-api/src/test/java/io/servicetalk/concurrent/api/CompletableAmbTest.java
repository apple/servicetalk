/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Completable.amb;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class CompletableAmbTest {

    private final TestCompletable first = new TestCompletable();
    private final TestCompletable second = new TestCompletable();
    private final TestCompletableSubscriber subscriber = new TestCompletableSubscriber();
    private final TestCancellable cancellable = new TestCancellable();

    public CompletableAmbTest(final BiFunction<Completable, Completable, Completable> ambSupplier) {
        toSource(ambSupplier.apply(first, second)).subscribe(subscriber);
        subscriber.awaitSubscription();
        assertThat("First source not subscribed.", first.isSubscribed(), is(true));
        assertThat("Second source not subscribed.", second.isSubscribed(), is(true));
    }

    @Parameterized.Parameters
    public static Collection<BiFunction<Completable, Completable, Completable>> data() {
        return asList(Completable::ambWith,
                (first, second) -> amb(first, second),
                (first, second) -> amb(asList(first, second)));
    }

    @Test
    public void successFirst() {
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
    }

    @Test
    public void successSecond() {
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
    }

    @Test
    public void failFirst() {
        sendErrorToAndVerify(first);
        verifyCancelled(second);
    }

    @Test
    public void failSecond() {
        sendErrorToAndVerify(second);
        verifyCancelled(first);
    }

    @Test
    public void successFirstThenSecond() {
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        second.onComplete();
    }

    @Test
    public void successSecondThenFirst() {
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        first.onComplete();
    }

    @Test
    public void failFirstThenSecond() {
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void failSecondThenFirst() {
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void successFirstThenSecondFail() {
        sendSuccessToAndVerify(first);
        verifyCancelled(second);
        second.onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void successSecondThenFirstFail() {
        sendSuccessToAndVerify(second);
        verifyCancelled(first);
        first.onError(DELIBERATE_EXCEPTION);
    }

    @Test
    public void failFirstThenSecondSuccess() {
        sendErrorToAndVerify(first);
        verifyCancelled(second);
        second.onComplete();
    }

    @Test
    public void failSecondThenFirstSuccess() {
        sendErrorToAndVerify(second);
        verifyCancelled(first);
        first.onComplete();
    }

    private void sendSuccessToAndVerify(final TestCompletable source) {
        source.onComplete();
        subscriber.awaitOnComplete();
    }

    private void sendErrorToAndVerify(final TestCompletable source) {
        source.onError(DELIBERATE_EXCEPTION);
        assertThat(subscriber.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    private void verifyCancelled(final TestCompletable other) {
        other.onSubscribe(cancellable);
        assertThat("Other source not cancelled.", cancellable.isCancelled(), is(true));
    }
}
