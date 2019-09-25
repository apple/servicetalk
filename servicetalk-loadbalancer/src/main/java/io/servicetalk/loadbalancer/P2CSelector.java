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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ScoreSupplier;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * This {@link LoadBalancer} selection algorithm is based on work by Michael David Mitzenmacher in The Power of Two
 * Choices in Randomized Load Balancing.
 *
 * @param <T> type of {@link ScoreSupplier} resource.
 *
 * @see <a href="https://www.eecs.harvard.edu/~michaelm/postscripts/tpds2001.pdf">Mitzenmacher (2001) The Power of Two
 * Choices in Randomized Load Balancing</a>
 */
final class P2CSelector<T extends ScoreSupplier> implements BiFunction<List<T>, Predicate<T>, T> {

    private final int maxEffort;

    /**
     * Creates a new instance.
     *
     * @param maxEffort maximum nr of match attempts against the {@code selector} {@link Predicate} with selections from
     * the provided {@code entries}, such that selection time is bound by {@code O(maxEffort)} upon which a {@code
     * NULL} value is returned.
     */
    P2CSelector(int maxEffort) {
        this.maxEffort = maxEffort;
    }

    @Override
    public T apply(final List<T> entries, final Predicate<T> selector) {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final int size = entries.size();

        switch (size) {
            case 0:
                return null;
            case 1:
                T c = entries.get(0);
                if (selector.test(c)) {
                    return c;
                }
                return null;
            default:
                for (int j = maxEffort; j > 0; j--) {
                    int i1 = rnd.nextInt(size);
                    int i2 = rnd.nextInt(size);

                    if (i1 == i2) {
                        continue;
                    }

                    T t1 = entries.get(i1);
                    T t2 = entries.get(i2);

                    if (t1.score() > t2.score()) {
                        if (selector.test(t1)) {
                            return t1;
                        }
                    } else if (selector.test(t2)) {
                        return t2;
                    }
                }
        }

        return null;
    }
}
