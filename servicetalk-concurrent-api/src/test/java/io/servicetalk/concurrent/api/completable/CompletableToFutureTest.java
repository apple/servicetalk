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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.api.AbstractToFutureTest;
import io.servicetalk.concurrent.api.TestCompletable;

import java.util.concurrent.Future;

class CompletableToFutureTest extends AbstractToFutureTest<Void> {

    private final TestCompletable source = new TestCompletable.Builder().build(subscriber -> {
        subscriber.onSubscribe(mockCancellable);
        return subscriber;
    });

    @Override
    protected boolean isSubscribed() {
        return source.isSubscribed();
    }

    @Override
    protected Future<Void> toFuture() {
        return source.toFuture();
    }

    @Override
    protected void completeSource() {
        source.onComplete();
    }

    @Override
    protected void failSource(final Throwable t) {
        source.onError(t);
    }

    @Override
    protected Void expectedResult() {
        return null;
    }
}
