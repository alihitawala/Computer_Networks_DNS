package edu.wisc.cs.sdn.simpledns;

/**
 * Created by aliHitawala on 4/25/16.
 */
public class Amazon {
    private String ip;
    private String mask;
    private String region;

    public Amazon(String ip, String mask, String region) {
        this.ip = ip;
        this.mask = mask;
        this.region = region;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
