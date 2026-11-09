/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.common;

import jiongjionger.common.MonitorInfoBean;

public interface IMonitorService {
    public MonitorInfoBean getMonitorInfoBean() throws Exception;

    public int getPid();

    public String getPidHostName();

    public long getUsedMemoryMB();

    public long getFreeMemoryMB();
}

