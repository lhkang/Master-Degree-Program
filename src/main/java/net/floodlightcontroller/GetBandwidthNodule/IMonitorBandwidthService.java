package net.floodlightcontroller.GetBandwidthNodule;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.sun.javafx.collections.MappingChange.Map;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

public interface IMonitorBandwidthService extends IFloodlightService {
	public java.util.Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();
}