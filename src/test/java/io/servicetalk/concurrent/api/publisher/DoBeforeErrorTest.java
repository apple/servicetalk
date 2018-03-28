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

import io.servicetalk.concurrent.api.DeliberateException;
import io.servicetalk.concurrent.api.Publisher;

import org.junit.Test;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;

public class DoBeforeErrorTest extends AbstractDoErrorTest {

    @Override
    protected <T> Publisher<T> doError(Publisher<T> publisher, Consumer<Throwable> consumer) {
        return publisher.doBeforeError(consumer);
    }

    @Override
    @Test
    public void testCallbackThrowsError() {
        DeliberateException srcEx = new DeliberateException();
        rule.subscribe(doError(Publisher.<String>error(srcEx), t -> {
            throw DELIBERATE_EXCEPTION;
        })).request(1);
        rule.verifyFailure(DELIBERATE_EXCEPTION);
    }
}
