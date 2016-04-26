package edu.wisc.cs.sdn.simpledns;

import java.util.List;

/**
 * Created by aliHitawala on 4/25/16.
 */
public class SimpleDNSHandler {
    private String serverIp;
    private List<Amazon> amazonIpDetails;

    public SimpleDNSHandler(String serverIp, List<Amazon> amazonIpDetails) {
        this.serverIp = serverIp;
        this.amazonIpDetails = amazonIpDetails;
    }

    public void start() {

    }
}
