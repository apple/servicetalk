///*
// * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package io.servicetalk.concurrent.api;
//
//import io.servicetalk.context.api.ContextMap;
//import io.servicetalk.context.api.ContextMapHolder;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import javax.annotation.Nonnull;
//
//import static java.lang.ThreadLocal.withInitial;
//
//final class ContextMapThreadLocal {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(ContextMapThreadLocal.class);
//
//    private static final ThreadLocal<ContextMap> CONTEXT_THREAD_LOCAL =
//            withInitial(ContextMapThreadLocal::newContextMap);
//
//    static ContextMap context() {
//        final Thread t = Thread.currentThread();
//        if (t instanceof ContextMapHolder) {
//            final ContextMapHolder contextMapHolder = (ContextMapHolder) t;
//            ContextMap map = contextMapHolder.context();
//            if (map == null) {
//                map = newContextMap();
//                contextMapHolder.context(map);
//            }
//            return map;
//        } else {
//            return CONTEXT_THREAD_LOCAL.get();
//        }
//    }
//
//    static CapturedContext captureContext() {
//        return toCaptureContext(context());
//    }
//
//    static CapturedContext captureContextCopy() {
//        return toCaptureContext(context().copy());
//    }
//
//    static Scope attachContext(ContextMap contextMap) {
//        ContextMap prev = exchangeContext(contextMap);
//        return !LOGGER.isDebugEnabled() && prev instanceof Scope ? (Scope) prev : () -> detachContext(contextMap, prev);
//    }
//
//    // Used for `CopyOnWriteContextMap.close()`
//    static void setContext(ContextMap contextMap) {
//        final Thread currentThread = Thread.currentThread();
//        if (currentThread instanceof ContextMapHolder) {
//            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
//            asyncContextMapHolder.context(contextMap);
//        } else {
//            CONTEXT_THREAD_LOCAL.set(contextMap);
//        }
//    }
//
//    // Used for CaptureContext operations
//    private static ContextMap exchangeContext(ContextMap contextMap) {
//        final Thread currentThread = Thread.currentThread();
//        ContextMap result;
//        if (currentThread instanceof ContextMapHolder) {
//            final ContextMapHolder asyncContextMapHolder = (ContextMapHolder) currentThread;
//            result = asyncContextMapHolder.context();
//            if (result == null) {
//                result = newContextMap();
//            }
//            asyncContextMapHolder.context(contextMap);
//        } else {
//            result = CONTEXT_THREAD_LOCAL.get();
//            CONTEXT_THREAD_LOCAL.set(contextMap);
//        }
//        return result;
//    }
//
//    private static CapturedContext toCaptureContext(ContextMap contextMap) {
//        return contextMap instanceof CapturedContext ?
//                (CapturedContext) contextMap : new CapturedContextImpl(contextMap);
//    }
//
//    private static final class CapturedContextImpl implements CapturedContext {
//
//        private final ContextMap contextMap;
//
//        CapturedContextImpl(ContextMap contextMap) {
//            this.contextMap = contextMap;
//        }
//
//        @Override
//        public ContextMap captured() {
//            return contextMap;
//        }
//
//        @Override
//        public Scope restoreContext() {
//            return attachContext(contextMap);
//        }
//    }
//
//    private static void detachContext(ContextMap expectedContext, ContextMap toRestore) {
//        ContextMap current = exchangeContext(toRestore);
//        if (current != expectedContext) {
//            LOGGER.debug("Current context didn't match the expected context. current: {}, expected: {}",
//                    current, expectedContext);
//        }
//    }
//
//    private static ContextMap newContextMap() {
//        return new CopyOnWriteContextMap();
//    }
//}
