/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.reactivestreams.tck;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.ScanConcatMapper;

import javax.annotation.Nullable;

import static java.lang.String.valueOf;

public class PublisherScanConcatTckTest extends AbstractPublisherOperatorTckTest<String> {
    @Override
    protected Publisher<String> composePublisher(Publisher<Integer> publisher, int elements) {
        return publisher.scanConcat(() -> new ScanConcatMapper<Integer, String>() {
            @Nullable
            @Override
            public String onNext(@Nullable final Integer next) {
                return valueOf(next);
            }

            @Nullable
            @Override
            public String onError(final Throwable t) throws Throwable {
                throw t;
            }

            @Nullable
            @Override
            public String onComplete() {
                return null;
            }

            @Override
            public boolean mapTerminalSignal() {
                return false;
            }
        });
    }
}
