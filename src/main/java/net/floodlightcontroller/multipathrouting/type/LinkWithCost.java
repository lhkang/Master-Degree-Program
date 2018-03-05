package net.floodlightcontroller.multipathrouting.type;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.test.FlowCost;

public class LinkWithCost implements Comparable<LinkWithCost> {
    protected DatapathId src;
    protected OFPort srcPort;
    protected DatapathId dst;
    protected OFPort dstPort;
    protected int cost;
    protected int count = 0;
    
    public LinkWithCost(DatapathId srcDpid,OFPort srcPort,DatapathId dstDpid,OFPort dstPort,int cost){
        this.src = srcDpid;
        this.srcPort = srcPort;
        this.dst = dstDpid;
        this.dstPort = dstPort;
        this.cost = cost;
    }
    
    public DatapathId getSrcDpid(){
        return src;
    }

    public DatapathId getDstDpid(){
        return dst;
    }
    public OFPort getSrcPort(){
        return srcPort;
    }
    public OFPort getDstPort(){
        return dstPort;
    }
    public int getCost(){
        return cost;
    }
    
    public int getLinkSize(){
    	return count;
    }
    
    public void setCost(int cost){
        this.cost = cost;
    }
    
    public void setLinkSize(int count){
    	this.count = count;
    }
    
    public void initalLinkSize(){
    	this.count = 0;
    }
    
    public int hashCode() {
        final int prime = 56;
        int result = 1;
        result = prime * result + (int) (dst.getLong() ^ (dst.getLong() >>> 32));
        result = prime * result + dstPort.getShortPortNumber();
        result = prime * result + (int) (src.getLong() ^ (src.getLong() >>> 32));
        result = prime * result + srcPort.getShortPortNumber();
        result = prime * result + cost;
        return result;
    }
    
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LinkWithCost other = (LinkWithCost)obj;
        if (dst != other.dst)
            return false;
        if (dstPort != other.dstPort)
            return false;
        if (src != other.src)
            return false;
        if (srcPort != other.srcPort)
            return false;
        if (cost  != other.cost)
            return false;
        return true;
    }
    
    public LinkWithCost getInverse(){
        return new LinkWithCost(dst,dstPort,src,srcPort,cost);
    }

	@Override
	public int compareTo(LinkWithCost o) {
		int compare = o.getLinkSize();
		
		if(this.count == compare)
			return 0;
		else if(this.count < compare)
			return 1;
		else
			return -1;
	}
}
