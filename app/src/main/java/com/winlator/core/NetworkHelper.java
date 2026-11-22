package com.winlator.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class NetworkHelper {
    private final ConnectivityManager connectivityManager;

    public static class IFAddress {
        public String name = "";
        public int flags = 0;
        public int family = OsConstants.AF_INET;
        public int scopeId = 0;
        public String address = "0";
        public String netmask = "0";

        @NonNull
        @Override
        public String toString() {
            return name+","+flags+","+family+","+scopeId+","+address+","+netmask;
        }
    }

    public NetworkHelper(Context context) {
        connectivityManager = context != null ?
            (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE) : null;
    }

    public String getIPv4Address() {
        if (!isConnected() || connectivityManager == null) return null;

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return null;
        LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
        if (linkProperties == null) return null;

        List<LinkAddress> linkAddresses = linkProperties.getLinkAddresses();
        for (LinkAddress linkAddress : linkAddresses) {
            InetAddress address = linkAddress.getAddress();
            if (address instanceof Inet4Address) return address.getHostAddress();
        }
        return null;
    }

    public List<IFAddress> getIFAddresses() {
        ArrayList<IFAddress> result = new ArrayList<>();
        if (connectivityManager == null) return result;
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return result;

        LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
        if (linkProperties == null) return result;

        List<LinkAddress> linkAddresses = linkProperties.getLinkAddresses();
        for (LinkAddress linkAddress : linkAddresses) {
            InetAddress address = linkAddress.getAddress();
            if (!(address instanceof Inet4Address || address instanceof Inet6Address)) continue;

            IFAddress ifAddress = new IFAddress();
            String interfaceName = linkProperties.getInterfaceName();
            if (interfaceName != null && !interfaceName.isEmpty()) ifAddress.name = interfaceName;
            if (address instanceof Inet6Address) {
                ifAddress.family = OsConstants.AF_INET6;
                ifAddress.scopeId = ((Inet6Address)address).getScopeId();
            }

            ifAddress.address = address.getHostAddress();
            ifAddress.netmask = formatNetmask(linkAddress.getPrefixLength(), address instanceof Inet6Address);
            ifAddress.flags =  OsConstants.IFF_UP | OsConstants.IFF_RUNNING;

            result.add(ifAddress);
        }
        return result;
    }

    public static String formatIpAddress(int ipAddress) {
        return (ipAddress & 255)+"."+((ipAddress >> 8) & 255)+"."+((ipAddress >> 16) & 255)+"."+((ipAddress >> 24) & 255);
    }

    public static String formatNetmask(int prefixLength) {
        return formatNetmask(prefixLength, false);
    }

    public static String formatNetmask(int prefixLength, boolean ipv6) {
        int bitCount = ipv6 ? 128 : 32;
        if (prefixLength < 0 || prefixLength > bitCount) return "";
        byte[] mask = new byte[bitCount / 8];
        for (int bit = 0; bit < prefixLength; bit++) {
            mask[bit / 8] |= (byte)(1 << (7 - (bit % 8)));
        }
        try {
            return InetAddress.getByAddress(mask).getHostAddress();
        }
        catch (java.net.UnknownHostException error) {
            return "";
        }
    }

    public boolean isConnected() {
        if (connectivityManager == null) return false;
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }
}
