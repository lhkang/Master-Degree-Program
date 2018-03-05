package net.floodlightcontroller.GetQueueStatus;

import java.util.ArrayList;
import java.util.HashMap;

import org.projectfloodlight.openflow.types.DatapathId;
import com.sun.javafx.collections.MappingChange.Map;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IGetQueueStatusService extends IFloodlightService {
	public java.util.Map<DatapathId, HashMap<Integer, ArrayList>> collectQueue();
}
