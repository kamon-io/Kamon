Play Framework Integration  [![Build Status](https://travis-ci.org/kamon-io/kamon-play.svg?branch=master)
==========================

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

***kamon-play-23*** [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-23_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-23_2.11)

***kamon-play-24*** [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-24_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-24_2.11)

***kamon-play-25*** [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-25_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-play-25_2.11)


The `kamon-play` module ships with bytecode instrumentation that brings automatic traces and segments management and
automatic trace token propagation to your Play! applications. The details on what those features are about can be found
in the [base functionality] documentation section. Here we will dig into the specific aspects of bringing support for them when using
Play!.


The <b>kamon-play</b> module requires you to start your application using the AspectJ Weaver Agent. Kamon will warn you
at startup if you failed to do so.


Since Kamon 0.5.0 we support both Play Framework 2.3, 2.4 and 2.5, but bringing support for Play! 2.4 and 2.5 required us
to ship different modules for each Play! version. Please make sure you add either <b>kamon-play-23</b>, <b>kamon-play-24</b> or
<b>kamon-play-25</b> to your project's classpath.



[base functionality]: http://kamon.io/integrations/web-and-http-toolkits/base-functionality/
