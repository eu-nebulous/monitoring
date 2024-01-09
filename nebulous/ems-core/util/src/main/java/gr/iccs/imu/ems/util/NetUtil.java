/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Network Utility
 */
@Slf4j
public class NetUtil {

    private final static String[] ADDRESS_FILTERS;

    private final static String DATAGRAM_ADDRESS;

    private final static String[][] PUBLIC_ADDRESS_DISCOVERY_SERVICES;

    static {
        // Configure Address Filters
        String filtersStr = System.getenv("NET_UTIL_ADDRESS_FILTERS");
        List<String> filtersList = new ArrayList<>();
        if (StringUtils.isNotBlank(filtersStr)) {
            filtersList = Arrays.stream(filtersStr.split("[;, \t]+")).map(String::trim).filter(s->!s.isEmpty()).collect(Collectors.toList());
        } else {
            filtersList = Arrays.asList(
                    "127.",
                    /*"192.168.", "10.", "172.16.", "172.31.", "169.254.",*/
                    "224.", "239.", "255.255.255.255"
            );
        }
        ADDRESS_FILTERS = filtersList.toArray(new String[0]);

        // Configure Datagram address
        String datagramAddress = System.getenv("NET_UTIL_DATAGRAM_ADDRESS");
        DATAGRAM_ADDRESS = StringUtils.isNotBlank(datagramAddress) ? datagramAddress.trim() : "8.8.8.8";

        // Configure Address discovery services
        String servicesStr = System.getenv("NET_UTIL_ADDRESS_DISCOVERY_SERVICES");
        List<String[]> servicesList = new ArrayList<>();
        if (StringUtils.isNotBlank(servicesStr)) {
            if (!"-".equals(servicesStr)) {
                Arrays.stream(servicesStr.split("[;, \t]+"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.split("[:=]", 2))
                        .filter(a -> a.length == 2)
                        .peek(a->{ a[0]=a[0].trim(); a[1]=a[1].trim(); })
                        .filter(a->!a[0].isEmpty() && !a[1].isEmpty())
                        .forEach(servicesList::add);
            }
        } else {
            servicesList.add(Arrays.asList("AWS", "http://checkip.amazonaws.com").toArray(new String[0]));
            servicesList.add(Arrays.asList("Ipify", "https://api.ipify.org/?format=text").toArray(new String[0]));
            servicesList.add(Arrays.asList("WhatIsMyIpAddress", "http://bot.whatismyipaddress.com/").toArray(new String[0]));
        }
        PUBLIC_ADDRESS_DISCOVERY_SERVICES = servicesList.toArray(new String[0][]);
    }

    // ------------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        for (String arg : args) {
            if ("-nolog".equalsIgnoreCase(arg)) {
                loggingOff = true;
            } else
            if ("-log-all".equalsIgnoreCase(arg)) {
                logAll = true;
            } else
            if ("public".equalsIgnoreCase(arg)) {
                printAddress(getPublicIpAddress());
            } else
            if ("default".equalsIgnoreCase(arg)) {
                printAddress(getDefaultIpAddress());
            } else
            if ("addresses".equalsIgnoreCase(arg)) {
                for (InetAddress addr : getIpAddresses()) {
                    printAddress(addr.getHostAddress());
                }
            } else
            {
                for (String[] service : PUBLIC_ADDRESS_DISCOVERY_SERVICES) {
                    if (service[0].equalsIgnoreCase(arg)) {
                        printAddress(queryService(service[1]));
                    }
                }
            }
        }
    }

    protected static void printAddress(String addr) {
        if (logAll) log_info("{}", addr);
        else System.out.println(addr);
    }

    // ------------------------------------------------------------------------

    protected static boolean loggingOff = false;
    protected static boolean logAll = false;

    protected static void log_trace(String s, Object...o) { if (loggingOff) return; log.trace(s, o); }
    protected static void log_debug(String s, Object...o) { if (loggingOff) return; log.debug(s, o); }
    protected static void log_info(String s, Object...o) { if (loggingOff) return; log.info(s, o); }
    protected static void log_warn(String s, Object...o) { if (loggingOff) return; log.warn(s, o); }

    // ------------------------------------------------------------------------

    protected static boolean cacheAddresses = true;

    public static boolean isCacheAddresses() { return cacheAddresses; }
    public static void setCacheAddresses(boolean b) { cacheAddresses = b; }

    public static void clearCaches() {
        ipAddresses = null;
        publicIpAddress = null;
        defaultIpAddress = null;
    }

    // ------------------------------------------------------------------------

    private static List<InetAddress> ipAddresses = null;

    public static List<InetAddress> getIpAddresses() throws SocketException {
        if (cacheAddresses && ipAddresses!=null) {
            log_debug("NetUtil.getIpAddresses(): Returning cached IP addresses: {}", ipAddresses);
            return ipAddresses;
        }

        List<InetAddress> list = new ArrayList<>();
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface ni = en.nextElement();
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress inet = ia.getAddress();
                if (inet instanceof java.net.Inet4Address) {
                    String addr = inet.getHostAddress();
                    if (!inet.isLoopbackAddress() && !inet.isMulticastAddress() && inet.isSiteLocalAddress()) {
                        boolean ok = Arrays.stream(ADDRESS_FILTERS)
                                .noneMatch(addr::startsWith);
                        if (ok) {
                            log_debug("{}", addr);
                            list.add(inet);
                        }
                    }
                }
            }
        }
        if (cacheAddresses) ipAddresses = Collections.unmodifiableList(list);
        return list;
    }

    protected static InetAddress _getIpAddress() {
        try {
            List<InetAddress> list = getIpAddresses();
            if (list.size() == 0) {
                log_debug("NetUtil.getIpAddress(): Returning 'null' because getIpAddresses() returned an empty list");
                return null;
            }
            return list.get(0);
        } catch (SocketException se) {
            log_debug("NetUtil.getIpAddress(): Returning 'null' due to exception: ", se);
            return null;
        }
    }

    public static String getIpAddress() {
        return _getIpAddress().getHostAddress();
    }

    public static String getHostname() {
        return _getIpAddress().getHostName();
    }

    public static String getCanonicalHostName() {
        return _getIpAddress().getCanonicalHostName();
    }

    // ------------------------------------------------------------------------

    private static String publicIpAddress = null;

    public static String getPublicIpAddress() {
        if (cacheAddresses && publicIpAddress!=null) {
            log_debug("NetUtil.getPublicIpAddress(): Returning cached Public IP address: {}", publicIpAddress);
            return publicIpAddress;
        }

        for (String[] service : PUBLIC_ADDRESS_DISCOVERY_SERVICES) {
            log_debug("NetUtil.getPublicIpAddress(): Contacting service {}", service[0]);
            String ip = getIpAddressUsingService(service[1]);
            if (StringUtils.isNotBlank(ip)) {
                String addr = ip.trim();
                if (cacheAddresses) publicIpAddress = addr;
                log_debug("NetUtil.getPublicIpAddress(): Public IP address: {}", addr);
                return addr;
            }
        }
        if (cacheAddresses) publicIpAddress = "";

        log_warn("NetUtil.getPublicIpAddress(): No Public IP address or connectivity problems exist");
        return null;
    }

    private static String getIpAddressUsingService(String url) {
        try {
            log_debug("NetUtil.getIpAddressUsingService(): Service URL: {}", url);
            String response = queryService(url);
            log_debug("NetUtil.getIpAddressUsingService(): Service response: {}", response);
            if (StringUtils.isNotBlank(response)) {
                return response;
            }
        } catch (Exception ex) {
            log_warn("NetUtil.getIpAddressUsingService(): Contacting service FAILED: url={}, EXCEPTION={}", url, ex.toString());
            log_trace("NetUtil.getIpAddressUsingService(): Exception stack trace: ", ex);
        }

        log_debug("NetUtil.getIpAddressUsingService(): Response is null or blank");
        return null;
    }

    private static String queryService(String url) throws IOException {
        try (Scanner s = new Scanner(URI.create(url).toURL().openStream(), StandardCharsets.UTF_8).useDelimiter("\\A")) {
            return s.next().trim();
        }
    }

    // ------------------------------------------------------------------------

    private static String defaultIpAddress = null;

    public static String getDefaultIpAddress() {
        if (cacheAddresses && defaultIpAddress!=null) {
            log_debug("NetUtil.getDefaultIpAddress(): Returning cached Default IP address: {}", defaultIpAddress);
            return defaultIpAddress;
        }

        try {
            log_debug("NetUtil.getDefaultIpAddress(): Datagram address: {}", DATAGRAM_ADDRESS);
            String addr = getIpAddressWithDatagram(DATAGRAM_ADDRESS);
            if (cacheAddresses) defaultIpAddress = addr;
            log_debug("NetUtil.getDefaultIpAddress(): Response: {}", addr);
            if (StringUtils.isNotBlank(defaultIpAddress)) return addr;
        } catch (Exception ex) {
            log_warn("NetUtil.getDefaultIpAddress(): Datagram method failed: outgoing-ip-address={}, exception=", DATAGRAM_ADDRESS, ex);
            if (cacheAddresses) defaultIpAddress = "";
        }

        log_warn("NetUtil.getDefaultIpAddress(): Address is null or blank");
        return null;
    }

    public static String getIpAddressWithDatagram(String address) throws SocketException, UnknownHostException {
        try(final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(address), 10002);
            return socket.getLocalAddress().getHostAddress();
        }
    }

    // ------------------------------------------------------------------------

    public static boolean isLocalAddress(String addr) throws UnknownHostException {
        return isLocalAddress(InetAddress.getByName(addr));
    }

    // Source: https://stackoverflow.com/questions/2406341/how-to-check-if-an-ip-address-is-the-local-host-on-a-multi-homed-system
    public static boolean isLocalAddress(InetAddress addr) {
        // Check if the address is a valid special local or loop back
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return true;
        }

        // Check if the address is defined on any interface
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }
}
