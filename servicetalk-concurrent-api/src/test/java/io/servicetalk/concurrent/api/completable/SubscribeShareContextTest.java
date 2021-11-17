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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.context.api.ContextMap;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SubscribeShareContextTest {

    static final ContextMap.Key<String> KEY = newKey("share-context-key", String.class);

    @Test
    void contextIsShared() throws Exception {
        AsyncContext.put(KEY, "v1");
        awaitTermination(completed().beforeOnComplete(() -> AsyncContext.put(KEY, "v2")).subscribeShareContext());
        assertThat("Unexpected value found in the context.", AsyncContext.get(KEY), is("v2"));
    }

    @Test
    void contextIsNotSharedIfNotLastOperator() throws Exception {
        // When we support this feature, then we can change this test
        AsyncContext.put(KEY, "v1");
        awaitTermination(completed().beforeOnComplete(() -> AsyncContext.put(KEY, "v2")).subscribeShareContext()
                .beforeOnComplete(() -> { }));
        assertThat("Unexpected value found in the context.", AsyncContext.get(KEY), is("v1"));
    }

    private void awaitTermination(Completable completable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        // completable.toFuture() will use the toSingle() conversion and share context operator will not be effective
        // since it isn't the last operator. So we directly subscribe to the completable.
        toSource(completable).subscribe(new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(final Cancellable cancellable) {
                // Noop
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(final Throwable t) {
                latch.countDown();
            }
        });
        latch.await();
    }
}
