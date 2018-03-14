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

import org.junit.Test;

import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.VerificationTestUtils.verifyOriginalAndSuppressedCauses;

@SuppressWarnings("ThrowableNotThrown")
public class CompositeExceptionTest {

    @Test
    public void testCauseAndSuppressed() throws Exception {
        CompositeException e = new CompositeException(DELIBERATE_EXCEPTION);
        DeliberateException suppressed1 = new DeliberateException();
        e.add(suppressed1);
        DeliberateException suppressed2 = new DeliberateException();
        e.add(suppressed2);
        e.addAllPendingSuppressed();
        verifyOriginalAndSuppressedCauses(e, DELIBERATE_EXCEPTION, suppressed1);
        verifyOriginalAndSuppressedCauses(e, DELIBERATE_EXCEPTION, suppressed2);
    }
}
