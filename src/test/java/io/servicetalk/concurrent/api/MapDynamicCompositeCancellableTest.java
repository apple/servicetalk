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

import io.servicetalk.concurrent.Cancellable;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class MapDynamicCompositeCancellableTest {

    @Test
    public void testAddAndRemove() {
        MapDynamicCompositeCancellable c = new MapDynamicCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        c.add(cancellable);
        c.remove(cancellable);
        c.cancel();
        verifyZeroInteractions(cancellable);
    }

    @Test
    public void testCancel() {
        MapDynamicCompositeCancellable c = new MapDynamicCompositeCancellable();
        Cancellable cancellable = mock(Cancellable.class);
        c.add(cancellable);
        c.cancel();
        verify(cancellable).cancel();
        c.remove(cancellable);
    }

    @Test
    public void testAddPostCancel() {
        MapDynamicCompositeCancellable c = new MapDynamicCompositeCancellable();
        c.cancel();
        Cancellable cancellable = mock(Cancellable.class);
        c.add(cancellable);
        verify(cancellable).cancel();
        c.remove(cancellable);
    }
}
