package com.twitter.finagle.http.filter

import com.twitter.finagle.Stack

// access workaround for Finagle internals
object Export {
  val httpTracingFilterRole: Stack.Role = HttpTracingFilter.Role
}
