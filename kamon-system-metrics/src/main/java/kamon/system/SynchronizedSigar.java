/*
 * =========================================================================================
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

package kamon.system;

import org.hyperic.sigar.*;

import java.util.List;
import java.util.Map;

/**
 * Safely share a single Sigar instance between multiple threads.
 * The Sigar object is thread-safe given that each thread has its
 * own Sigar instance.  In order to share a single Sigar instance
 * the methods implemented here are synchronized.  All data returned
 * by the Sigar methods is read-only and hence thread-safe.
 */
public class SynchronizedSigar implements SigarProxy {

    private Sigar sigar;

    public SynchronizedSigar() {
        this.sigar = new Sigar();
    }

    public synchronized long getPid()
    {
        return this.sigar.getPid();
    }

    public synchronized long getServicePid(String name) throws SigarException {
        return this.sigar.getServicePid(name);
    }

    public synchronized Mem getMem()
            throws SigarException
    {
        return this.sigar.getMem();
    }

    public synchronized Swap getSwap()
            throws SigarException
    {
        return this.sigar.getSwap();
    }

    public synchronized Cpu getCpu()
            throws SigarException
    {
        return this.sigar.getCpu();
    }

    public synchronized CpuPerc getCpuPerc()
            throws SigarException
    {
        return this.sigar.getCpuPerc();
    }

    public synchronized Uptime getUptime()
            throws SigarException
    {
        return this.sigar.getUptime();
    }

    
    public ResourceLimit getResourceLimit() throws SigarException {
        return null;
    }

    public synchronized double[] getLoadAverage()
            throws SigarException
    {
        return this.sigar.getLoadAverage();
    }

    public synchronized long[] getProcList()
            throws SigarException
    {
        return this.sigar.getProcList();
    }

    public synchronized ProcStat getProcStat()
            throws SigarException
    {
        return this.sigar.getProcStat();
    }

    public synchronized ProcMem getProcMem(long pid)
            throws SigarException
    {
        return this.sigar.getProcMem(pid);
    }

    public synchronized ProcMem getProcMem(String pid)
            throws SigarException
    {
        return this.sigar.getProcMem(pid);
    }

    
    public ProcMem getMultiProcMem(String s) throws SigarException {
        return null;
    }

    public synchronized ProcState getProcState(long pid)
            throws SigarException
    {
        return this.sigar.getProcState(pid);
    }

    public synchronized ProcState getProcState(String pid)
            throws SigarException
    {
        return this.sigar.getProcState(pid);
    }

    public synchronized ProcTime getProcTime(long pid)
            throws SigarException
    {
        return this.sigar.getProcTime(pid);
    }

    public synchronized ProcTime getProcTime(String pid)
            throws SigarException
    {
        return this.sigar.getProcTime(pid);
    }

    public synchronized ProcCpu getProcCpu(long pid)
            throws SigarException
    {
        return this.sigar.getProcCpu(pid);
    }

    public synchronized ProcCpu getProcCpu(String pid)
            throws SigarException
    {
        return this.sigar.getProcCpu(pid);
    }

    
    public MultiProcCpu getMultiProcCpu(String s) throws SigarException {
        return null;
    }

    public synchronized ProcCred getProcCred(long pid)
            throws SigarException
    {
        return this.sigar.getProcCred(pid);
    }

    public synchronized ProcCred getProcCred(String pid)
            throws SigarException
    {
        return this.sigar.getProcCred(pid);
    }

    public synchronized ProcCredName getProcCredName(long pid)
            throws SigarException
    {
        return this.sigar.getProcCredName(pid);
    }

    public synchronized ProcCredName getProcCredName(String pid)
            throws SigarException
    {
        return this.sigar.getProcCredName(pid);
    }

    public synchronized ProcFd getProcFd(long pid)
            throws SigarException
    {
        return this.sigar.getProcFd(pid);
    }

    public synchronized ProcFd getProcFd(String pid)
            throws SigarException
    {
        return this.sigar.getProcFd(pid);
    }

    public synchronized ProcExe getProcExe(long pid)
            throws SigarException
    {
        return this.sigar.getProcExe(pid);
    }

    public synchronized ProcExe getProcExe(String pid)
            throws SigarException
    {
        return this.sigar.getProcExe(pid);
    }

    public synchronized String[] getProcArgs(long pid)
            throws SigarException
    {
        return this.sigar.getProcArgs(pid);
    }

    public synchronized String[] getProcArgs(String pid)
            throws SigarException
    {
        return this.sigar.getProcArgs(pid);
    }

    public synchronized Map getProcEnv(long pid)
            throws SigarException
    {
        return this.sigar.getProcEnv(pid);
    }

    public synchronized Map getProcEnv(String pid)
            throws SigarException
    {
        return this.sigar.getProcEnv(pid);
    }

    public synchronized String getProcEnv(long pid, String key)
            throws SigarException
    {
        return this.sigar.getProcEnv(pid, key);
    }

    public synchronized String getProcEnv(String pid, String key)
            throws SigarException
    {
        return this.sigar.getProcEnv(pid, key);
    }

    public synchronized List getProcModules(long pid)
            throws SigarException
    {
        return this.sigar.getProcModules(pid);
    }

    public synchronized List getProcModules(String pid)
            throws SigarException
    {
        return this.sigar.getProcModules(pid);
    }

    
    public long getProcPort(int i, long l) throws SigarException {
        return 0;
    }

    
    public long getProcPort(String s, String s2) throws SigarException {
        return 0;
    }

    public synchronized FileSystem[] getFileSystemList()
            throws SigarException
    {
        return this.sigar.getFileSystemList();
    }

    public synchronized FileSystemMap getFileSystemMap()
            throws SigarException
    {
        return this.sigar.getFileSystemMap();
    }

    public synchronized FileSystemUsage getMountedFileSystemUsage(String name)
            throws SigarException
    {
        return this.sigar.getMountedFileSystemUsage(name);
    }

    public synchronized FileSystemUsage getFileSystemUsage(String name)
            throws SigarException
    {
        return this.sigar.getFileSystemUsage(name);
    }

    
    public DiskUsage getDiskUsage(String s) throws SigarException {
        return null;
    }

    public synchronized FileInfo getFileInfo(String name)
            throws SigarException
    {
        return this.sigar.getFileInfo(name);
    }

    public synchronized FileInfo getLinkInfo(String name)
            throws SigarException
    {
        return this.sigar.getLinkInfo(name);
    }

    public synchronized DirStat getDirStat(String name)
            throws SigarException
    {
        return this.sigar.getDirStat(name);
    }

    
    public DirUsage getDirUsage(String s) throws SigarException {
        return null;
    }

    public synchronized CpuInfo[] getCpuInfoList()
            throws SigarException
    {
        return this.sigar.getCpuInfoList();
    }

    public synchronized Cpu[] getCpuList()
            throws SigarException
    {
        return this.sigar.getCpuList();
    }

    public synchronized CpuPerc[] getCpuPercList()
            throws SigarException
    {
        return this.sigar.getCpuPercList();
    }

    public synchronized NetRoute[] getNetRouteList()
            throws SigarException
    {
        return this.sigar.getNetRouteList();
    }

    public synchronized NetInterfaceConfig getNetInterfaceConfig(String name)
            throws SigarException
    {
        return this.sigar.getNetInterfaceConfig(name);
    }

    
    public synchronized NetInterfaceConfig getNetInterfaceConfig() throws SigarException {
        return null;
    }

    public synchronized NetInterfaceStat getNetInterfaceStat(String name)
            throws SigarException
    {
        return this.sigar.getNetInterfaceStat(name);
    }

    public synchronized String[] getNetInterfaceList()
            throws SigarException
    {
        return this.sigar.getNetInterfaceList();
    }

    
    public synchronized NetConnection[] getNetConnectionList(int i) throws SigarException {
        return this.sigar.getNetConnectionList(i);
    }

    
    public synchronized String getNetListenAddress(long l) throws SigarException {
        return this.sigar.getNetListenAddress(l);
    }

    
    public synchronized String getNetListenAddress(String s) throws SigarException {
        return this.sigar.getNetListenAddress(s);
    }

    
    public synchronized NetStat getNetStat() throws SigarException {
        return this.sigar.getNetStat();
    }

    
    public synchronized String getNetServicesName(int i, long l) {
        return this.sigar.getNetServicesName(i,l);
    }

    
    public synchronized Who[] getWhoList() throws SigarException {
        return this.sigar.getWhoList();
    }

    
    public synchronized Tcp getTcp() throws SigarException {
        return this.sigar.getTcp();
    }

    
    public synchronized NfsClientV2 getNfsClientV2() throws SigarException {
        return this.sigar.getNfsClientV2();
    }

    
    public synchronized NfsServerV2 getNfsServerV2() throws SigarException {
        return this.sigar.getNfsServerV2();
    }

    
    public synchronized NfsClientV3 getNfsClientV3() throws SigarException {
        return this.sigar.getNfsClientV3();
    }

    
    public synchronized NfsServerV3 getNfsServerV3() throws SigarException {
        return this.sigar.getNfsServerV3();
    }

    
    public synchronized NetInfo getNetInfo() throws SigarException {
        return this.sigar.getNetInfo();
    }

    public synchronized String getFQDN()
            throws SigarException
    {
        return this.sigar.getFQDN();
    }
}