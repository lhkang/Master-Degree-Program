package net.floodlightcontroller.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.floodlightcontroller.GetBandwidthNodule.IMonitorBandwidthService;
import net.floodlightcontroller.GetBandwidthNodule.MonitorBandwidth;
import net.floodlightcontroller.GetQueueStatus.GetQueueStatus;
import net.floodlightcontroller.GetQueueStatus.IGetQueueStatusService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.multipathrouting.IMultiPathRoutingService;
import net.floodlightcontroller.multipathrouting.MultiPathRouting;
import net.floodlightcontroller.multipathrouting.type.FlowId;
import net.floodlightcontroller.multipathrouting.type.MultiRoute;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.PathId;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

/*Github testing*/

public class ComputeDecision implements IFloodlightModule, IComputeDecisionService {
	protected IMultiPathRoutingService multipath;
	
    private Map<DatapathId,Map<OFPort,Long>> BandwidthMap;
    private Map<DatapathId, HashMap<Integer, ArrayList>> Queue;
    
    protected static IFloodlightProviderService floodlightProviderService;
    protected static IMonitorBandwidthService monitorbandwidth;
    private static GetQueueStatus getqueuestatus;
	
    private static IThreadPoolService threadPoolService;
	private static ScheduledFuture<?> portBandwidthCollector;//
    private static final int Interval = 15;
    
    private int portCollectorSize = 0;
    
    protected class FlowCacheLoader extends CacheLoader<FlowId,MultiRoute> {
    	ComputeDecision mpr;
        FlowCacheLoader(ComputeDecision mpr) {
            this.mpr = mpr;
    }

        @Override
        public MultiRoute load(FlowId fid) {
            return mpr.FlowRoute(fid);
        }
    }
    
    private final FlowCacheLoader flowCacheLoader = new FlowCacheLoader(this);
    protected LoadingCache<FlowId,MultiRoute> flowcache;
    
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IComputeDecisionService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>,IFloodlightService>();
        m.put(IComputeDecisionService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		        l.add(IFloodlightProviderService.class);
		        l.add(IStaticEntryPusherService.class);
		        l.add(IMonitorBandwidthService.class);
		        l.add(IGetQueueStatusService.class);
		        l.add(IThreadPoolService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		this.floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		this.monitorbandwidth = context.getServiceImpl(IMonitorBandwidthService.class);
		this.getqueuestatus = (GetQueueStatus)context.getServiceImpl(IGetQueueStatusService.class);
		this.multipath = context.getServiceImpl(IMultiPathRoutingService.class);
		this.threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		flowcache = CacheBuilder.newBuilder().concurrencyLevel(4)
                .maximumSize(1000L)
                .build(
                        new CacheLoader<FlowId,MultiRoute>() {
                            public MultiRoute load(FlowId fid) {
                                return flowCacheLoader.load(fid);
                            }
                        });
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		startCollectBandwidth();
	}
	
	
	private synchronized void startCollectBandwidth(){
		portBandwidthCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new StartPortThred(), Interval, Interval, TimeUnit.SECONDS);
	}
	
    protected class StartPortThred extends Thread{
    	public void run(){
    		//portCollectorSize = 0;
    		BandwidthMap = new HashMap<DatapathId,Map<OFPort,Long>>();
    		BandwidthMap =  ((MonitorBandwidth) monitorbandwidth).collectBandwidth();
    		portCollectorSize = monitorbandwidth.getBandwidthMap().size();
	        System.out.println("Networks Size:"+ portCollectorSize +"\n");
	        
    	}
    }
    
    /* For Forwarding module */
    public MultiRoute Route(DatapathId srcDpid, OFPort srcPort, DatapathId dstDpid, OFPort dstPort) {

        if (srcDpid.equals(dstDpid) && srcPort.equals(dstPort))
            return null;

        FlowId id = new FlowId(srcDpid,srcPort,dstDpid,dstPort);
        MultiRoute result = null;

        if(!srcDpid.equals(dstDpid)){
        	try{
	            result = flowcache.get(id);
	        }catch (Exception e){
	            //logger.error("error {}",e.toString());
	        }	
        }

        /* if result is congestion re-search database */
           if( portCollectorSize != 0 && isCongestion(result).getFlag() ){
	            //System.out.println("Network congestion! ");
	            System.out.println("Re-routing! ");
	            
	            /* Delete this flow from paths database  */
	            flowcache.invalidate(id);
	            	
	            try {
	    			result = flowcache.get(id);
	    		} catch (ExecutionException e) {
	    			// TODO Auto-generated catch block
	    			//e.printStackTrace();
	    		}
           }
        
        
        //add load balance module ! 
        
        if (result == null && srcDpid.equals(dstDpid)) return null;
        return result;
	}
    
    public MultiRoute FlowRoute(FlowId fid){
    	
        DatapathId srcDpid = fid.getSrc();
        DatapathId dstDpid = fid.getDst();
        OFPort srcPort = fid.getSrcPort();
        OFPort dstPort = fid.getDstPort();

        MultiRoute routes = null;
        
        try{
        	routes = multipath.getRoute(srcDpid, 
		    		srcPort, 
		    		dstDpid, 
		    		dstPort);
        }catch (Exception e){}

        return sortMultipath(routes);
    }
    
    public MultiRoute sortMultipath(MultiRoute multipath){
    	
    	ArrayList<FlowCost> pathList = new ArrayList<FlowCost>();
    	int pathSize = multipath.getRouteSize();
    	
    	System.out.println("Start sort paths & Find disjoint paths");
    	
    	//source to destination are same switch
    	if( 0 == pathSize){
            return null;
    	}else{
	    	for(int i = 0; i < pathSize; i++){
	    		List<NodePortTuple> switchPortList = multipath.getRoute(i).getPath();
	    		Integer max = 0;
	    		
	    		if(portCollectorSize != 0)
	    		for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
	    			//System.out.println("path " + i + ":" + switchPortList.get(indx).toString());
	    			Integer Bandwidth;
	    			try{
	    				if(BandwidthMap.containsValue(switchPortList.get(indx).getNodeId())){
	    					//get this switch port status from this Map
	    					Map<OFPort,Long> portList = BandwidthMap.get(switchPortList.get(indx).getNodeId());
		                    Bandwidth = portList.get(switchPortList.get(indx).getPortId()).intValue();
			                if(max <= Bandwidth){
			                	max = Bandwidth;
			                }
	    				}else{
	    					//Bandwidth = Integer.MAX_VALUE;//do well for example do't use this path
	    					//flag = false;
	    				}
	    				
	                }catch(Exception ex){}
	    		}
	    			FlowCost Cost = new FlowCost(multipath.getRoute(i),max); //10000-max
	    			pathList.add(Cost);
	    	}
	    	Collections.sort(pathList);
	    	return findDisjointPath(pathList, pathSize);
        }
    }
    
    public MultiRoute findDisjointPath(ArrayList<FlowCost> paths,int pathSize){
    	MultiRoute disjoint = new MultiRoute();
    	Set<Integer> locationMap = new HashSet<>();//store disjoint path location in "paths"
    	
    	List<NodePortTuple> r1 = null;
    	List<NodePortTuple> r2 = null;
    	
    	for (int i = 0; i < pathSize; i++){
    		if(locationMap.contains(i)) continue;
    		r1 = paths.get(i).getFlowCostPath().getPath();
    		for (int j = i+1; j < pathSize; j++){
    			r2 = paths.get(j).getFlowCostPath().getPath();
    			for (int indx = r2.size() - 1; indx > 0; indx -= 2) {
    				if(isContains(r1,r2.get(indx))){
    					locationMap.add(j);
    				}
    			}
    			//System.out.println("paths" + "\n" + r1.toString() + "\n" + r2.toString() + "\n" +  Flag);
    		}
    	}
    	
    	for (int index = 0; index < pathSize; index++){
    		if(!locationMap.contains(index)){
    			disjoint.addRoute(paths.get(index).getFlowCostPath());
    			//record disjoint congestion path, in order to avoid use congestion path
    			if(paths.get(index).getCost() >= 5000){
    				disjoint.CongestionFlag(true);
    				disjoint.addlocation(index);
    				//show paths
    			}
    		}
    	}
    	
    	/* print out all disjoint path and no one are congestion*/
    	
    	/*for(int l = 0 ; l < disjoint.getRouteSize(); l++){
    		if(disjoint.getLocation().contains(l)) continue;
    		System.out.println("disjoint path " + l + ":" + disjoint.getRoute(l).toString());
    	}*/
    	
    	//System.out.println("disjoint count :" + disjoint.getRouteSize());
    	return disjoint;
    }
    
    boolean isContains(List<NodePortTuple> r1,NodePortTuple r2){
		boolean result = false;
		
		for (int ntp = r1.size() - 1; ntp > 0; ntp -= 2) {
			if((r1.get(ntp).getNodeId().equals(r2.getNodeId()))&&(r1.get(ntp).getPortId().equals(r2.getPortId())) ){
				result = true;
				break;
			}
			//System.out.println(r1.get(ntp).toString() + " " + r2.toString() + " " + result);
			//result = false;
		}
		//System.out.println(result);
    	return result;
    }
    
    public MultiRoute isCongestion(MultiRoute paths){
		
    	boolean iFlag = false;
    	paths.initialtion(); 
    	
    	//NestedLoop:
    	for (int i = 0; i < paths.getRouteSize(); i++){
    		List<NodePortTuple> r = paths.getRoute(i).getPath();
	    	for (int indx = r.size() - 1; indx > 0; indx -= 2) {
	    		//if(BandwidthMap.containsValue(r.get(indx).getNodeId())){
		    		Map<OFPort,Long> portList = BandwidthMap.get(r.get(indx).getNodeId());
		            Integer Bandwidth = portList.get(r.get(indx).getPortId()).intValue();
		            //System.out.println("Bandwidth:" + Bandwidth ); 
		            if(Bandwidth >= 5000){
		    			iFlag = true;
		    			paths.addlocation(i);
		    			//break NestedLoop;
		    		}
	    	}
    	}
    	paths.CongestionFlag(iFlag);
    	return paths;
    }
    
    public void PrintPortBandwidth(){
    	for(Map.Entry<DatapathId, Map<OFPort,Long>> iter : BandwidthMap.entrySet()){
      		System.out.println("Switch MacAddress : " + iter.getKey()+ "\n");
      		Map<OFPort,Long> swPort = iter.getValue();
      		
      		for(Map.Entry<OFPort, Long> c : swPort.entrySet()){
      			OFPort p = c.getKey();
      			System.out.println("Port : " + p + "\n");
      			System.out.println("Bandwidth : " + c.getValue() + "\n");
      		}
      	}
    }
    
    public java.util.Map<DatapathId,Map<OFPort,Long>> collectBandwidth(){
		return BandwidthMap;
	}

}
