package net.floodlightcontroller.GetQueueStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IGetQueueStatusService extends IFloodlightService {
	public Map<DatapathId, HashMap<Integer, ArrayList>> collectQueue();
}
