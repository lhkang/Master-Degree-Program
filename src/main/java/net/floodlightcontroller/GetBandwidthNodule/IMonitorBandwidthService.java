package net.floodlightcontroller.GetBandwidthNodule;

import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;


import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

public interface IMonitorBandwidthService extends IFloodlightService {
	public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();
	public void initial();
}
