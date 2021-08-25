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
package io.servicetalk.transport.api;

/**
 * Context for controlling listen behavior.
 */
public interface ServerListenContext {
    /**
     * Toggles the server's ability to accept new connections.
     * <p>
     * Passing a {@code false} value will signal the server to stop accepting new connections.
     * It won't affect any other interactions to currently open connections (i.e., reads / writes).
     * <p>
     * Depending on the transport, connections may still get ESTABLISHED, see
     * {@link ServiceTalkSocketOptions#SO_BACKLOG backlog} or OS wide settings:
     * <ul>
     *     <li>Linux: <a href="https://www.kernel.org/doc/Documentation/networking/ip-sysctl.txt">SOMAXCONN</a></li>
     *     <li>MacOS/BSD: <a href="https://docs.freebsd.org/en/books/handbook/config/#configtuning-kernel-limits">
     *         kern.ipc.somaxconn / kern.ipc.soacceptqueue</a></li>
     * </ul>
     * For instance, in case of TCP the 3-way handshake may finish, and the connection will await in the
     * accept queue to be accepted. If the accept queue is full, connection SYNs will await in the
     * SYN backlog (in the case of linux). This can be tuned:
     * <a href="https://www.kernel.org/doc/Documentation/networking/ip-sysctl.txt">tcp_max_syn_backlog</a>
     * These additional parameters may affect the behavior of new flows when the service is not accepting.
     * <p>
     * Depending on how long this stays in the {@code false} state, it may affect other timeouts (i.e., connect-timeout
     * or idleness) on the peer-side and/or the other flows to the peer (i.e., proxies).
     * <p>
     * Considerations:
     * <ul>
     * <li>Upon resumption, {@code accept == true}, backlogged connections will be processed first,
     * which may be inactive by that time.</li>
     * <li>The effect of toggling connection acceptance may be lazy evaluated (implementation detail), meaning
     * that connections may still go through even after setting this to {@code false}.</li>
     * </ul>
     * @param accept Toggles the server's accepting connection ability.
     */
    void acceptConnections(boolean accept);
}
