/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class CompositeCancellableTest {


    @RegisterExtension
    public final MockedCancellableHolder holder = new MockedCancellableHolder();

    @Test
    public void testEmpty() {
        assertThrows(IllegalArgumentException.class, () -> holder.init(0));
    }

    @Test
    public void testOne() {
        Cancellable composite = holder.init(1);
        assertThat("Composite of 1 must not create a new instance.", composite, is(holder.components[0]));
        composite.cancel();
    }

    @Test
    public void testTwo() {
        holder.init(2).cancel();
    }

    @Test
    public void testMany() {
        holder.init(5).cancel();
    }

    @Test
    public void cancelThrowsForTwo() {
        testCancelThrows(holder.init(2));
    }

    @Test
    public void cancelThrowsForMany() {
        testCancelThrows(holder.init(5));
    }

    @Test
    public void cancellablesMayContainNull() {
        Cancellable cancellable = holder.init(5);
        holder.components[3] = null;
        cancellable.cancel();
        holder.verify();
    }

    private void testCancelThrows(final Cancellable cancellable) {
        for (Cancellable component : holder.components) {
            doThrow(new DeliberateException()).when(component).cancel();
        }
        assertThrows(DeliberateException.class, cancellable::cancel, "Unexpected exception from cancel.");
        holder.verify();
    }

    static class MockedCancellableHolder implements AfterEachCallback {
        @SuppressWarnings("NotNullFieldNotInitialized")
        Cancellable[] components;

        Cancellable init(int count) {
            components = new Cancellable[count];
            for (int i = 0; i < count; i++) {
                components[i] = mock(Cancellable.class);
            }
            return CompositeCancellable.create(components);
        }

        protected void verify() {
            for (Cancellable component : components) {
                if (component != null) {
                    Mockito.verify(component).cancel();
                }
            }
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            verify();
        }
    }
}
