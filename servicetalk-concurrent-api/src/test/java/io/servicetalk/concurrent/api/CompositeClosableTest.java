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

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(30)
final class CompositeClosableTest {

    @ParameterizedTest(name = "{displayName} [{index}] merge={0} gracefully={1}")
    @CsvSource(value = {"true,true", "true,false", "false,true", "false,false"})
    void sameOperationDoesNotSOE(boolean merge, boolean gracefully) throws Exception {
        AsyncCloseable mockClosable = newMock("asyncCloseable");
        CompositeCloseable compositeCloseable = newCompositeCloseable();
        for (int i = 0; i < 100000; ++i) {
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

    @ParameterizedTest(name = "{displayName} [{index}] gracefully={0}")
    @ValueSource(booleans = {true, false})
    void alternatingOperationSOE(boolean gracefully) {
        AsyncCloseable mockClosable = newMock("asyncCloseable");
        CompositeCloseable compositeCloseable = newCompositeCloseable();
        for (int i = 0; i < 100000; ++i) {
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

    @ParameterizedTest(name = "{displayName} [{index}] gracefully={0}")
    @ValueSource(booleans = {true, false})
    void appendPrependOrder(boolean gracefully) throws Exception {
        AsyncCloseable mock1 = newMock("mock1");
        AsyncCloseable mock2 = newMock("mock2");
        InOrder order = inOrder(mock1, mock2);

        CompositeCloseable compositeCloseable = newCompositeCloseable();
        compositeCloseable.append(mock2);
        compositeCloseable.prepend(mock1);

        if (gracefully) {
            compositeCloseable.closeGracefully();
            order.verify(mock1).closeAsyncGracefully();
            order.verify(mock2).closeAsyncGracefully();
        } else {
            compositeCloseable.close();
            order.verify(mock1).closeAsync();
            order.verify(mock2).closeAsync();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] gracefully={0}")
    @ValueSource(booleans = {true, false})
    void mergePrependOrder(boolean gracefully) throws Exception {
        AsyncCloseable mock1 = newMock("mock1");
        AsyncCloseable mock2 = newMock("mock2");
        InOrder order = inOrder(mock1, mock2);

        CompositeCloseable compositeCloseable = newCompositeCloseable();
        compositeCloseable.merge(mock2);
        compositeCloseable.prepend(mock1);

        if (gracefully) {
            compositeCloseable.closeGracefully();
            order.verify(mock1).closeAsyncGracefully();
            order.verify(mock2).closeAsyncGracefully();
        } else {
            compositeCloseable.close();
            order.verify(mock1).closeAsync();
            order.verify(mock2).closeAsync();
        }
    }

    private static AsyncCloseable newMock(String name) {
        AsyncCloseable mockClosable = mock(AsyncCloseable.class, name);
        when(mockClosable.closeAsync()).thenReturn(completed());
        when(mockClosable.closeAsyncGracefully()).thenReturn(completed());
        return mockClosable;
    }
}
