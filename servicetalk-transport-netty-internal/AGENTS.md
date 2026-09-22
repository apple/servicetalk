# AGENTS.md

This module owns the shared transport test fixtures that the rest of the
repository depends on: `AddressUtils`, `ExecutionContextExtension`,
`EmbeddedDuplexChannel`, `CloseUtils`, `MockFlushStrategy`, and
`RandomDataUtils`, all under `src/testFixtures/java`.

Two consequences:

- Its own tests mostly drive an `EmbeddedChannel` rather than a real socket.
  Prefer that when it can answer the question.
- A change to anything in `src/testFixtures` affects many other modules. Check
  the callers before you change a signature.

[../.agents/skills/servicetalk-testing/references/transport-and-netty.md](../.agents/skills/servicetalk-testing/references/transport-and-netty.md)
documents these fixtures and how consumers use them. Keep it in step when you
change one.

Start from [../.agents/skills/servicetalk-testing/SKILL.md](../.agents/skills/servicetalk-testing/SKILL.md)
for the conventions that apply to every test, and from the repository root
[../AGENTS.md](../AGENTS.md) for build commands and constraints.
