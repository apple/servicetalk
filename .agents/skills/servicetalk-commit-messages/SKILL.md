---
name: servicetalk-commit-messages
description: "Use when writing a commit message or a pull request title and description in the ServiceTalk repository, including amending or squashing commits before they land on main."
---

# Writing commit messages in ServiceTalk

A commit message tells a human **what** changed and **why**. The diff already
shows **how**. Every sentence must give the reader something the diff cannot.

The reader is a ServiceTalk maintainer or user who knows the codebase, HTTP,
gRPC, and the relevant RFCs, and who has the diff open.

Pull requests are squash-merged: the PR title becomes the subject and the PR
description becomes the body of the commit on `main`. Write them as one
message. A fixup commit on a PR branch is squashed away, so a subject line is
enough for it.

## The message

```
<area>: <what is different, in the imperative>

#### Motivation

<One paragraph: what goes wrong or is missing, when, and for whom.>

#### Modifications

<Optional lead paragraph: the idea behind the bullets.>

- <At most five bullets, one or two sentences each.>

#### Result

<One or two sentences: what is different now, for a user of ServiceTalk.>

<Callout paragraph(s) — see "Breaking and behavior changes".>
```

**Subject.** Lowercase `<area>:` prefix (the module name without
`servicetalk-`, such as `http-netty` or `loadbalancer`) when the change stays in
one area. Name the effect, not the mechanism:
`don't strand connections on hosts that can't grow their pool`, not
`add canGrowPool parameter to ConnectionSelector`.

**Motivation** is one paragraph: the symptom, the trigger, and the
consequence. Give the root cause in one sentence at the level a user would
recognize — "sizing the buffer as `available + 1` overflows" — not as a trace
through the code.

**Modifications** is an optional lead paragraph, then at most five bullets of
one or two sentences each. Write the lead paragraph only when the bullets alone
do not show the idea that ties them together. It states that idea in terms a
user would recognize, such as "a forced core pool now declines a connection
only when the host can open a new one."
The lead paragraph and the bullets cover only these kinds of change:

- public API that is added, changed, deprecated, or removed
- configuration: builder options, system properties, defaults, limits
- runtime behavior a user can observe: wire output, errors, status codes, logs
- the one design decision a reviewer is most likely to question, with the
  reason it beat the obvious alternative in one sentence

When several classes get the same change, name the method or property once and
say where it applies: "Add `maxDecompressedBytes(long)` to the gzip and deflate
builders." The diff has the full list.

Private classes, helpers, call order, renamed fields, Javadoc, Gradle and
lockfile edits, and tests belong to the diff. Leave them there. Two exceptions,
each keyed to something you can check:

- If the change *is* test or build work (a flaky test, a CI job), those are
  the modifications.
- If you verified the change in a way the diff cannot show (a reproduction run
  1000 times on CI), say so in Result.

**Result** is one or two sentences that state only what is different now, from
the user's side: what now works, fails, or is configurable. What stays the same
is the default, and goes unsaid. The callout paragraphs below follow these
sentences and do not count toward them.

## Breaking and behavior changes

Check both conditions for every change. Write a labeled paragraph at the end of
Result for each one that holds:

| Label | Condition |
|---|---|
| `Breaking change:` | Code, configuration, or a build that worked before now fails to compile, link, or start. For example: a public type or method in a non-`-internal` module is renamed, removed, or changes signature; a system property is removed; a minimum Java or dependency version goes up. |
| `Behavior change:` | The same code compiles but acts differently for a user. For example: a new or changed default or limit; input that was accepted is now rejected; a different status code, exception, or wire output. |

Each paragraph says who is affected, what now happens, and what to do:

```
Breaking change: `RequestTracker.ErrorClass.isLocal()` is removed, and
`LoadBalancingPolicy.name()` is no longer public. Callers that need to know
whether an error is local must check the `ErrorClass` value directly.
```

`-experimental` modules count; `-internal` modules do not. When neither
condition holds, Result ends after its first paragraph.

## Example

The original message for #3475 was 326 words. It walked through pipeline
placement, cleanup helpers, and property parsing, and put the behavior change
mid-paragraph. The same change, written to this recipe:

```
encoding-netty: cap decompressed size to prevent decompression bombs

#### Motivation

The aggregated gzip and deflate decoders put no limit on total decompressed
output. A small compressed payload can expand to gigabytes and exhaust the heap
of any service that accepts compressed content from clients. The streaming
decoders are already bounded by back-pressure.

#### Modifications

- Add `maxDecompressedBytes(long)` to `ZipCompressionBuilder` and the
  deprecated `ZipContentCodecBuilder`. The default is 64 MiB, and `0` disables
  the cap.
- Add the `io.servicetalk.encoding.netty.maxDecompressedBytes` system property
  to override the default for the whole process.

#### Result

Aggregated decoders reject decompression bombs by default.

Behavior change: an aggregated decode whose output exceeds 64 MiB now fails
with `BufferEncodingException`. Raise the limit per builder or with the system
property.
```

## Before you commit

1. Each section holds only what the recipe above lists for it.
2. Read every sentence. Keep it only if it states a reason, a symptom, a
   consequence for a user, or a name the user must type. Delete the rest.
3. Both callout conditions are checked. Each one that holds has its own
   labeled paragraph at the end of Result.
4. American English, plain words, for an international audience
   (`CONTRIBUTING.adoc`). Code identifiers in backticks.
