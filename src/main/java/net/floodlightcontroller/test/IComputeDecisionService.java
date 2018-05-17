package net.floodlightcontroller.test;

import java.util.ArrayList;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.multipathrouting.type.FlowId;
import net.floodlightcontroller.multipathrouting.type.MultiRoute;

public interface IComputeDecisionService extends IFloodlightService {
	public MultiRoute sortPaths(MultiRoute multipath);
	public MultiRoute Route(FlowId fid);
	public MultiRoute FlowRoute(FlowId fid);
	public void resetcomputeDecision();
}
