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
package io.servicetalk.concurrent.api.publisher;

import io.servicetalk.concurrent.api.Publisher;

import org.junit.Test;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;

public class DoBeforeNextTest extends AbstractDoNextTest {
    @Override
    protected <T> Publisher<T> doNext(Publisher<T> publisher, Consumer<T> consumer) {
        return publisher.doBeforeNext(consumer);
    }

    @Test
    public void testCallbackThrowsError() {
        try {
            rule.subscribe(doNext(Publisher.from("Hello"), s -> {
                throw DELIBERATE_EXCEPTION;
            })).request(1);
        } finally {
            rule.verifyFailure(DELIBERATE_EXCEPTION);
        }
    }
}
