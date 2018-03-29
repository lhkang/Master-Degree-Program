package net.floodlightcontroller.GetQueueStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFQueueStatsEntry;
import org.projectfloodlight.openflow.protocol.OFQueueStatsReply;
import org.projectfloodlight.openflow.protocol.OFQueueStatsRequest;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.test.Container;

public class GetQueueStatus implements IFloodlightModule, IGetQueueStatusService {
	
	protected static IFloodlightProviderService floodlightProvider;
	private static IOFSwitchService switchService;
	protected static Logger logger;
	private HashMap<DatapathId,HashMap<Integer,ArrayList>> portQueue;
	

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IGetQueueStatusService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
     	m.put(IGetQueueStatusService.class, this);
     	return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        logger = LoggerFactory.getLogger(GetQueueStatus.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub

	}
	
	public synchronized void startCollectBandwidth(){
		Set<DatapathId> switchDpid = switchService.getAllSwitchDpids();
		portQueue = new HashMap<DatapathId,HashMap<Integer,ArrayList>>();
		
		ExecutorService taskExecutor = Executors.newFixedThreadPool(switchDpid.size());
    	GetQueuesThred t;
    	
    	for(DatapathId l : switchDpid){
    		t = new GetQueuesThred(DatapathId.of(l.getLong()));
    		//t.start();
    		taskExecutor.execute(t);
    	}
    	
    	taskExecutor.shutdown();
    	
    	try {
    		  taskExecutor.awaitTermination(1, TimeUnit.NANOSECONDS);	  
    	} catch (InterruptedException e) { }

    	//printQueue(portQueue);//print all
	}
	
	protected class GetQueuesThred extends Thread{

	   	OFFactory flowFactory = OFFactories.getFactory(OFVersion.OF_13);
	   	DatapathId switchId = null;
	   	long previous_txb = 0;
	   	
	   	protected ArrayList<Container> Lists = new ArrayList<Container>();
	   	protected ArrayList<Integer> PortAddresses = new ArrayList<Integer>();
	   	private HashMap<Integer,ArrayList> collMap  = new HashMap<Integer,ArrayList> ();

	   	protected GetQueuesThred(DatapathId switchId){
	   		this.switchId = switchId;		
	   	}
	   	
    	public void run(){
    		if(switchId != null){
    			
	    	   	OFQueueStatsRequest sr = flowFactory.buildQueueStatsRequest().setPortNo(OFPort.ANY)
	                    .setQueueId(UnsignedLong.MAX_VALUE.longValue()).build();
	    	   	
	    	   	ListenableFuture<List<OFQueueStatsReply>> future = switchService.getSwitch(switchId).writeStatsRequest(sr);
	    	   	
	    	   	try {	   	
	     	   	   List<OFQueueStatsReply> replies = future.get(10, TimeUnit.SECONDS);
	     	   	   
	     	   	   for (OFQueueStatsReply reply : replies) {	//substract port
	     	   		    //logger.info(reply.toString());
	     	   	        for (OFQueueStatsEntry e : reply.getEntries()) {	//substract queue
	     	   	        	long id = e.getQueueId();
	     	   	        	U64 txb = e.getTxBytes();
	     	   	        	U64 txp = e.getTxPackets();
	     	   	            int ports = e.getPortNo().getPortNumber();
	     	   	        	
	     	   	        	if(!PortAddresses.contains(ports)){
	     	   	        		PortAddresses.add(ports);
	     	   	        	}
	     	   	        	
	     	   	            Container object = new Container(e.getPortNo(),id,txb,txp);
	     	   	            Lists.add(object);
	     	   	            
	     	   	           //long summary = e.getTxBytes().getValue()/(8*1024) - previous_txb;
	     	   	           //logger.info("PORT()"+String.valueOf(e.getPortNo()));
	     	   	           //logger.info("e.getQueueId()"+String.valueOf(id));
	     	       		   //logger.info("e.getTxBytes()"+String.valueOf(txb.getBigInteger()));
	     	       		   //logger.info("e.getTxPackets()"+String.valueOf(txp.getBigInteger()));
	     	       		   //logger.info("e.getTxBytes()"+  summary);
	     	       		   
	     	       		   previous_txb = txb.getValue()/(8*1024);
	     	       		   
	     	   	        }
	     	   	   }
	     	   	   
	     	   	for(int i = 1 ;i <= PortAddresses.size();i++){
	    			ArrayList<Container> l = new ArrayList<Container>();
	    			List<Integer> arr = new ArrayList<Integer>();
	    			Integer c = PortAddresses.get(i-1);
	    			Integer j = 1;
	    			for(Container ob : Lists){
	    				int temp = ob.getportNo();
	    				if(temp == c){
	    					l.add(ob);
	    					arr.add(j);
	    				}
	    				j++;
	    				collMap.put(c, l);
		    		}
	    			for(Integer k :arr){
	    				Lists.remove(k);
	    			}
	    		}  

	     	   	} catch ( Exception e) { 
	     	   	   e.printStackTrace();
	     	   	}
    		}
    		
    		if(!collMap.isEmpty()){
    			portQueue.put(switchId, collMap); 
    			//collMap.clear();
    		}
    	}
    }
	
	public Map<DatapathId, HashMap<Integer, ArrayList>> collectQueue(){
		return portQueue;
	}
	
	public void printQueue(HashMap<DatapathId, HashMap<Integer,ArrayList>> entry){
		System.out.println("Switch Queue List :" + "\n");
		for(Map.Entry<DatapathId, HashMap<Integer,ArrayList>> entrys : portQueue.entrySet()){
			System.out.println("Switch MacAddress : " + entrys.getKey()+ "\n");
			HashMap<Integer,ArrayList> cl = entrys.getValue();
			//if(cl == null){break;}
			for(HashMap.Entry<Integer, ArrayList> entryMap : cl.entrySet()){
				System.out.println("Switch Port : " + entryMap.getKey()+"\n");
				ArrayList<Container> alst = entryMap.getValue();
				if(alst == null){break;}
				for(Container ct : alst){
					System.out.println (" Queue Id "+ct.getQueId()+"\n");
					System.out.println ("");
				}
			}
		}
    }
	
}
