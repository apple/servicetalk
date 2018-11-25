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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContextFilterTest {
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public final MockedSingleListenerRule<Boolean> listener = new MockedSingleListenerRule<>();

    @Mock
    private ConnectionContext context;

    @Spy
    private ContextFilter first;
    @Spy
    private ContextFilter second;

    @Test
    public void chainingTrueThenTrueShouldReturnTrue() {
        setFilterResult(first, Single.success(true));
        setFilterResult(second, Single.success(true));

        listener.listen(first.andThen(second).filter(context));
        listener.verifySuccess(TRUE);

        verify(first).filter(context);
        verify(second).filter(context);
    }

    @Test
    public void chainingTrueThenFalseShouldReturnFalse() {
        setFilterResult(first, Single.success(true));
        setFilterResult(second, Single.success(false));

        listener.listen(first.andThen(second).filter(context));
        listener.verifySuccess(FALSE);

        verify(first).filter(context);
        verify(second).filter(context);
    }

    @Test
    public void chainingTrueThenErrorShouldReturnError() {
        setFilterResult(first, Single.success(true));
        setFilterResult(second, Single.error(DELIBERATE_EXCEPTION));

        listener.listen(first.andThen(second).filter(context));
        listener.verifyFailure(DeliberateException.class);

        verify(first).filter(context);
        verify(second).filter(context);
    }

    @Test
    public void chainingAfterFalseShouldNotCallNextFilter() {
        setFilterResult(first, Single.success(false));

        listener.listen(first.andThen(second).filter(context));
        listener.verifySuccess(FALSE);

        verify(first).filter(context);
        verify(second, never()).filter(any(ConnectionContext.class));
    }

    @Test
    public void chainingAfterNullShouldNotCallNextFilter() {
        setFilterResult(first, Single.success(null));

        listener.listen(first.andThen(second).filter(context));
        listener.verifySuccess(null);

        verify(first).filter(context);
        verify(second, never()).filter(any(ConnectionContext.class));
    }

    @Test
    public void chainingAfterErrorShouldNotCallNextFilter() {
        setFilterResult(first, Single.error(DELIBERATE_EXCEPTION));

        listener.listen(first.andThen(second).filter(context));
        listener.verifyFailure(DeliberateException.class);

        verify(first).filter(context);
        verify(second, never()).filter(any(ConnectionContext.class));
    }

    private void setFilterResult(final ContextFilter filter, final Single<Boolean> resultSingle) {
        when(filter.filter(context)).thenReturn(resultSingle);
    }
}
