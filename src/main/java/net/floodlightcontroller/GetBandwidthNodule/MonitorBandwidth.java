package net.floodlightcontroller.GetBandwidthNodule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketQueue;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigRequest;
import org.projectfloodlight.openflow.protocol.OFQueueStatsEntry;
import org.projectfloodlight.openflow.protocol.OFQueueStatsReply;
import org.projectfloodlight.openflow.protocol.OFQueueStatsRequest;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActionSetQueue;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.protocol.queueprop.OFQueueProp;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.StatisticsCollector;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OFMessageDamper;

public class MonitorBandwidth implements IFloodlightModule,IMonitorBandwidthService{
	
	private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);
	
	protected static IFloodlightProviderService floodlightProvider;
	protected static IStatisticsService statisticsService;
	private static IOFSwitchService switchService;
	private static IStaticEntryPusherService sfpService;
	protected static Logger logger;
	private static IThreadPoolService threadPoolService;
	private static ScheduledFuture<?> portBandwidthCollector;
	private static final int portBandwidthInterval = 15;
	
	private static Map<NodePortTuple,SwitchPortBandwidth> bandwidth;
	public Map<DatapathId,Map<OFPort,Long>> BandwidthMap = new HashMap<DatapathId,Map<OFPort,Long>>();
	 
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IMonitorBandwidthService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IMonitorBandwidthService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStatisticsService.class);
        l.add(IOFSwitchService.class);
        l.add(IThreadPoolService.class);
        l.add(IStaticEntryPusherService.class);
        return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        statisticsService = context.getServiceImpl(IStatisticsService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        sfpService = context.getServiceImpl(IStaticEntryPusherService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		//startCollectBandwidth();
	}

	private synchronized void startCollectBandwidth(){
        portBandwidthCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new GetBandwidthThread(), portBandwidthInterval, portBandwidthInterval, TimeUnit.SECONDS);
        log.warn("Statistics collection thread(s) started");
    }
	
	private class GetBandwidthThread extends Thread implements Runnable  {
	        private Map<NodePortTuple,SwitchPortBandwidth> bandwidth;

	        public Map<NodePortTuple, SwitchPortBandwidth> getBandwidth() {
	            return bandwidth;
	        }
    	        
	        @Override
	        public void run() {
	        	System.out.println("GetBandwidthThread run()....");
	            bandwidth = getBandwidthMap(); 
	            System.out.println("bandwidth.size():"+bandwidth.size());
	        }
	 }
	
	public java.util.Map<DatapathId,Map<OFPort,Long>> collectBandwidth(){
		return BandwidthMap;
	}
	
	@Override
	public Map<NodePortTuple,SwitchPortBandwidth> getBandwidthMap(){
		bandwidth = statisticsService.getBandwidthConsumption();
//      
//      for(NodePortTuple tuple:bandwidth.keySet()){
//          System.out.println(tuple.getNodeId().toString()+","+tuple.getPortId().getPortNumber());
//          System.out.println();
//      }
		
		/*
        Iterator<Entry<NodePortTuple,SwitchPortBandwidth>> iter = bandwidth.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<NodePortTuple,SwitchPortBandwidth> entry = iter.next();
            NodePortTuple tuple  = entry.getKey();
            SwitchPortBandwidth switchPortBand = entry.getValue();
            System.out.print(tuple.getNodeId()+","+tuple.getPortId().getPortNumber()+",");
            System.out.println(switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024));

        }
		*/

        return bandwidth;
    }

	@Override
	public void initial() {
		// TODO Auto-generated method stub
		
	}
  
}