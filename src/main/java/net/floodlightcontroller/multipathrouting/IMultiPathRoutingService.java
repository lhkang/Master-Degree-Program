package net.floodlightcontroller.multipathrouting;

import java.util.Date;
import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.multipathrouting.type.MultiRoute;

public interface IMultiPathRoutingService extends IFloodlightService {
	public void modifyLinkCost(DatapathId srcDpid,DatapathId dstDpid,short cost);
	public MultiRoute getRoute(DatapathId srcDpid,OFPort srcPort,DatapathId dstDpid,OFPort dstPort);
	
}
