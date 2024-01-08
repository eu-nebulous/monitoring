/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.control.info;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemInfoProvider implements IEmsInfoProvider {

    private final File root = new File("/");

    @Override
    public Map<String, Object> getMetricValues() {
        Map<String,Object> sysInfo = new LinkedHashMap<>();
        sysInfo.put("jvm-memory-total", Runtime.getRuntime().totalMemory());
        sysInfo.put("jvm-memory-max", Runtime.getRuntime().freeMemory());
        sysInfo.put("jvm-memory-free", Runtime.getRuntime().maxMemory());

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean() ;
        String heapInfo = memBean.getHeapMemoryUsage().toString();
        String nonHeapInfo = memBean.getNonHeapMemoryUsage().toString();
        sysInfo.put("jvm-memory-heap", heapInfo);
        sysInfo.put("jvm-memory-non-heap", nonHeapInfo);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        sysInfo.put("jvm-thread-count", threadBean.getThreadCount());
        sysInfo.put("jvm-thread-daemon-count", threadBean.getDaemonThreadCount());
        sysInfo.put("jvm-thread-peak-count", threadBean.getPeakThreadCount());
        sysInfo.put("jvm-thread-total-started-count", threadBean.getTotalStartedThreadCount());

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long uptime = runtimeBean.getUptime() / 1000;
        String vmInfo = "%s, ver.%s, by %s".formatted(runtimeBean.getVmName(), runtimeBean.getVmVersion(), runtimeBean.getVmVendor());
        sysInfo.put("jvm-info", vmInfo);
        sysInfo.put("jvm-uptime", uptime);

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        String osInfo = "OS %s, %s, v.%s, processors: %d, avg. load: %.02f".formatted(osBean.getName(), osBean.getArch(), osBean.getVersion(),
                osBean.getAvailableProcessors(), osBean.getSystemLoadAverage());
        sysInfo.put("os-info", osInfo);

        sysInfo.put("os-disk-total", root.getTotalSpace());
        sysInfo.put("os-disk-free", root.getFreeSpace());
        sysInfo.put("os-disk-usable", root.getUsableSpace());

        return sysInfo;
    }
}
