/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.LegacyTestCompletable;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ConcatWithCompletableTest {
    @RegisterExtension
    final ExecutorExtension<Executor> executorExtension = ExecutorExtension.withCachedExecutor();
    private final TestSingleSubscriber<String> listener = new TestSingleSubscriber<>();
    private LegacyTestSingle<String> single = new LegacyTestSingle<>();
    private LegacyTestCompletable completable = new LegacyTestCompletable();

    @Test
    public void concatWaitsForCompletableSuccess() {
        toSource(single.concat(completable)).subscribe(listener);
        single.onSuccess("foo");
        assertThat(listener.pollTerminal(10, MILLISECONDS), is(nullValue()));
        completable.onComplete();
        assertThat(listener.awaitOnSuccess(), is("foo"));
    }

    @Test
    public void concatPropagatesCompletableFailure() {
        toSource(single.concat(completable)).subscribe(listener);
        single.onSuccess("foo");
        assertThat(listener.pollTerminal(10, MILLISECONDS), is(nullValue()));
        completable.onError(DELIBERATE_EXCEPTION);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }

    @Test
    public void concatPropagatesSingleFailure() {
        toSource(single.concat(completable)).subscribe(listener);
        single.onError(DELIBERATE_EXCEPTION);
        assertThat(listener.awaitOnError(), is(DELIBERATE_EXCEPTION));
    }
}
