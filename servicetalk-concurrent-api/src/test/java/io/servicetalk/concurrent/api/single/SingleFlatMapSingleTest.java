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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.TestSingle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public final class SingleFlatMapSingleTest {

    @Rule
    public final MockedSingleListenerRule<String> listener = new MockedSingleListenerRule<>();
    private TestSingle<String> first;
    private TestSingle<String> second;

    @Before
    public void setUp() {
        first = new TestSingle<>();
        second = new TestSingle<>();
    }

    @Test
    public void testFirstAndSecondPropagate() {
        listener.listen(success(1).flatMap(s -> success("Hello" + s)));
        listener.verifySuccess("Hello1");
    }

    @Test
    public void testSuccess() {
        listener.listen(success(1).flatMap(s -> success("Hello")));
        listener.verifySuccess("Hello");
    }

    @Test
    public void testFirstEmitsError() {
        listener.listen(error(DELIBERATE_EXCEPTION).flatMap(s -> success("Hello")));
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testSecondEmitsError() {
        listener.listen(success(1).flatMap(s -> error(DELIBERATE_EXCEPTION)));
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void testCancelBeforeFirst() {
        listener.listen(first.flatMap(s -> second));
        listener.cancel();
        first.verifyCancelled();
    }

    @Test
    public void testCancelBeforeSecond() {
        listener.listen(first.flatMap(s -> second));
        first.onSuccess("Hello");
        listener.cancel();
        first.verifyNotCancelled();
        second.verifyCancelled();
    }

    @Test
    public void exceptionInTerminalCallsOnError() {
        listener.listen(first.flatMap(s -> {
            throw DELIBERATE_EXCEPTION;
        }));
        first.onSuccess("Hello");
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void nullInTerminalCallsOnError() {
        listener.listen(first.flatMap(s -> null));
        first.onSuccess("Hello");
        listener.verifyFailure(NullPointerException.class);
    }
}
