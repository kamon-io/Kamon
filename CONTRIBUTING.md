Contributing to Kamon
=====================

Thanks for your intention on collaborating to the Kamon Project! It doesn't matter if you want to provide a small change
to our docs, are lost in configuration or want contribute a brand new feature, we value all of your contributions and
the time you take to use our tool and prepare a contribution, we only ask you to follow this guidance depending on your
situation: 

If you are experiencing a bug
-----------------------------

If you see weird exceptions in your log or something definitely is working improperly please [open an issue] and include
the Kamon, Akka and Spray/Play! versions that you are using along with as many useful information you can find related 
to the issue. If you can provide a gist or a short way to reproduce the issue we will be more than happy!

If you don't know what is wrong
-------------------------------

If you don't see any metrics at all or features are not working maybe you have a setup or configuration problem, to
address this kind of problems please send us a emails to our [mailing list] and we will reply as soon as we can! Again,
please include the relevant version and current setup information to speed up the process. If you are in doubt of
whether you have a bug or a configuration problem, email us and we will take care of openning a issue if necessary.

If you want to make a code contribution to the project
------------------------------------------------------

Awesome! First, please note that we try to follow the [commit message conventions] used by the Spray guys and we need
you to electronically fill our [CLA] before accepting your contribution. Also, if your PR contains various commits,
please squash them into a single commit.

### Building and Testing with Mill

Kamon uses [Mill](https://mill-build.org/) as its build tool. Use the bundled `./mill` wrapper script.

- **Compile all modules:**
  ```bash
  ./mill __.compile
  ```

- **Compile a specific module:**
  ```bash
  ./mill core.core[2.13.13].compile
  ./mill 'reporters.prometheus[3.3.7].compile'
  ```

- **Run tests:**
  ```bash
  ./mill core.__.test
  ./mill reporters.__.test
  ./mill instrumentation.__.test
  ```

- **Run tests for a single module:**
  ```bash
  ./mill 'reporters.prometheus[2.13.13].test'
  ```

### Code Formatting

This project uses [Scalafmt](https://scalameta.org/scalafmt/) for code style. Ensure code is formatted before submitting a PR:

- **Reformat all Scala sources:**
  ```bash
  ./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll
  ```

- **Verify formatting:**
  ```bash
  ./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll
  ```

### IDE and BSP Setup

To set up your IDE (IntelliJ IDEA, VS Code with Metals, etc.) using the Build Server Protocol (BSP):

```bash
./mill mill.bsp.BSP/install
```

This generates the BSP connection file in `.bsp/mill-bsp.json`, allowing your IDE to import the project via BSP.


[open an issue]: https://github.com/kamon-io/Kamon/issues/new
[mailing list]: https://groups.google.com/forum/#!forum/kamon-user
[commit message conventions]: http://spray.io/project-info/contributing/
[CLA]: https://docs.google.com/forms/d/1G_IDrBTFzOMwHvhxfKRBwNtpRelSa_MZ6jecH8lpTlc/viewform
