/**
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
package io.servicetalk.concurrent.api;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MapPublisherTest {
    private TestPublisher<Integer> source;
    @Rule
    public final MockedSubscriberRule<Boolean> subscriber = new MockedSubscriberRule<>();

    @Before
    public void setUp() throws Exception {
        source = new TestPublisher<>(true);
    }

    @Test
    public void testMapFunctionReturnsNull() {
        Publisher<String> map = source.map(v -> null);

        MockedSubscriberRule<String> subscriber1 = new MockedSubscriberRule<>();
        subscriber1.subscribe(map, false);

        source.sendOnSubscribe();

        subscriber1.verifySubscribe();

        subscriber1.request(2);
        source.verifyRequested(2);
        source.sendItems(1, 2);
        subscriber1.verifyItems(sub -> verify(sub, times(2)), new String[] {null});
    }
}
