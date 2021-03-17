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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SubscribeShareContextTest {

    public static final AsyncContextMap.Key<String> KEY = AsyncContextMap.Key.newKey("share-context-key");

    @Test
    public void contextIsShared() throws Exception {
        AsyncContext.put(KEY, "v1");
        // publisher.toFuture() will use the toSingle() conversion and share context operator will not be effective
        // since it isn't the last operator. So we directly subscribe to the publisher.
        awaitTermination(from(1).beforeOnNext(__ -> AsyncContext.put(KEY, "v2")).subscribeShareContext());
        assertThat("Unexpected value found in the context.", AsyncContext.get(KEY), is("v2"));
    }

    @Test
    public void contextIsNotSharedIfNotLastOperator() throws Exception {
        // When we support this feature, then we can change this test
        AsyncContext.put(KEY, "v1");
        // publisher.toFuture() will use the toSingle() conversion and share context operator will not be effective
        // since it isn't the last operator. So we directly subscribe to the publisher.
        awaitTermination(from(1).beforeOnNext(__ -> AsyncContext.put(KEY, "v2")).subscribeShareContext()
                .beforeOnNext(__ -> { }));
        assertThat("Unexpected value found in the context.", AsyncContext.get(KEY), is("v1"));
    }

    private void awaitTermination(Publisher<Integer> publisher) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        // publisher.toFuture() will use the toSingle() conversion and we can not verify offload for
        // Publisher.Subscriber. So we directly subscribe to the publisher.
        toSource(publisher).subscribe(new PublisherSource.Subscriber<Integer>() {
            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(@Nullable final Integer integer) {
                // Noop
            }

            @Override
            public void onError(final Throwable t) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
        latch.await();
    }
}
