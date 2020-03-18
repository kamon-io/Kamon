package com.zaxxer.hikari.pool

import java.sql.Connection

object PoolEntryProtectedAccess {

  /**
    * Returns the actual connection behind a pool entry
    */
  def underlyingConnection(poolEntry: Any): Connection =
    poolEntry match {
      case pe: PoolEntry => pe.connection
      case _             => null
    }
}
