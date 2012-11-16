package com.intel.mtwilson.datatypes;

import java.net.URI;
import java.net.URISyntaxException;
import org.codehaus.jackson.annotate.JsonValue;

/**
 * Representation of a hostname. This class enforces some rules on the 
 * syntax of the hostname to make it usable without further type checking.
 * 
 * @since 0.5.1
 * @author jbuhacoff
 */
public class Hostname {

    private String hostname = null;

    /*
    private Hostname() {
    }
    *
    */

    public Hostname(String hostname) {
        setHostname(hostname);
    }


    public final void setHostname(String hostname) {
        if( hostname == null ) { throw new IllegalArgumentException("Missing hostname"); } // or NullPointerException
        if( hostname.isEmpty() ) { throw new IllegalArgumentException("Hostname is empty"); } // or IllegalArgumentException
        if (isValid(hostname)) {
            this.hostname = hostname;
        } else {
            throw new IllegalArgumentException("Invalid hostname: " + hostname);
        }
    }

    /**
     * Returns the hostname so that you can easily concatenate to a string.
     * Example: assert new Hostname("1.2.3.4").toString().equals("1.2.3.4");
     *
     * @see java.lang.Object#toString()
     */
    @JsonValue
    @Override
    public String toString() {
        return hostname;
    }

    // should deprecate? or still allow it?
    /*
    public static Hostname parse(String input) {
        if (isValid(input)) {
            Hostname h = new Hostname();
            h.hostname = input;
            return h; // new Hostname(input);
        }
        throw new IllegalArgumentException("invalid hostname: " + input);
    }
    * 
    */

    /**
     * This method does NOT check the network for the existence of the given
     * hostname, it only checks its format for validity and, if an IPv4 or IPv6
     * hostname is given, checks that it is within the allowed range.
     *
     * @param hostname to check for validity, such as 1.2.3.4
     * @return true if the hostname appears to be a valid IPv4 or IPv6 address,
     * false if the hostname is null or otherwise invalid
     */
    public static boolean isValid(String hostname) {
        // right now valid hostname can be any string that does not contain a comma
        return ( !hostname.contains(",") );
        /*
        try {
            if (hostname.contains(":")) {
                // IPv6 format
                URI valid = new URI(String.format("//[%s]", hostname));
                return valid.getHost() != null;
            } else {
                // IPv4 format
                URI valid = new URI(String.format("//%s", hostname));
                return valid.getHost() != null;
            }
        } catch (NullPointerException e) {
            return false; // happens when hostname is null or invalid format like 1b.2.3i.4
        } catch (URISyntaxException e) {
            return false;
        }
        */
    }
    
    @Override
    public int hashCode() {
        return hostname.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Hostname other = (Hostname) obj;
        if ((this.hostname == null) ? (other.hostname != null) : !this.hostname.equals(other.hostname)) {
            return false;
        }
        return true;
    }
}
