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
package io.servicetalk.concurrent.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class CompositeClosableTest {
    @ParameterizedTest
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void sameOperationDoesNotSOE(boolean merge, boolean gracefully) throws Exception {
        AsyncCloseable mockClosable = mock(AsyncCloseable.class);
        when(mockClosable.closeAsync()).thenReturn(completed());
        when(mockClosable.closeAsyncGracefully()).thenReturn(completed());

        CompositeCloseable compositeCloseable = newCompositeCloseable();
        for (int i = 0; i < 10000; ++i) {
            if (merge) {
                compositeCloseable.merge(mockClosable);
            } else {
                compositeCloseable.append(mockClosable);
            }
        }

        if (gracefully) {
            compositeCloseable.closeGracefully();
        } else {
            compositeCloseable.close();
        }
    }

    @ParameterizedTest(name = "gracefully={0}")
    @ValueSource(booleans = {true, false})
    void alternatingOperationSOE(boolean gracefully) {
        AsyncCloseable mockClosable = mock(AsyncCloseable.class);
        when(mockClosable.closeAsync()).thenReturn(completed());
        when(mockClosable.closeAsyncGracefully()).thenReturn(completed());

        CompositeCloseable compositeCloseable = newCompositeCloseable();
        for (int i = 0; i < 10000; ++i) {
            if ((i & 1) == 0) {
                compositeCloseable.merge(mockClosable);
            } else {
                compositeCloseable.append(mockClosable);
            }
        }

        // We currently don't offer protection across different operations. Large cardinality mixed operations is not
        // common but could be addressed in the future if it becomes common.
        assertThrows(StackOverflowError.class, () -> {
            if (gracefully) {
                compositeCloseable.closeGracefully();
            } else {
                compositeCloseable.close();
            }
        });
    }
}
