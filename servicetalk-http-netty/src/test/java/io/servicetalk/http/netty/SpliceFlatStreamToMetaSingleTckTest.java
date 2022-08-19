/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.reactivestreams.tck.AbstractPublisherOperatorTckTest;

import org.testng.annotations.Test;

import static io.servicetalk.concurrent.api.Publisher.from;

@Test
public class SpliceFlatStreamToMetaSingleTckTest extends AbstractPublisherOperatorTckTest<Integer> {

    @Override
    protected Publisher<Integer> composePublisher(final Publisher<Integer> publisher, final int elements) {
        Publisher<Integer> composed = publisher.liftSyncToSingle(new SpliceFlatStreamToMetaSingle<>(Result::new))
                .flatMapPublisher(result -> from(result.first).concat(result.following));
        // For TCK only, convert an error the splice generates for an empty stream to onComplete():
        return elements == 0 ? composed.onErrorComplete(IllegalStateException.class) : composed;
    }

    private static final class Result {
        private final Integer first;
        private final Publisher<Integer> following;

        Result(Integer first, Publisher<Integer> following) {
            this.first = first;
            this.following = following;
        }
    }
}
