# AGENTS.md

Most tests in this module bind a real socket and run a real gRPC client against
a real server, often over the HTTP/2 transport or a Unix domain socket.

Before you add or change a test here, read
[../.agents/skills/servicetalk-testing/references/transport-and-netty.md](../.agents/skills/servicetalk-testing/references/transport-and-netty.md).
It covers how to bind a server, how to share an `ExecutionContext`, and the
order in which to close resources. `GrpcUdsTest` is the exemplar for this
module.

Start from [../.agents/skills/servicetalk-testing/SKILL.md](../.agents/skills/servicetalk-testing/SKILL.md)
for the conventions that apply to every test, and from the repository root
[../AGENTS.md](../AGENTS.md) for build commands and constraints.
