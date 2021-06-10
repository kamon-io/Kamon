# Kamon<img align="right" src="https://raw.githubusercontent.com/kamon-io/kamon.io/279f4d3a658437a5182e10d75aa3d55b811b2836/assets/img/kamon/kamon-icon-light.svg" height="150px" style="padding-left: 20px"/>
[![Build Status](https://travis-ci.org/kamon-io/Kamon.svg?branch=master)](https://travis-ci.org/kamon-io/Kamon)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-core_2.13)

Kamon is a set of tools for instrumenting applications running on the JVM. The best way to get started is to go to our
official [Get Started Page](https://kamon.io/get-started/) and start instrumenting your applications right away! There
you will also find guides and reference documentation for the core APIs, instrumentation and reporting modules.

# Importing the project into Intellij IDEA
This project has a library dependency on a subproject of itself (`kamon-tapir`).
As a result of that, if you just clone this project and open it in IDEA, it will fail to import it.
First, run `sbt +kamon-tapir/publishLocal` from the console, and then IDEA will be able to import the project.

## Why is this necessary?
Among our instrumented libraries is `Tapir`, which is also the only library we instrument that does not have a scala 2.11 version. 
In order to be able to cross publish `kamon-bundle` to all scala versions, while excluding `kamon-tapir` from the 2.11 bundle, 
we had to define `kamon-tapir` as a library dependency of `kamon-bundle` (instead of as a subproject).

## License

This software is licensed under the Apache 2 license, quoted below.

Copyright © 2013-2019 the kamon project <https://kamon.io/>

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

    [http://www.apache.org/licenses/LICENSE-2.0]

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
