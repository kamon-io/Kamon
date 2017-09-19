/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.jdb.instrumentation;

import kamon.agent.libs.net.bytebuddy.asm.Advice;
import org.mariadb.jdbc.MariaDbServerPreparedStatement;

import java.sql.SQLException;

import static kamon.jdbc.instrumentation.MariaPreparedStatementServerInstrumentationMethods.executeInternal;

public class ExecuteUpdateMethodInterceptor {
    public static void executeUpdate(@Advice.This Object preparedStatement, @Advice.Return(readOnly = false) Object result) throws SQLException {
        MariaDbServerPreparedStatement pps = (MariaDbServerPreparedStatement) preparedStatement;
        executeInternal(pps);
        result = pps.getUpdateCount();
    }
}

