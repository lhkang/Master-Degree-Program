package net.floodlightcontroller.multipathrouting.type;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.util.HexString;

import net.floodlightcontroller.core.types.NodePortTuple;

public class FlowId implements Cloneable, Comparable<FlowId> {
	protected DatapathId src;
    protected DatapathId dst;
    protected OFPort srcPort;
    protected OFPort dstPort;
	
    public FlowId(DatapathId src, OFPort srcPort,DatapathId dst,OFPort dstPort) {
        super();
        this.src = src;
        this.dst = dst;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }
    
    public DatapathId getSrc() {
        return src;
    }

    public void setSrc(DatapathId src) {
        this.src = src;
    }

    public DatapathId getDst() {
        return dst;
    }

    public void setDst(DatapathId dst) {
        this.dst = dst;
    }

    public OFPort getSrcPort(){
        return srcPort;
    }
    public void setSrcPort(OFPort port){
        this.srcPort = port;
    }
    public OFPort getDstPort(){
        return dstPort;
    }
    public void setDstPort(OFPort port){
        this.dstPort = port;
    }   
	
	public int hashCode() {
		final int prime = 2417;
        Long result = new Long(1);
        result = prime * result + ((dst == null) ? 0 : dst.hashCode());
        result = prime * result + ((src == null) ? 0 : src.hashCode());
        result = prime * result + srcPort.getShortPortNumber();
        result = prime * result + dstPort.getShortPortNumber();
        return result.hashCode(); 
	}
	
	public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FlowId other = (FlowId) obj;
        if (dst == null) {
            if (other.dst != null)
                return false;
        } else if (!dst.equals(other.dst))
            return false;
        if (src == null) {
            if (other.src != null)
                return false;
        } else if (!src.equals(other.src))
            return false;
        if (srcPort != other.srcPort)
            return false;
        if (dstPort != other.dstPort)
            return false;
        return true;
    }
	
	protected Object clone() throws CloneNotSupportedException {
        return super.clone();
	}
	
	public int compareTo(FlowId o) {
        int result = src.compareTo(o.getSrc());
        if (result != 0)
            return result;
        result = dst.compareTo(o.getDst());
        if (result != 0)
            return result;
        if ( srcPort == o.getSrcPort())
            return dstPort.getShortPortNumber() - o.getDstPort().getShortPortNumber();
        return srcPort.getShortPortNumber() - o.getSrcPort().getShortPortNumber();
	}
	
}
