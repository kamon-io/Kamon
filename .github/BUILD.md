# SBT CI groups

CI compiles once, runs core tests, then runs the remaining groups independently:

```text
compile -> core -> Akka / Pekko / Play / other instrumentation / reporters / packaging
```

The instrumentation matrix uses `fail-fast: false`, so a failing group does not
cancel its siblings. GitHub's **Re-run failed jobs** reuses the successful compile
job's artifact; it does not require recompiling every group.

The equivalent local commands are:

```sh
sbt ciCompile
sbt '+core/test'
sbt '+ciAkka/test'
sbt '+ciPekko/test'
sbt '+ciPlay/test'
sbt '+ciOther/test'
sbt '+reporters/test'
sbt '+bundle/packageBin;+packageBin;+packageSrc;+makePom'
```

`+` preserves each project's supported Scala versions. The groups are defined in
`build.sbt`; instrumentation not explicitly assigned to Akka, Pekko, or Play goes
into `ciOther`. Keep new instrumentation in `instrumentationProjects`.

`ciCompile` also compiles the custom test configurations invoked by Akka, Play,
Cassandra, and Tapir's overridden test tasks through project-scoped
`ciCompileTests` tasks. These wrappers avoid SBT shell/cross-command parsing issues
with custom configuration names. Add new custom test configurations there too,
otherwise they will first compile in the test job.

The compile artifact contains all `target/` directories, including Zinc analysis,
generated sources, resources, packaged dependencies, and the build definition.
Every downstream job restores the exact artifact ID from the compile job, not a
possibly stale cache of compiled classes. Dependency/tool caches are separate.
All jobs use the same checkout layout and JDK because SBT output contains absolute
paths. The artifact is for reuse within a workflow run, not between revisions or
on a different local checkout path.

SBT still checks incremental compilation when running tests; restoring output is
not a request to skip dependency checks. Generators and repackaged dependency jars
can still invalidate small dependent modules (local cross-compilation recompiled
one testkit source between Scala switches). Check GitHub runner logs to measure
reuse; local runs cannot establish cross-runner Zinc reuse or failed-job retry
behavior. The jobs are independently retryable, not guaranteed compilation-free.

Packaging generates binary jars, source jars, and POMs without publishing or using
release credentials. It uses the existing SBT packaging tasks, not the Mill
branch's bundle-content verifier.
