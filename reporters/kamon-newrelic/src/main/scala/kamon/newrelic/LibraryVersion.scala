/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

package kamon.newrelic

import java.util.Optional

object LibraryVersion {
  val version: String =
    Optional.ofNullable(classOf[LibraryVersion].getPackage.getImplementationVersion)
      .orElse("UnknownVersion");
}

private class LibraryVersion
