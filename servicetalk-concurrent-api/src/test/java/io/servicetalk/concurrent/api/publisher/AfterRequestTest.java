/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;

import java.util.function.LongConsumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;

class AfterRequestTest extends AbstractWhenRequestTest {
    @Override
    protected <T> PublisherSource<T> doRequest(Publisher<T> publisher, LongConsumer consumer) {
        return toSource(publisher.afterRequest(consumer));
    }
}
