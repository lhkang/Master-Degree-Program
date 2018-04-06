package net.floodlightcontroller.IMonitorPkLossService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IMonitorPkLossService extends IFloodlightService {
	public Long getPacketLossConsumption(DatapathId dpid, OFPort p);
}
