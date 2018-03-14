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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Verifier;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class CompositeCancellableTest {

    @Rule
    public final MockedCancellableHolder holder = new MockedCancellableHolder();

    @Test(expected = IllegalArgumentException.class)
    public void testEmpty() {
        holder.init(0);
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

    private static class MockedCancellableHolder extends Verifier {

        Cancellable[] components;

        Cancellable init(int count) {
            components = new Cancellable[count];
            for (int i = 0; i < count; i++) {
                components[i] = mock(Cancellable.class);
            }
            return CompositeCancellable.create(components);
        }

        @Override
        protected void verify() {
            for (Cancellable component : components) {
                Mockito.verify(component).cancel();
            }
        }
    }
}
