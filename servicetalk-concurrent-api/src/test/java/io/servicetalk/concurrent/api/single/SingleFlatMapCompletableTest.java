/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.test.internal.TestCompletableSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public final class SingleFlatMapCompletableTest {
    private final TestCompletableSubscriber listener = new TestCompletableSubscriber();

    private LegacyTestSingle<String> single;
    private LegacyTestCompletable completable;

    @BeforeEach
    public void setUp() {
        single = new LegacyTestSingle<>();
        completable = new LegacyTestCompletable();
    }

    @Test
    public void testSuccess() {
        toSource(succeeded(1).flatMapCompletable(s -> completed())).subscribe(listener);
        listener.awaitOnComplete();
    }

    @Test
    public void testFirstEmitsError() {
        toSource(Single.failed(DELIBERATE_EXCEPTION).flatMapCompletable(s -> completable)).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSecondEmitsError() {
        toSource(succeeded(1).flatMapCompletable(s -> failed(DELIBERATE_EXCEPTION))).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelBeforeFirst() {
        toSource(single.flatMapCompletable(s -> completable)).subscribe(listener);
        listener.awaitSubscription().cancel();
        single.verifyCancelled();
    }

    @Test
    public void testCancelBeforeSecond() {
        toSource(single.flatMapCompletable(s -> completable)).subscribe(listener);
        single.onSuccess("Hello");
        listener.awaitSubscription().cancel();
        single.verifyNotCancelled();
        completable.verifyCancelled();
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        toSource(single.flatMapCompletable(s -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(listener);
        single.onSuccess("Hello");
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void nullInTerminalCallsOnError() {
        toSource(single.flatMapCompletable(s -> null)).subscribe(listener);
        single.onSuccess("Hello");
        assertThat(listener.awaitOnError(), instanceOf(NullPointerException.class));
    }
}
