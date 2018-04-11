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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.AbstractHttpHeadersTest;
import io.servicetalk.http.api.HttpHeaders;

import org.junit.Ignore;
import org.junit.Test;

public class NettyToServiceTalkHttpHeadersTest extends AbstractHttpHeadersTest {
    @Override
    protected HttpHeaders newHeaders() {
        return new NettyToServiceTalkHttpHeaders(new io.netty.handler.codec.http.DefaultHttpHeaders());
    }

    @Override
    protected HttpHeaders newHeaders(final int initialSizeHint) {
        return newHeaders();
    }

    @Ignore("netty bug ... valueIterator(name) doesn't iterate in insertion order")
    @Override
    public void multipleValuesPerNameShouldBeAllowed() {
    }

    @Ignore("netty bug ... valueIterator(name) doesn't iterate in insertion order")
    @Override
    public void testGetAndRemove() {
    }

    @Ignore("netty bug ... valueIterator(name) doesn't iterate in insertion order")
    @Override
    public void setArrayShouldOverWritePreviousValue() {
    }

    @Ignore("netty bug ... valueIterator(name) doesn't iterate in insertion order")
    @Override
    public void setIterableShouldOverWritePreviousValue() {
    }

    @Ignore("netty bug ... valueIterator(name) doesn't iterate in insertion order")
    @Override
    public void minimalBucketsIterationOrder() {
    }

    @Ignore("netty bug ... valueIterator(name) doesn't iterate in insertion order")
    @Override
    public void removalAndInsertionDuringIteration() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    @Test
    public void removalAndInsertionConcurrentModification() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    public void getValueIteratorRemove() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    public void overallIteratorRemoveFirstAndLast() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    public void overallIteratorRemoveAll() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    public void testSimultaneousIteratorRemove() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    public void overallIteratorRemoveMiddle() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    @Test
    public void entryIteratorThrowsIfNoNextCall() {
    }

    @Ignore("netty Iterator doesn't support remove")
    @Override
    @Test
    public void entryIteratorThrowsIfDoubleRemove() {
    }
}
