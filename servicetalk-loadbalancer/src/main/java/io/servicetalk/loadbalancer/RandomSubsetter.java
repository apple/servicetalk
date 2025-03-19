/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

/**
 * A {@link Subsetter} that takes a random subset of the provided hosts.
 */
final class RandomSubsetter implements Subsetter {

    private final int randomSubsetSize;

    RandomSubsetter(int randomSubsetSize) {
        this.randomSubsetSize = ensurePositive(randomSubsetSize, "randomSubsetSize");
    }

    @Override
    public <T extends PrioritizedHost> List<T> subset(List<T> nextHosts) {
        if (nextHosts.size() <= randomSubsetSize) {
            return nextHosts;
        }

        // We need to sort, and then return the list with the subsetSize number of healthy elements.
        List<T> result = new ArrayList<>(nextHosts);
        result.sort(Comparator.comparingLong(PrioritizedHost::randomSeed));

        // We don't want to consider the unhealthy elements to be a part of our subset, so we're going to grow it
        // to account for un-health endpoints. However, we need to know how many that is.
        for (int i = 0, healthyCount = 0; i < result.size(); i++) {
            if (result.get(i).isHealthy()) {
                ++healthyCount;
                if (healthyCount == randomSubsetSize) {
                    // Trim elements after i to form the subset.
                    while (result.size() > i + 1) {
                        result.remove(result.size() - 1);
                    }
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "RandomSubsetter{" +
                "randomSubsetSize=" + randomSubsetSize +
                '}';
    }
}
