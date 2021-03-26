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
import io.servicetalk.concurrent.api.ScanWithLifetimeMapper;

import javax.annotation.Nullable;

import static java.lang.String.valueOf;

public class PublisherScanWithLifetimeTckTest extends AbstractPublisherOperatorTckTest<String> {
    @Override
    protected Publisher<String> composePublisher(Publisher<Integer> publisher, int elements) {
        return publisher.scanWithLifetime(() -> new ScanWithLifetimeMapper<Integer, String>() {
            @Override
            public void afterFinally() {
            }

            @Nullable
            @Override
            public String mapOnNext(@Nullable final Integer next) {
                return valueOf(next);
            }

            @Nullable
            @Override
            public String mapOnError(final Throwable cause) throws Throwable {
                return null;
            }

            @Nullable
            @Override
            public String mapOnComplete() {
                return valueOf(-1);
            }

            @Override
            public boolean mapTerminal() {
                return false;
            }
        });
    }
}
