package net.floodlightcontroller.test;

import java.util.ArrayList;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.multipathrouting.type.FlowId;
import net.floodlightcontroller.multipathrouting.type.MultiRoute;

public interface IComputeDecisionService extends IFloodlightService {
	public void PrintPortBandwidth();
	public java.util.Map<DatapathId,Map<OFPort,Long>> collectBandwidth();
	public MultiRoute sortMultipath(MultiRoute multipath);
	public MultiRoute Route(DatapathId srcDpid, OFPort srcPort, DatapathId dstDpid, OFPort dstPort);
	public MultiRoute FlowRoute(FlowId fid);
}
