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

import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public final class SingleFlatMapSingleTest {

    private final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();

    private LegacyTestSingle<String> first;
    private LegacyTestSingle<String> second;

    @BeforeEach
    public void setUp() {
        first = new LegacyTestSingle<>();
        second = new LegacyTestSingle<>();
    }

    @Test
    public void testFirstAndSecondPropagate() {
        toSource(succeeded(1).flatMap(s -> succeeded("Hello" + s))).subscribe(listener);
        assertThat(listener.awaitOnSuccess(), is("Hello1"));
    }

    @Test
    public void testSuccess() {
        toSource(succeeded(1).flatMap(s -> succeeded("Hello"))).subscribe(listener);
        assertThat(listener.awaitOnSuccess(), is("Hello"));
    }

    @Test
    public void testFirstEmitsError() {
        toSource(failed(DELIBERATE_EXCEPTION).flatMap(s -> succeeded("Hello"))).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testSecondEmitsError() {
        SourceAdapters.<String>toSource(succeeded(1).flatMap(s -> failed(DELIBERATE_EXCEPTION))).subscribe(listener);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void testCancelBeforeFirst() {
        toSource(first.flatMap(s -> second)).subscribe(listener);
        listener.awaitSubscription().cancel();
        first.verifyCancelled();
    }

    @Test
    public void testCancelBeforeSecond() {
        toSource(first.flatMap(s -> second)).subscribe(listener);
        first.onSuccess("Hello");
        listener.awaitSubscription().cancel();
        first.verifyNotCancelled();
        second.verifyCancelled();
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        SourceAdapters.<String>toSource(first.flatMap(s -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(listener);
        first.onSuccess("Hello");
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void nullInTerminalCallsOnError() {
        SourceAdapters.<String>toSource(first.flatMap(s -> null)).subscribe(listener);
        first.onSuccess("Hello");
        assertThat(listener.awaitOnError(), instanceOf(NullPointerException.class));
    }
}
