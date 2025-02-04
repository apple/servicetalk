package io.servicetalk.concurrent.api;

import io.servicetalk.context.api.ContextMap;
import io.servicetalk.context.api.ContextMapHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static java.lang.ThreadLocal.withInitial;

final class ContextMapThreadLocal {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextMapThreadLocal.class);

    protected static final ThreadLocal<ContextMap> CONTEXT_THREAD_LOCAL =
            withInitial(ContextMapThreadLocal::newContextMap);

    @Nonnull
    static ContextMap context() {
        final Thread t = Thread.currentThread();
        if (t instanceof ContextMapHolder) {
            final ContextMapHolder contextMapHolder = (ContextMapHolder) t;
            ContextMap map = contextMapHolder.context();
            if (map == null) {
                map = newContextMap();
                contextMapHolder.context(map);
            }
            return map;
        } else {
            return CONTEXT_THREAD_LOCAL.get();
        }
    }

    static Scope attachContext(ContextMap contextMap) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap prev = asyncContextMapHolder.context();
            asyncContextMapHolder.context(contextMap);
            return prev == null ? Scope.NOOP : () -> detachContext(contextMap, prev);
        } else {
            return slowPathSetContext(contextMap);
        }
    }

    private static Scope slowPathSetContext(ContextMap contextMap) {
        ContextMap prev = CONTEXT_THREAD_LOCAL.get();
        CONTEXT_THREAD_LOCAL.set(contextMap);
        return () -> detachContext(contextMap, prev);
    }

    private static void detachContext(ContextMap expectedContext, ContextMap toRestore) {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ContextMapHolder) {
            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
            ContextMap current = asyncContextMapHolder.context();
            if (current != expectedContext) {
                LOGGER.warn("Current context didn't match the expected context. current: {}, expected: {}",
                        current, expectedContext);
            }
            asyncContextMapHolder.context(toRestore);
        } else {
            slowPathDetachContext(expectedContext, toRestore);
        }
    }

    private static void slowPathDetachContext(ContextMap expectedContext, ContextMap toRestore) {
        ContextMap current = CONTEXT_THREAD_LOCAL.get();
        if (current != expectedContext) {
            LOGGER.warn("Current context didn't match the expected context. current: {}, expected: {}",
                    current, expectedContext);
        }
        CONTEXT_THREAD_LOCAL.set(toRestore);
    }

    private static ContextMap newContextMap() {
        return new CopyOnWriteContextMap();
    }
}
