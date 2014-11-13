/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.sigar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarLoader;

/**
 * ### Kamon Sigar 1) agent 2) extractor 3) loader.
 * 
 * To load as JVM agent:
 * 
 * <pre>
 * java -javaagent:/path/to/kamon-sigar.jar=/path/to/sigar/extract/folder ...
 * </pre>
 */
public class SigarAgent {

	private static final Logger logger = Logger.getLogger(SigarAgent.class
			.getName());

	/** Agent command line options. */
	private static volatile String options;

	/** Agent injected JVM instrumentation. */
	private static volatile Instrumentation instrumentation;

	/**
	 * Location of native libraries.
	 * 
	 * <pre>
	 * when stored in kamon-sigar.jar:    /${LIB_DIR}/libsigar-xxx.so
	 * after agent default extraction:    ${user.dir}/${LIB_DIR}/libsigar-xxx.so
	 * </pre>
	 */
	public static final String LIB_DIR = "native";

	static final int EOF = -1;
	static final int SIZE = 10 * 1024;

	/** Perform stream copy. */
	public static void transfer(final InputStream input,
			final OutputStream output) throws Exception {
		final byte[] data = new byte[SIZE];
		while (true) {
			final int count = input.read(data, 0, SIZE);
			if (count == EOF) {
				break;
			}
			output.write(data, 0, count);
		}
	}

	/** Default sigar library extract location. */
	public static String defaultLocation() {
		return System.getProperty("user.dir") + File.separator + LIB_DIR;
	}

	/** Extract and load native sigar library in the default folder. */
	public static void provision() throws Exception {
		provision(new File(defaultLocation()));
	}

	/** Extract and load native sigar library in the provided folder. */
	public static void provision(final File folder) throws Exception {

		if (!folder.exists()) {
			folder.mkdirs();
		}

		final SigarLoader sigarLoader = new SigarLoader(Sigar.class);

		/** Library name for given architecture. */
		final String libraryName = sigarLoader.getLibraryName();

		/** Library location embedded in the jar class path. */
		final String sourcePath = "/" + LIB_DIR + "/" + libraryName;

		/** Absolute path to the extracted library the on file system. */
		final File targetPath = new File(folder, libraryName).getAbsoluteFile();

		/** Extract library form the jar to the local file system. */
		final InputStream sourceStream = SigarAgent.class
				.getResourceAsStream(sourcePath);
		final OutputStream targetStream = new FileOutputStream(targetPath);
		transfer(sourceStream, targetStream);
		sourceStream.close();
		targetStream.close();

		/** Load library via absolute path. */
		final String libraryPath = targetPath.getAbsolutePath();
		System.load(libraryPath);

		/** Tell sigar loader that the library is already loaded. */
		System.setProperty("org.hyperic.sigar.path", "-");
		sigarLoader.load();

		logger.info("Sigar library provisioned: " + libraryPath);

	}

	/** Contract: starting agents after JVM startup. */
	public static void agentmain(final String options,
			final Instrumentation instrumentation) throws Exception {
		logger.info("Sigar loader via agent-main.");
		configure(options, instrumentation);
	}

	/** Contract: starting agents via command-line interface. */
	public static void premain(final String options,
			final Instrumentation instrumentation) throws Exception {
		logger.info("Sigar loader via pre-main.");
		configure(options, instrumentation);
	}

	/** Agent mode configuration. */
	public static synchronized void configure(final String options,
			final Instrumentation instrumentation) throws Exception {

		if (SigarAgent.instrumentation != null) {
			logger.severe("Duplicate agent setup attempt.");
			return;
		}

		SigarAgent.options = options;
		SigarAgent.instrumentation = instrumentation;

		logger.info("Sigar loader options: " + options);

		final String location;
		if (options == null) {
			logger.info("Sigar using default library extract location.");
			location = defaultLocation();
		} else {
			logger.info("Sigar using provided library extract location.");
			location = options;
		}

		final File folder = new File(location);

		provision(folder);

	}

	/** Agent command line options. */
	public static String options() {
		return options;
	}

	/** Injected JVM instrumentation instance. */
	public static Instrumentation instrumentation() {
		return instrumentation;
	}

}
