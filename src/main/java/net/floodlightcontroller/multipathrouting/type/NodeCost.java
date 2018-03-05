package net.floodlightcontroller.multipathrouting.type;

import org.projectfloodlight.openflow.types.DatapathId;

public class NodeCost implements Comparable<NodeCost> {
	private final String nodeStr;
    private final DatapathId node;
    private final int cost;
	
    public String getDpidStr() {
        return nodeStr;
    }
    public DatapathId getDpid(){
        return node;
    }
    public int getCost() {
        return cost;
    }
    
    public NodeCost(DatapathId node, int cost) {
        this.node = node;
        this.nodeStr = node.toString();
        this.cost = cost;
    }
    
	@Override
	public int compareTo(NodeCost o) {
		// TODO Auto-generated method stub
		if (o.cost == this.cost) {
            return (int)(this.node.getLong() - o.node.getLong());
        }
		return this.cost - o.cost;
	}
    
	public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NodeCost other = (NodeCost) obj;
        if (node == null) {
            if (other.node != null)
                return false;
        } else if (!node.equals(other.node))
            return false;
        return true;
	}
	
	public int hashCode() {
        assert false : "hashCode not designed";
        return 42;
	}
	
}
