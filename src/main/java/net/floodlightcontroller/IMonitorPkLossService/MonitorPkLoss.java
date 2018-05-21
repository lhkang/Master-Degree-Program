package net.floodlightcontroller.IMonitorPkLossService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.StatisticsCollector;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;

public class MonitorPkLoss implements IMonitorPkLossService, IFloodlightModule, IOFMessageListener {
	
    private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);

    private static  HashMap<NodePortTuple, Long> DPID_PK_LOSS = new HashMap<NodePortTuple, Long>();

    protected static IFloodlightProviderService floodlightProvider;
    protected static IStatisticsService statisticsService;
    private static IOFSwitchService switchService;
    private static IThreadPoolService threadPoolService;
    private static ScheduledFuture<?> portStatsCollector;

    private static int portStatsInterval = 10;
	
	@Override
	public String getName() {
		return "monitorpkloss";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
//      Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
//      Long sourceMACHash = eth.getSourceMACAddress().getLong();
//      OFFactory factory = sw.getOFFactory();

//      if(msg.getType() == OFType.STATS_REPLY){
//          OFStatsReply reply = (OFStatsReply) msg;
//          OFPortStatsReply psr = (OFPortStatsReply) reply;
//          OFPortStatsEntry pse = (OFPortStatsEntry) psr;
//          System.out.println("rx bytes:"+pse.getRxBytes().getValue());
//          System.out.println("rx_dropped bytes:"+pse.getRxDropped().getValue());
//          System.out.println("tx bytes:"+pse.getTxBytes().getValue());
//          System.out.println("tx_dropped bytes:"+pse.getTxDropped().getValue());
//      }

        return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IMonitorPkLossService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IMonitorPkLossService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStatisticsService.class);
        l.add(IOFSwitchService.class);
        l.add(IThreadPoolService.class);
        return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        statisticsService = context.getServiceImpl(IStatisticsService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        startStatisticsCollection();
	}
	
	public Long getPacketLossConsumption(DatapathId dpid, OFPort p) {
		return DPID_PK_LOSS.get(new NodePortTuple(dpid, p));
	}
	
	/*Start all start threads.*/
	private synchronized void startStatisticsCollection() {
        portStatsCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new PortStatsCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);
        log.warn("Statistics collection thread(s) started");
    }
	
	/**
     * Single thread for collecting switch statistics and containing the reply.
     */
    private class GetStatisticsThread extends Thread {
        private List<OFStatsReply> statsReply;
        private DatapathId switchId;
        private OFStatsType statType;

        public GetStatisticsThread(DatapathId switchId, OFStatsType statType) {
            this.switchId = switchId;
            this.statType = statType;
            this.statsReply = null;
        }

        public List<OFStatsReply> getStatisticsReply() {
            return statsReply;
        }

        public DatapathId getSwitchId() {
            return switchId;
        }

        @Override
        public void run() {
//          System.out.println("run............");
            statsReply = getSwitchStatistics(switchId, statType);
        }
    }
    
    @SuppressWarnings("unchecked")
    protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId, OFStatsType statsType) {
        IOFSwitch sw = switchService.getSwitch(switchId);
        //System.out.println("getSwitchStatistics............");
        ListenableFuture<?> future;
        List<OFStatsReply> values = null;
        Match match;
        if (sw != null) {
            OFStatsRequest<?> req = null;
            switch (statsType) {
            case FLOW:
                match = sw.getOFFactory().buildMatch().build();
                req = sw.getOFFactory().buildFlowStatsRequest()
                        .setMatch(match)
                        .setOutPort(OFPort.ANY)
                        .setTableId(TableId.ALL)
                        .build();
                break;
            case AGGREGATE:
                match = sw.getOFFactory().buildMatch().build();
                req = sw.getOFFactory().buildAggregateStatsRequest()
                        .setMatch(match)
                        .setOutPort(OFPort.ANY)
                        .setTableId(TableId.ALL)
                        .build();
                break;
            case PORT:
                req = sw.getOFFactory().buildPortStatsRequest()
                .setPortNo(OFPort.ANY)
                .build();
                break;
            case QUEUE:
                req = sw.getOFFactory().buildQueueStatsRequest()
                .setPortNo(OFPort.ANY)
                .setQueueId(UnsignedLong.MAX_VALUE.longValue())
                .build();
                break;
            case DESC:
                req = sw.getOFFactory().buildDescStatsRequest()
                .build();
                break;
            case GROUP:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildGroupStatsRequest()                
                            .build();
                }
                break;

            case METER:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildMeterStatsRequest()
                            .setMeterId(OFMeterSerializerVer13.ALL_VAL)
                            .build();
                }
                break;

            case GROUP_DESC:            
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildGroupDescStatsRequest()            
                            .build();
                }
                break;

            case GROUP_FEATURES:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildGroupFeaturesStatsRequest()
                            .build();
                }
                break;

            case METER_CONFIG:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildMeterConfigStatsRequest()
                            .build();
                }
                break;

            case METER_FEATURES:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildMeterFeaturesStatsRequest()
                            .build();
                }
                break;

            case TABLE:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildTableStatsRequest()
                            .build();
                }
                break;

            case TABLE_FEATURES:    
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                    req = sw.getOFFactory().buildTableFeaturesStatsRequest()
                            .build();       
                }
                break;
            case PORT_DESC:
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                    req = sw.getOFFactory().buildPortDescStatsRequest()
                            .build();
                }
                break;
            case EXPERIMENTER:      
            default:
                log.error("Stats Request Type {} not implemented yet", statsType.name());
                break;
            }
            try {
                if (req != null) {
                    future = sw.writeStatsRequest(req); 
                    values = (List<OFStatsReply>) future.get(portStatsInterval / 2, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch {}. {}", sw, e);
            }
        }
        return values;
    }
    /* Retrieve the statistics from all switches in parallel. */
    private Map<DatapathId, List<OFStatsReply>> getAllSwitchStatistics(Set<DatapathId> dpids, OFStatsType statsType) {
        HashMap<DatapathId, List<OFStatsReply>> model = new HashMap<DatapathId, List<OFStatsReply>>();

        List<GetStatisticsThread> activeThreads = new ArrayList<GetStatisticsThread>(dpids.size());
        List<GetStatisticsThread> pendingRemovalThreads = new ArrayList<GetStatisticsThread>();
        GetStatisticsThread t;
        for (DatapathId d : dpids) {
            t = new GetStatisticsThread(d, statsType);
            activeThreads.add(t);
            t.start();
        }

        /* Join all the threads after the timeout. Set a hard timeout
         * of 12 seconds for the threads to finish. If the thread has not
         * finished the switch has not replied yet and therefore we won't
         * add the switch's stats to the reply.
         */
        for (int iSleepCycles = 0; iSleepCycles < portStatsInterval; iSleepCycles++) {
            for (GetStatisticsThread curThread : activeThreads) {
                if (curThread.getState() == State.TERMINATED) {
                    model.put(curThread.getSwitchId(), curThread.getStatisticsReply());
                    pendingRemovalThreads.add(curThread);
                }
            }

            /* remove the threads that have completed the queries to the switches */
            for (GetStatisticsThread curThread : pendingRemovalThreads) {
                activeThreads.remove(curThread);
            }

            /* clear the list so we don't try to double remove them */
            pendingRemovalThreads.clear();

            /* if we are done finish early */
            if (activeThreads.isEmpty()) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for statistics", e);
            }
        }

        return model;
    }
    private class PortStatsCollector implements Runnable {
        @Override
        public void run() {
//          System.out.println("Runnable run()....");
            Map<DatapathId, List<OFStatsReply>> replies = getAllSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.PORT);
//          System.out.println("replies.size():"+replies.size());
            for (Entry<DatapathId, List<OFStatsReply>> e : replies.entrySet()) {
                for (OFStatsReply r : e.getValue()) {
                    OFPortStatsReply psr = (OFPortStatsReply) r;
                    for (OFPortStatsEntry pse : psr.getEntries()) {
//                      System.out.println("dpid:"+e.getKey().toString());
//                      System.out.println("for (OFPortStatsEntry pse : psr.getEntries())");

                        double pk_loss = 0;

                        if(e.getKey().toString().equals("") || e.getKey() == null){
//                          System.out.println("e.getKey() is null....");
                        }
//                      System.out.println("--------------------------------------------");
                        NodePortTuple npt = new NodePortTuple(e.getKey(), pse.getPortNo());
//                      System.out.println("--------------------------------------------");
//                      System.out.println(pse.getRxDropped().getValue() + pse.getTxDropped().getValue());
//                      System.out.println(pse.getRxBytes().getValue() + pse.getTxBytes().getValue());
                        /* if 'totalPacket' equal 0 then packet loss is infinite */
                        double totalPacket = (double)(pse.getRxBytes().getValue()) + (double)(pse.getTxBytes().getValue());
                        if(totalPacket != 0){
                        	/* Packet loss rate(封包遺失率) = (傳送封包總數 - 接收到的封包總數) / (傳送封包總數) */
                            pk_loss = ((double)(pse.getRxDropped().getValue()) + (double)(pse.getTxDropped().getValue()))/totalPacket;
                        }else{
                            pk_loss = 0;
                        }
                        
//                      System.out.println("PK_LOSS:"+pk_loss+",dpid:"+e.getKey().toString());
                        DPID_PK_LOSS.put(npt, pse.getRxDropped().getValue() + pse.getTxDropped().getValue());
                        //DPID_PK_LOSS.put(npt, (long)pk_loss);
                    }
                }
            }
            /*
            File file = new File("/home/floodlight/Desktop/Experiment_data/packetLoss20.txt");
		    FileWriter writer = null;
		    try {
		        writer = new FileWriter(file, true);
		        Iterator<Entry<NodePortTuple,Long>> iter = DPID_PK_LOSS.entrySet().iterator();
	            while (iter.hasNext()) {
	                Entry<NodePortTuple,Long> entry = iter.next();
	                NodePortTuple tuple  = entry.getKey();
	                Long lossRate = entry.getValue();
	                writer.write(tuple.getNodeId()+","+tuple.getPortId()+",");
	                writer.write(lossRate.toString() + "\n");
	                //System.out.print(tuple.getNodeId()+","+tuple.getPortId()+",");
	                //System.out.println(lossRate);
	            }
		    } catch (IOException ex) {
		        ex.printStackTrace(); // I'd rather declare method with throws IOException and omit this catch.
		    } finally {
		        if (writer != null) try { writer.close(); } catch (IOException ignore) {}
		    }
		    */
/*
            Iterator<Entry<NodePortTuple,Long>> iter = DPID_PK_LOSS.entrySet().iterator();
            while (iter.hasNext()) {
                Entry<NodePortTuple,Long> entry = iter.next();
                NodePortTuple tuple  = entry.getKey();
                Long lossRate = entry.getValue();
                System.out.print(tuple.getNodeId()+","+tuple.getPortId()+",");
                System.out.println(lossRate);
            }
*/
        }
    }
}
