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

import java.util.logging.Logger;

import org.hyperic.sigar.Sigar;

/** Verify sigar loader operation when invoked as agent. */
public class SigarAgentMain {

	private static final Logger logger = Logger.getLogger(SigarAgentMain.class
			.getName());

	public static void main(final String... args) throws Exception {

		/** Sigar must be availbale now. */
		final Sigar sigar = new Sigar();

		logger.info("Process id: " + sigar.getPid());
		logger.info("Host FQDN: " + sigar.getFQDN());
		logger.info("CPU info: " + sigar.getCpu());
		logger.info("MEM info: " + sigar.getMem());
		logger.info("CPU load: " + sigar.getLoadAverage()[0]);

	}

}
