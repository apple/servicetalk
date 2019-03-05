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
package io.servicetalk.concurrent.api.single;

import io.servicetalk.concurrent.api.ExecutorRule;
import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;

public class ConcatWithCompletableTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @Rule
    public final ExecutorRule executorRule = ExecutorRule.newRule();
    @Rule
    public final LegacyMockedSingleListenerRule<String> listener = new LegacyMockedSingleListenerRule<>();
    private LegacyTestSingle<String> single = new LegacyTestSingle<>();
    private LegacyTestCompletable completable = new LegacyTestCompletable();

    @Test
    public void concatWaitsForCompletableSuccess() {
        listener.listen(single.concatWith(completable));
        single.onSuccess("foo");
        listener.verifyNoEmissions();
        completable.onComplete();
        listener.verifySuccess("foo");
    }

    @Test
    public void concatPropagatesCompletableFailure() {
        listener.listen(single.concatWith(completable));
        single.onSuccess("foo");
        listener.verifyNoEmissions();
        completable.onError(DELIBERATE_EXCEPTION);
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }

    @Test
    public void concatPropagatesSingleFailure() {
        listener.listen(single.concatWith(completable));
        single.onError(DELIBERATE_EXCEPTION);
        listener.verifyFailure(DELIBERATE_EXCEPTION);
    }
}
