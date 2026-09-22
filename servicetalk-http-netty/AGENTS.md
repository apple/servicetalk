# AGENTS.md

Most tests in this module bind a real socket and run a real HTTP client against
a real server. They are the largest group of such tests in the repository.

Before you add or change a test here, read
[../.agents/skills/servicetalk-testing/references/transport-and-netty.md](../.agents/skills/servicetalk-testing/references/transport-and-netty.md).
It covers how to bind a server, how to share an `ExecutionContext`, the order
in which to close resources, and when to use `EmbeddedChannel` instead.

Start from [../.agents/skills/servicetalk-testing/SKILL.md](../.agents/skills/servicetalk-testing/SKILL.md)
for the conventions that apply to every test, and from the repository root
[../AGENTS.md](../AGENTS.md) for build commands and constraints.
