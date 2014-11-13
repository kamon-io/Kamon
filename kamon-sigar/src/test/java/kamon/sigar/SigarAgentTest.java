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

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.hyperic.sigar.Sigar;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verify sigar loader operation. */
public class SigarAgentTest {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Test
	public void provision() throws Exception {

		final File extractLocation = new File("target/native");
		SigarAgent.provision(extractLocation);
		final Sigar sigar = new Sigar();
		assertTrue(sigar.getPid() > 0);

		logger.info("Process id: {}", sigar.getPid());
		logger.info("Host FQDN: {}", sigar.getFQDN());
		logger.info("CPU info: {}", sigar.getCpu());
		logger.info("MEM info: {}", sigar.getMem());
		logger.info("CPU load: {}", sigar.getLoadAverage()[0]);

	}

}
