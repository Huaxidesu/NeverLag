/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.common;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.StringTokenizer;
import jiongjionger.common.IMonitorService;
import jiongjionger.common.MonitorInfoBean;

public class MonitorServiceImpl
implements IMonitorService {
    private static final int CPUTIME = 30;
    private static final int PERCENT = 100;
    private static final int FAULTLENGTH = 10;

    @Override
    public MonitorInfoBean getMonitorInfoBean() throws Exception {
        int kb = 1024;
        long totalMemory = Runtime.getRuntime().totalMemory() / (long)kb;
        long freeMemory = Runtime.getRuntime().freeMemory() / (long)kb;
        long maxMemory = Runtime.getRuntime().maxMemory() / (long)kb;
        OperatingSystemMXBean osmxb = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        String osName = System.getProperty("os.name");
        long totalMemorySize = osmxb.getTotalPhysicalMemorySize() / (long)kb;
        long freePhysicalMemorySize = osmxb.getFreePhysicalMemorySize() / (long)kb;
        long usedMemory = (osmxb.getTotalPhysicalMemorySize() - osmxb.getFreePhysicalMemorySize()) / (long)kb;
        ThreadGroup parentThread = Thread.currentThread().getThreadGroup();
        while (parentThread.getParent() != null) {
            parentThread = parentThread.getParent();
        }
        int totalThread = parentThread.activeCount();
        double cpuRatio = 0.0;
        cpuRatio = osName.toLowerCase().startsWith("windows") ? this.getCpuRatioForWindows() : (osName.toLowerCase().startsWith("mac") ? 0.0 : MonitorServiceImpl.getCpuRateForLinux());
        MonitorInfoBean infoBean = new MonitorInfoBean();
        infoBean.setFreeMemory(String.valueOf(freeMemory / 1024L) + "MB");
        infoBean.setFreePhysicalMemorySize(String.valueOf(freePhysicalMemorySize / 1024L) + "MB");
        infoBean.setMaxMemory(String.valueOf(maxMemory / 1024L) + "MB");
        infoBean.setOsName(osName);
        infoBean.setTotalMemory(String.valueOf(totalMemory / 1024L) + "MB");
        infoBean.setTotalMemorySize(String.valueOf(totalMemorySize / 1024L) + "MB");
        infoBean.setTotalThread(totalThread);
        infoBean.setUsedMemory(String.valueOf(usedMemory / 1024L) + "MB");
        infoBean.setCpuRatio(String.valueOf(cpuRatio) + "%");
        return infoBean;
    }

    @Override
    public int getPid() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        String name = runtime.getName();
        try {
            return Integer.parseInt(name.substring(0, name.indexOf(64)));
        }
        catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String getPidHostName() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return runtime.getName();
    }

    @Override
    public long getUsedMemoryMB() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 0x100000L;
    }

    @Override
    public long getFreeMemoryMB() {
        return Runtime.getRuntime().freeMemory() / 0x100000L;
    }

    private static double getCpuRateForLinux() {
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = new BufferedReader(isr);
        StringTokenizer tokenStat = null;
        try {
            br.readLine();
            br.readLine();
            String line = br.readLine();
            tokenStat = new StringTokenizer(line, ",");
            String nextToken = tokenStat.nextToken();
            while (!nextToken.contains("id") && tokenStat.hasMoreTokens()) {
                nextToken = tokenStat.nextToken();
            }
            int index = nextToken.indexOf("%");
            if (index != -1) {
                nextToken = nextToken.substring(0, index);
            } else {
                index = nextToken.indexOf("id");
                if (index != -1) {
                    nextToken = nextToken.substring(0, index);
                }
            }
            Float usage = Float.valueOf(Float.parseFloat(nextToken.trim()));
            double d = 100.0f - usage.floatValue();
            return d;
        }
        catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            MonitorServiceImpl.freeResource(is, isr, br);
            return 1.0;
        }
        finally {
            MonitorServiceImpl.freeResource(is, isr, br);
        }
    }

    private static void freeResource(InputStream is, InputStreamReader isr, BufferedReader br) {
        try {
            if (is != null) {
                is.close();
            }
            if (isr != null) {
                isr.close();
            }
            if (br != null) {
                br.close();
            }
        }
        catch (IOException ioe) {
            System.out.println(ioe.getMessage());
        }
    }

    private double getCpuRatioForWindows() {
        try {
            String procCmd = String.valueOf(System.getenv("windir")) + "\\system32\\wbem\\wmic.exe process get Caption,CommandLine,KernelModeTime,ReadOperationCount,ThreadCount,UserModeTime,WriteOperationCount";
            long[] c0 = this.readCpu(Runtime.getRuntime().exec(procCmd));
            Thread.sleep(30L);
            long[] c1 = this.readCpu(Runtime.getRuntime().exec(procCmd));
            if (c0 != null && c1 != null) {
                long idletime = c1[0] - c0[0];
                long busytime = c1[1] - c0[1];
                return 100L * busytime / (busytime + idletime);
            }
            return 0.0;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return 0.0;
        }
    }

    private long[] readCpu(Process proc) {
        long[] retn = new long[2];
        try {
            proc.getOutputStream().close();
            InputStreamReader ir = new InputStreamReader(proc.getInputStream());
            LineNumberReader input = new LineNumberReader(ir);
            String line = input.readLine();
            if (line == null || line.length() < 10) {
                return null;
            }
            int capidx = line.indexOf("Caption");
            int cmdidx = line.indexOf("CommandLine");
            int rocidx = line.indexOf("ReadOperationCount");
            int umtidx = line.indexOf("UserModeTime");
            int kmtidx = line.indexOf("KernelModeTime");
            int wocidx = line.indexOf("WriteOperationCount");
            long idletime = 0L;
            long kneltime = 0L;
            long usertime = 0L;
            while ((line = input.readLine()) != null) {
                if (line.length() < wocidx) continue;
                String caption = line.substring(capidx, cmdidx - 1).trim();
                String cmd = line.substring(cmdidx, kmtidx - 1).trim();
                if (cmd.indexOf("wmic.exe") >= 0) continue;
                String s1 = line.substring(kmtidx, rocidx - 1).trim();
                String s2 = line.substring(umtidx, wocidx - 1).trim();
                if (caption.equals("System Idle Process") || caption.equals("System")) {
                    if (s1.length() > 0) {
                        idletime += Long.valueOf(s1).longValue();
                    }
                    if (s2.length() <= 0) continue;
                    idletime += Long.valueOf(s2).longValue();
                    continue;
                }
                if (s1.length() > 0) {
                    kneltime += Long.valueOf(s1).longValue();
                }
                if (s2.length() <= 0) continue;
                usertime += Long.valueOf(s2).longValue();
            }
            retn[0] = idletime;
            retn[1] = kneltime + usertime;
            long[] lArray = retn;
            return lArray;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            try {
                proc.getInputStream().close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}

