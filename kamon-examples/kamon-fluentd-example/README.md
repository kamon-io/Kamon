kamon-fluentd-example
------------------------------

An example Spray application with Kamon monitoring reporting to Fluentd Server.

Prerequisites
---------------
* fluentd:
  ```sh
  you@host:kamon-fluentd-example $ gem install fluentd
  ```

* install kamon snapshots to local:
  ```sh
  you@host:kamon-fluentd-example $ cd ../../
  you@host:Kamon $ sbt "+ publishLocal"
  ... snip...
  [info] 	published ivy to /Users/___/.ivy2/local/io.kamon/kamon-akka-remote_2.11/0.5.2-021ffd253e104342e6b4c75ae42717b51e3b6b26/ivys/ivy.xml
  [success] Total time: 248 s, completed 2015/10/04 0:27:53
  [info] Setting version to 2.10.4
  [info] Reapplying settings...
  [info] Set current project to kamon (in build file:/Users/___/kamon-io/Kamon/)
  ```

* edit build.sbt.  edit `kamonV` variable with installed snapshot version (`0.5.2-021ffd253e104342e6b4c75ae42717b51e3b6b26` in the above example).

How to run
------------
1. just do it: `sbt aspectj-runner:run`
2. you'll see kamon-log-reporter outputs on console.
3. you'll also see kamon metrics sent to fluentd on files named `target/fluentd_out.****`
