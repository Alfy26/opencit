package com.intel.mtwilson.as.datatype;

import java.net.URI;
import java.net.URISyntaxException;
import org.codehaus.jackson.annotate.JsonValue;

/**
 * Representation of an IP Address in either IPv4 or IPv6 format.
 * 
 * Internally it uses the java.net.URI class to validate IPv4 and IPv6
 * addresses but this may change in the future.
 * 
 * @since 0.5.1
 * @author jbuhacoff
 */
public class IPAddress {

    private String address = null;

    public IPAddress() {
    }

    public IPAddress(String address) {
        setAddress(address);
    }


    public String getAddress() {
        return address;
    }

    public final void setAddress(String address) {
        if (isValid(address)) {
            this.address = address;
        } else {
            throw new IllegalArgumentException("Invalid IP address: " + address);
        }
    }

    /**
     * Returns the address so that you can easily concatenate to a string.
     * Example: assert new IPAddress("1.2.3.4").toString().equals("1.2.3.4");
     *
     * @see java.lang.Object#toString()
     */
    @JsonValue
    @Override
    public String toString() {
        return address;
    }

    // should deprecate? or still allow it? don't want to return new
    // Hostname(...) because then it's checked twice
    public static IPAddress parse(String input) {
        if (isValid(input)) {
            IPAddress h = new IPAddress();
            h.address = input;
            return h; // new Hostname(input);
        }
        throw new IllegalArgumentException("Invalid IP address: " + input);
    }

    /**
     * This method does NOT check the network for the existence of the given
     * address, it only checks its format for validity and, if an IPv4 or IPv6
     * address is given, checks that it is within the allowed range.
     *
     * @param address to check for validity, such as 1.2.3.4
     * @return true if the address appears to be a valid IPv4 or IPv6 address,
     * false if the address is null or otherwise invalid
     */
    public static boolean isValid(String address) {
        try {
            /*
             * because URI format for host is hostname ; but problem is that
             * ipv4 is valid and [ipv6] is valid but [ipv4] is not valid and
             * ipv6 is not valid so we need to know in advance which it is or it
             * won't validate properly.. .which defeats the purpose of this
             * check... so we look for ":" to distinguish ipv4 from ipv6
             * 
             * When IP address is invalid such as "1b.2.3i.4" there will be an exception thrown
             */
            if( address.contains(":") ) {
                // IPv6 format
                URI valid = new URI(String.format("//[%s]", address));
                return valid.getHost() != null;
            }
            else {
                // IPv4 format
                URI valid = new URI(String.format("//%s", address));
                // also make sure that there are only digits and dots
                // because URI also accepts valid hostnames, which are not addresses
                return valid.getHost() != null && address.matches("[\\d\\.]+");
            }
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
