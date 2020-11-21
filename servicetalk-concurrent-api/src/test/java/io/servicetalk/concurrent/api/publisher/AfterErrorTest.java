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
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

public class AfterErrorTest extends AbstractWhenOnErrorTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Override
    protected <T> PublisherSource<T> doError(Publisher<T> publisher, Consumer<Throwable> consumer) {
        return toSource(publisher.afterOnError(consumer));
    }

    @Override
    @Test
    public void testCallbackThrowsError() {
        DeliberateException srcEx = new DeliberateException();
        doError(Publisher.<String>failed(srcEx), t -> {
            throw DELIBERATE_EXCEPTION;
        }).subscribe(subscriber);
        assertThat(subscriber.awaitOnError(), sameInstance(srcEx));
    }
}
