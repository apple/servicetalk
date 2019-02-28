/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.collection.IsIterableContainingInOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;

public final class IsIterableEndingWithInOrder<T> extends IsIterableContainingInOrder<T> {

    private final int expectedCount;

    IsIterableEndingWithInOrder(final List<Matcher<? super T>> matchers) {
        super(matchers);
        this.expectedCount = matchers.size();
    }

    @Override
    protected boolean matchesSafely(final Iterable<? extends T> iterable, final Description mismatchDescription) {
        if (!(iterable instanceof Collection)) {
            throw new IllegalArgumentException("IsIterableEndingWithInOrder only works with collections, not " +
                    iterable.getClass());
        }
        final Iterator<? extends T> iterator = iterable.iterator();
        int skip = ((Collection) iterable).size() - expectedCount;
        while (skip-- > 0) {
            iterator.next();
        }
        final List<T> actual = new ArrayList<>(expectedCount);
        while (iterator.hasNext()) {
            actual.add(iterator.next());
        }

        return super.matchesSafely(actual, mismatchDescription);
    }

    @SafeVarargs
    public static <E> Matcher<Iterable<? extends E>> endsWith(E... items) {
        List<Matcher<? super E>> matchers = new ArrayList<>();
        for (E item : items) {
            matchers.add(equalTo(item));
        }

        return new IsIterableEndingWithInOrder<>(matchers);
    }

    public static <E> Matcher<Iterable<? extends E>> endsWith(List<E> items) {
        List<Matcher<? super E>> matchers = new ArrayList<>();
        for (E item : items) {
            matchers.add(equalTo(item));
        }

        return new IsIterableEndingWithInOrder<>(matchers);
    }
}
