package com.grun.calorietracker.service.media;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicImageDnsResolverTest {
    @Test void rejectsPrivateMetadataReservedAndTransitionAddresses() throws Exception {
        for (String ip : new String[]{"0.0.0.0", "127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.64.0.1", "198.18.0.1", "192.0.2.1", "198.51.100.1", "203.0.113.1",
                "224.0.0.1", "240.0.0.1", "::1", "fc00::1", "fd00:ec2::254", "fe80::1", "ff02::1",
                "64:ff9b::a00:1", "2002:a00:1::1", "2001::1", "2001:db8::1", "::ffff:127.0.0.1"}) {
            assertThat(PublicImageDnsResolver.isPublic(InetAddress.getByName(ip))).as(ip).isFalse();
        }
    }

    @Test void allowsPublicIpv4AndNativeIpv6() throws Exception {
        for (String ip : new String[]{"8.8.8.8", "1.1.1.1", "2001:4860:4860::8888", "2606:4700:4700::1111"}) {
            assertThat(PublicImageDnsResolver.isPublic(InetAddress.getByName(ip))).as(ip).isTrue();
        }
    }

    @Test void mixedDnsAnswerFailsClosed() throws Exception {
        DnsResolver dns = mock(DnsResolver.class);
        when(dns.resolve("mixed.invalid")).thenReturn(new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1")});
        assertThatThrownBy(() -> new PublicImageDnsResolver(dns).resolve("mixed.invalid")).isInstanceOf(UnknownHostException.class);
    }

    @Test void connectionTimeRebindingIsRejectedBeforeConnecting() throws Exception {
        DnsResolver dns = mock(DnsResolver.class);
        when(dns.resolve("rebinding.invalid")).thenReturn(new InetAddress[]{InetAddress.getByName("8.8.8.8")},
                new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        var guarded = new PublicImageDnsResolver(dns);
        guarded.resolve("rebinding.invalid");
        var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(guarded).build();
        try (var client = HttpClients.custom().setConnectionManager(manager).disableAutomaticRetries().build()) {
            assertThatThrownBy(() -> client.execute(new HttpGet("https://rebinding.invalid/image.png")))
                    .isInstanceOf(UnknownHostException.class);
        }
        verify(dns, times(2)).resolve("rebinding.invalid");
    }
}
