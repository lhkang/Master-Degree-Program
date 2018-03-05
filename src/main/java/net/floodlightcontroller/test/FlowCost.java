package net.floodlightcontroller.test;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.multipathrouting.type.NodeCost;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.PathId;

public class FlowCost implements Comparable<FlowCost> {
	
	private final Path route;
	private Integer cost;

    public FlowCost(Path route ,Integer cost){
    	this.route = route;
    	this.cost = cost;
    }
    
    public Path getFlowCostPath() {
        return route;
    }
    
    public Integer getCost() {
        return cost;
    }
    
	@Override
	public int compareTo(FlowCost flow) {
		int compare = flow.getCost();
		
		if(this.cost == compare)
			return 0;
		else if(this.cost < compare)  //else if(this.cost < compare)
			return 1;
		else
			return -1;
	}
	
}
