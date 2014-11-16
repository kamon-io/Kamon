
### Kamon Sigar 1) agent 2) extractor 3) loader.

To load as JVM agent:
```
java -javaagent:/path/to/kamon-sigar.jar
# OR
java -javaagent:/path/to/kamon-sigar.jar=/path/to/sigar/extract/folder ...

```

To load from your code:
```
		import java.io.File;
		import kamon.sigar.SigarAgent;
		import org.hyperic.sigar.Sigar;

		SigarAgent.provision();
		final Sigar sigar = new Sigar();

// OR
	
		final File extractLocation = new File("./target/native");
		SigarAgent.provision(extractLocation);
		final Sigar sigar = new Sigar();

```
