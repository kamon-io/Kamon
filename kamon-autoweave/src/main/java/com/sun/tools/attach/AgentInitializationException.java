/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.tools.attach;

/**
 * The exception thrown when an agent fails to initialize in the target Java virtual machine.
 * <p/>
 * <p> This exception is thrown by {@link
 * VirtualMachine#loadAgent VirtualMachine.loadAgent},
 * {@link VirtualMachine#loadAgentLibrary
 * VirtualMachine.loadAgentLibrary}, {@link
 * VirtualMachine#loadAgentPath VirtualMachine.loadAgentPath}
 * methods if an agent, or agent library, cannot be initialized.
 * When thrown by <tt>VirtualMachine.loadAgentLibrary</tt>, or
 * <tt>VirtualMachine.loadAgentPath</tt> then the exception encapsulates
 * the error returned by the agent's <code>Agent_OnAttach</code> function.
 * This error code can be obtained by invoking the {@link #returnValue() returnValue} method.
 */
public final class AgentInitializationException extends Exception
{
   private static final long serialVersionUID = -1508756333332806353L;

   private final int returnValue;

   /**
    * Constructs an <code>AgentInitializationException</code> with
    * no detail message.
    */
   public AgentInitializationException()
   {
      returnValue = 0;
   }

   /**
    * Constructs an <code>AgentInitializationException</code> with the specified detail message.
    *
    * @param s the detail message.
    */
   public AgentInitializationException(String s)
   {
      super(s);
      returnValue = 0;
   }

   /**
    * Constructs an <code>AgentInitializationException</code> with
    * the specified detail message and the return value from the
    * execution of the agent's <code>Agent_OnAttach</code> function.
    *
    * @param s           the detail message.
    * @param returnValue the return value
    */
   public AgentInitializationException(String s, int returnValue)
   {
      super(s);
      this.returnValue = returnValue;
   }

   /**
    * If the exception was created with the return value from the agent
    * <code>Agent_OnAttach</code> function then this returns that value,
    * otherwise returns <code>0</code>. </p>
    */
   public int returnValue()
   {
      return returnValue;
   }
}
