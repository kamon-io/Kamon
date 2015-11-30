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
package com.sun.tools.attach.spi;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Attach provider class for attaching to a Java virtual machine.
 * <p/>
 * <p> An attach provider is a concrete subclass of this class that has a
 * zero-argument constructor and implements the abstract methods specified
 * below. </p>
 * <p/>
 * <p> An attach provider implementation is typically tied to a Java virtual
 * machine implementation, version, or even mode of operation. That is, a specific
 * provider implementation will typically only be capable of attaching to
 * a specific Java virtual machine implementation or version. For example, Sun's
 * JDK implementation ships with provider implementations that can only attach to
 * Sun's <i>HotSpot</i> virtual machine. In general, if an environment
 * consists of Java virtual machines of different versions and from different
 * vendors then there will be an attach provider implementation for each
 * <i>family</i> of implementations or versions. </p>
 * <p/>
 * <p> An attach provider is identified by its {@link #name <i>name</i>} and
 * {@link #type <i>type</i>}. The <i>name</i> is typically, but not required to
 * be, a name that corresponds to the VM vendor. The Sun JDK implementation,
 * for example, ships with attach providers that use the name <i>"sun"</i>. The
 * <i>type</i> typically corresponds to the attach mechanism. For example, an
 * implementation that uses the Doors inter-process communication mechanism
 * might use the type <i>"doors"</i>. The purpose of the name and type is to
 * identify providers in environments where there are multiple providers
 * installed. </p>
 * <p/>
 * <p> AttachProvider implementations are loaded and instantiated at the first
 * invocation of the {@link #providers() providers} method. This method
 * attempts to load all provider implementations that are installed on the
 * platform. </p>
 * <p/>
 * <p> All of the methods in this class are safe for use by multiple
 * concurrent threads. </p>
 *
 * @since 1.6
 */
public abstract class AttachProvider
{
   private static final Object lock = new Object();
   private static List<AttachProvider> providers;

   /**
    * Initializes a new instance of this class.
    */
   protected AttachProvider() {}

   /**
    * Return this provider's name.
    *
    * @return The name of this provider
    */
   public abstract String name();

   /**
    * Return this provider's type.
    *
    * @return The type of this provider
    */
   public abstract String type();

   /**
    * Attaches to a Java virtual machine.
    * <p/>
    * <p> A Java virtual machine is identified by an abstract identifier. The
    * nature of this identifier is platform dependent but in many cases it will be the
    * string representation of the process identifier (or pid). </p>
    * <p/>
    * <p> This method parses the identifier and maps the identifier to a Java
    * virtual machine (in an implementation dependent manner). If the identifier
    * cannot be parsed by the provider then an {@link
    * com.sun.tools.attach.AttachNotSupportedException AttachNotSupportedException}
    * is thrown. Once parsed this method attempts to attach to the Java virtual machine.
    * If the provider detects that the identifier corresponds to a Java virtual machine
    * that does not exist, or it corresponds to a Java virtual machine that does not support
    * the attach mechanism implemented by this provider, or it detects that the
    * Java virtual machine is a version to which this provider cannot attach, then
    * an <code>AttachNotSupportedException</code> is thrown. </p>
    *
    * @param id The abstract identifier that identifies the Java virtual machine.
    * @return VirtualMachine representing the target virtual machine.
    * @throws SecurityException           If a security manager has been installed and it denies
    *                                     {@link com.sun.tools.attach.AttachPermission AttachPermission}
    *                                     <tt>("attachVirtualMachine")</tt>, or other permission
    *                                     required by the implementation.
    * @throws AttachNotSupportedException If the identifier cannot be parsed, or it corresponds to
    *                                     to a Java virtual machine that does not exist, or it
    *                                     corresponds to a Java virtual machine which this
    *                                     provider cannot attach.
    * @throws IOException                 If some other I/O error occurs
    * @throws NullPointerException        If <code>id</code> is <code>null</code>
    */
   public abstract VirtualMachine attachVirtualMachine(String id)
      throws AttachNotSupportedException, IOException;

   /**
    * Attaches to a Java virtual machine.
    * <p/>
    * A Java virtual machine can be described using a {@link
    * com.sun.tools.attach.VirtualMachineDescriptor VirtualMachineDescriptor}.
    * This method invokes the descriptor's {@link
    * com.sun.tools.attach.VirtualMachineDescriptor#provider() provider()} method
    * to check that it is equal to this provider. It then attempts to attach to the
    * Java virtual machine.
    *
    * @param vmd The virtual machine descriptor
    * @return VirtualMachine representing the target virtual machine.
    * @throws SecurityException           If a security manager has been installed and it denies
    *                                     {@link com.sun.tools.attach.AttachPermission AttachPermission}
    *                                     <tt>("attachVirtualMachine")</tt>, or other permission
    *                                     required by the implementation.
    * @throws AttachNotSupportedException If the descriptor's {@link
    *                                     com.sun.tools.attach.VirtualMachineDescriptor#provider() provider()} method
    *                                     returns a provider that is not this provider, or it does not correspond
    *                                     to a Java virtual machine to which this provider can attach.
    * @throws IOException                 If some other I/O error occurs
    * @throws NullPointerException        If <code>vmd</code> is <code>null</code>
    */
   public VirtualMachine attachVirtualMachine(VirtualMachineDescriptor vmd)
      throws AttachNotSupportedException, IOException
   {
      if (vmd.provider() != this) {
         throw new AttachNotSupportedException("provider mismatch");
      }

      return attachVirtualMachine(vmd.id());
   }

   /**
    * Lists the Java virtual machines known to this provider.
    * <p/>
    * This method returns a list of {@link com.sun.tools.attach.VirtualMachineDescriptor} elements.
    * Each <code>VirtualMachineDescriptor</code> describes a Java virtual machine
    * to which this provider can <i>potentially</i> attach.  There isn't any
    * guarantee that invoking {@link #attachVirtualMachine(VirtualMachineDescriptor)
    * attachVirtualMachine} on each descriptor in the list will succeed.
    *
    * @return The list of virtual machine descriptors which describe the
    *         Java virtual machines known to this provider (may be empty).
    */
   public abstract List<VirtualMachineDescriptor> listVirtualMachines();

   /**
    * Returns a list of the installed attach providers.
    * <p/>
    * <p> An AttachProvider is installed on the platform if:
    * <p/>
    * <ul>
    * <li><p>It is installed in a JAR file that is visible to the defining
    * class loader of the AttachProvider type (usually, but not required
    * to be, the {@link java.lang.ClassLoader#getSystemClassLoader system
    * class loader}).</p></li>
    * <p/>
    * <li><p>The JAR file contains a provider configuration named
    * <tt>com.sun.tools.attach.spi.AttachProvider</tt> in the resource directory
    * <tt>META-INF/services</tt>. </p></li>
    * <p/>
    * <li><p>The provider configuration file lists the full-qualified class
    * name of the AttachProvider implementation. </p></li>
    * </ul>
    * <p/>
    * <p> The format of the provider configuration file is one fully-qualified
    * class name per line. Space and tab characters surrounding each class name,
    * as well as blank lines are ignored. The comment character is
    * <tt>'#'</tt> (<tt>0x23</tt>), and on each line all characters following
    * the first comment character are ignored. The file must be encoded in
    * UTF-8. </p>
    * <p/>
    * <p> AttachProvider implementations are loaded and instantiated
    * (using the zero-arg constructor) at the first invocation of this method.
    * The list returned by the first invocation of this method is the list
    * of providers. Subsequent invocations of this method return a list of the same
    * providers. The list is unmodifable.</p>
    *
    * @return A list of the installed attach providers.
    */
   @SuppressWarnings({"Since15"})
   public static List<AttachProvider> providers()
   {
      synchronized (lock) {
         if (providers == null) {
            providers = new ArrayList<AttachProvider>();

            ServiceLoader<AttachProvider> providerLoader =
               ServiceLoader.load(AttachProvider.class, AttachProvider.class.getClassLoader());

            for (AttachProvider aProviderLoader : providerLoader) {
               try {
                  providers.add(aProviderLoader);
               }
               catch (ThreadDeath td) {
                  throw td;
               }
               catch (Throwable t) {
                  // Ignore errors and exceptions.
                  System.err.println(t);
               }
            }
         }

         return Collections.unmodifiableList(providers);
      }
   }
}
