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

import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.internal.ArrayUtils.indexOf;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static java.util.Objects.requireNonNull;

final class CopyOnWriteExecutorPluginSet implements ExecutorPlugin {
    private final AtomicReference<CopyOnWriteSet> setRef = new AtomicReference<>(EmptyExecutorPluginSet.INSTANCE);

    boolean add(ExecutorPlugin plugin) {
        requireNonNull(plugin);
        for (;;) {
            CopyOnWriteSet set = setRef.get();
            CopyOnWriteSet afterAddSet = set.add(plugin);
            if (set == afterAddSet) {
                return false;
            } else if (setRef.compareAndSet(set, afterAddSet)) {
                return true;
            }
        }
    }

    boolean remove(ExecutorPlugin plugin) {
        for (;;) {
            CopyOnWriteSet set = setRef.get();
            CopyOnWriteSet afterRemoveSet = set.remove(plugin);
            if (set == afterRemoveSet) {
                return false;
            } else if (setRef.compareAndSet(set, afterRemoveSet)) {
                return true;
            }
        }
    }

    void clear() {
        setRef.set(EmptyExecutorPluginSet.INSTANCE);
    }

    @Override
    public Executor wrapExecutor(final Executor result) {
        return setRef.get().wrapExecutor(result);
    }

    private interface CopyOnWriteSet extends ExecutorPlugin {
        CopyOnWriteSet add(ExecutorPlugin plugin);

        CopyOnWriteSet remove(ExecutorPlugin plugin);
    }

    private static final class EmptyExecutorPluginSet implements CopyOnWriteSet {
        static final CopyOnWriteSet INSTANCE = new EmptyExecutorPluginSet();

        private EmptyExecutorPluginSet() {
            // singleton
        }

        @Override
        public CopyOnWriteSet add(final ExecutorPlugin plugin) {
            return new OneExecutorPluginSet(plugin);
        }

        @Override
        public CopyOnWriteSet remove(final ExecutorPlugin plugin) {
            return this;
        }

        @Override
        public Executor wrapExecutor(final Executor result) {
            return result;
        }
    }

    private static final class OneExecutorPluginSet implements CopyOnWriteSet {
        private final ExecutorPlugin plugin;

        OneExecutorPluginSet(ExecutorPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public CopyOnWriteSet add(final ExecutorPlugin plugin) {
            return this.plugin.equals(plugin) ? this : new TwoExecutorPluginSet(this.plugin, plugin);
        }

        @Override
        public CopyOnWriteSet remove(final ExecutorPlugin plugin) {
            return this.plugin.equals(plugin) ? EmptyExecutorPluginSet.INSTANCE : this;
        }

        @Override
        public Executor wrapExecutor(final Executor result) {
            return plugin.wrapExecutor(result);
        }
    }

    private static final class TwoExecutorPluginSet implements CopyOnWriteSet {
        private final ExecutorPlugin first;
        private final ExecutorPlugin second;

        TwoExecutorPluginSet(ExecutorPlugin first, ExecutorPlugin second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public CopyOnWriteSet add(final ExecutorPlugin plugin) {
            return first.equals(plugin) || second.equals(plugin) ? this :
                    new ThreeOrMoreExecutorPluginSet(first, second, plugin);
        }

        @Override
        public CopyOnWriteSet remove(final ExecutorPlugin plugin) {
            if (first.equals(plugin)) {
                return new OneExecutorPluginSet(second);
            } else if (second.equals(plugin)) {
                return new OneExecutorPluginSet(first);
            }
            return this;
        }

        @Override
        public Executor wrapExecutor(final Executor result) {
            return second.wrapExecutor(first.wrapExecutor(result));
        }
    }

    private static final class ThreeOrMoreExecutorPluginSet implements CopyOnWriteSet {
        private final ExecutorPlugin[] plugins;

        ThreeOrMoreExecutorPluginSet(ExecutorPlugin... plugins) {
            this.plugins = plugins;
        }

        @Override
        public CopyOnWriteSet add(final ExecutorPlugin plugin) {
            int i = indexOf(plugin, plugins);
            if (i >= 0) {
                return this;
            }
            ExecutorPlugin[] newArray = copyOf(plugins, plugins.length + 1);
            newArray[plugins.length] = plugin;
            return new ThreeOrMoreExecutorPluginSet(newArray);
        }

        @Override
        public CopyOnWriteSet remove(final ExecutorPlugin plugin) {
            int i = indexOf(plugin, plugins);
            if (i < 0) {
                return this;
            }
            if (plugins.length == 3) {
                switch (i) {
                    case 0:
                        return new TwoExecutorPluginSet(plugins[1], plugins[2]);
                    case 1:
                        return new TwoExecutorPluginSet(plugins[0], plugins[2]);
                    case 2:
                        return new TwoExecutorPluginSet(plugins[0], plugins[1]);
                    default:
                        throw new IllegalStateException("programming error. i: " + i);
                }
            }
            ExecutorPlugin[] newArray = new ExecutorPlugin[plugins.length - 1];
            arraycopy(plugins, 0, newArray, 0, i);
            arraycopy(plugins, i + 1, newArray, i, plugins.length - i - 1);
            return new ThreeOrMoreExecutorPluginSet(newArray);
        }

        @Override
        public Executor wrapExecutor(final Executor result) {
            // We know there is at least 3 elements in the array.
            Executor executor = plugins[2].wrapExecutor(plugins[1].wrapExecutor(plugins[0].wrapExecutor(result)));
            for (int i = 3; i < plugins.length; ++i) {
                executor = plugins[i].wrapExecutor(executor);
            }
            return executor;
        }
    }
}
