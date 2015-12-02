/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.autoweave;

import com.typesafe.config.Config;
import kamon.Kamon;
import kamon.autoweave.loader.AgentLoader;
import org.aspectj.weaver.loadtime.Agent;

public class Autoweave {

    static {

        final Config config = Kamon.config().getConfig("kamon.autowave.options");
        final Boolean verbose = config.getBoolean("verbose");
        final Boolean showWeaveInfo = config.getBoolean("showWeaveInfo");

        System.setProperty("aj.weaving.verbose", verbose.toString());
        System.setProperty("org.aspectj.weaver.showWeaveInfo", showWeaveInfo.toString());

        AgentLoader.attachAgentToJVM(Agent.class);
   }
}
