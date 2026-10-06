package com.grun.calorietracker.service.media;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Validates the exact address set used by the HTTP connection, not a separate DNS lookup. */
public final class PublicImageDnsResolver implements DnsResolver {
    private final DnsResolver delegate;

    public PublicImageDnsResolver(DnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = delegate.resolve(host);
        if (addresses == null || addresses.length == 0) throw new UnknownHostException("Image host has no public address");
        for (InetAddress address : addresses) {
            if (!isPublic(address)) throw new UnknownHostException("Image host is not public");
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }

    static boolean isPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 255;
        int second = bytes[1] & 255;
        if (bytes.length == 4) {
            int third = bytes[2] & 255;
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 192 && second == 0 && (third == 0 || third == 2))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113);
        }
        // Only native global unicast IPv6; reject ULA, translation and transition tunnels.
        return bytes.length == 16 && (first & 224) == 32
                && !(first == 32 && second == 2)
                && !(first == 32 && second == 1 && bytes[2] == 0 && bytes[3] == 0)
                && !(first == 32 && second == 1 && (bytes[2] & 255) == 13 && (bytes[3] & 255) == 184);
    }
}
