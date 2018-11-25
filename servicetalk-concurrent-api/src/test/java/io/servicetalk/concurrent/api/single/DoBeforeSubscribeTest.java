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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.Single;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class DoBeforeSubscribeTest extends AbstractDoSubscribeTest {
    @Test
    public void testCallbackThrowsError() {
        List<AssertionError> failures = new ArrayList<>();
        doSubscribe(Single.success("Hello"), s -> {
            throw DELIBERATE_EXCEPTION;
        }).subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Cancellable c) {
                failures.add(new AssertionError("onSubscribe invoked unexpectedly."));
            }

            @Override
            public void onError(final Throwable t) {
                failures.add(new AssertionError("onError invoked unexpectedly.", t));
            }

            @Override
            public void onSuccess(@Nullable String val) {
                failures.add(new AssertionError("onSuccess invoked unexpectedly with value: " + val));
            }
        });
        assertThat("Unexpected errors: " + failures, failures, hasSize(0));
    }

    @Override
    protected <T> Single<T> doSubscribe(Single<T> single, Consumer<Cancellable> consumer) {
        return single.doBeforeSubscribe(consumer);
    }
}
