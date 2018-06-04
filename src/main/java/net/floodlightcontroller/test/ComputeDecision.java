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
import net.floodlightcontroller.multipathrouting.type.LinkWithCost;
import net.floodlightcontroller.multipathrouting.type.MultiRoute;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.PathId;
import net.floodlightcontroller.staticentry.IStaticEntryPusherService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

public class ComputeDecision implements IFloodlightModule, IComputeDecisionService {
	protected IMultiPathRoutingService multipath;
	protected static IStatisticsService statisticsService;
    
    protected static IFloodlightProviderService floodlightProviderService;
    protected static IMonitorBandwidthService monitorbandwidth;
	
    private static IThreadPoolService threadPoolService;
	private static final int Interval = 12;
    
    private static int portCollectorSize = 0;
    
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
		        l.add(IStatisticsService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		this.floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		this.monitorbandwidth = context.getServiceImpl(IMonitorBandwidthService.class);
		this.multipath = context.getServiceImpl(IMultiPathRoutingService.class);
		this.threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		this.statisticsService = context.getServiceImpl(IStatisticsService.class);
		
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
		threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new StartPortThred(), Interval, Interval, TimeUnit.SECONDS);
	}
	
    protected class StartPortThred extends Thread{
    	public void run(){
    		portCollectorSize = 0;
    		portCollectorSize = statisticsService.getBandwidthSzie();
    		System.out.println("Networks Size:"+ portCollectorSize +"\n");
    	}
    }
    
    /* Return multipaths to Forwarding module */
    public MultiRoute Re_routing_Cross_Layer(FlowId fid) {
    	
    	DatapathId srcDpid = fid.getSrc();
        DatapathId dstDpid = fid.getDst();
        OFPort srcPort = fid.getSrcPort();
        OFPort dstPort = fid.getDstPort();
        

        FlowId id = new FlowId(srcDpid,srcPort,dstDpid,dstPort);
        MultiRoute result = null;

        if(!srcDpid.equals(dstDpid)){
        	try{
	            result = flowcache.get(id);
	        }catch (Exception e){
	            //logger.error("error {}",e.toString());
	        }	
        }
        
        if (result == null && srcDpid.equals(dstDpid)) 
        	return null;
        
        //We ignoring the situation if controller does not get the port status
        if(portCollectorSize != 0) {
        	// if occur congestion that re-routing 
	        if( Congestion_Detection(result) ){
		        //System.out.println("Network congestion! ");
		        System.out.println("Re-routing! ");
		        // Delete this path from multipath cache  
		        flowcache.invalidate(id);
		        try {
		        	MultiRoute route = flowcache.get(id);
		        	result = new MultiRoute();
		        	for(int i = 0 ; i < route.getRouteSize(); i++){
			    		if(!route.get_Congestion_Mark().contains(i)) {
			    			result.addRoute(route.getRoute(i));
			    		}
			    		else if (route.getAllRoute().size()/2 <= route.get_Congestion_Mark().size()) {
			    			//This situation is the multipath set have many paths congestion but it have't too many paths can use
			    			result.addRoute(route.getRoute(i));
			    		}
			    	}
				} catch (ExecutionException e) {
					//e.printStackTrace();
				}
	        }else {
	        	//No one path congestion and load balance
	        	result = transform_Type(sorting_by_bandwidth(result),null);
	        }
        }
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

        return selection_Paths(routes);
    }
    
    public MultiRoute selection_Paths(MultiRoute multipath){
    	
    	System.out.println("Sorting paths by bandwidth & Find disjoint multipath");
    	
    	//source to destination are same switch
    	if( 0 == multipath.getRouteSize()){
            return null;
    	}else{
    		
	    	//return finding_Node_Disjoint_Path(sorting_by_bandwidth(multipath), 
	    	//		multipath.getRouteSize());
    		return finding_Link_Disjoint_Path(sorting_by_bandwidth(multipath), 
	    			multipath.getRouteSize());
        }
    }
    
    public ArrayList<FlowCost> sorting_by_bandwidth(MultiRoute pathSet){
    	ArrayList<FlowCost> pathList = new ArrayList<FlowCost>();
    	int pathSize = pathSet.getRouteSize();
    	
    	for(int i = 0; i < pathSize; i++){
    		List<NodePortTuple> switchList = pathSet.getRoute(i).getPath();
    		Integer max = 0;
    		
    		if(portCollectorSize != 0)//thread failed
    		for (int indx = switchList.size() - 1; indx > 0; indx -= 2) {
    			//System.out.println("path " + i + ":" + switchPortList.get(indx).toString());
    			Long Bandwidth;
    			try{
    				if(statisticsService.getBandwidthConsumption(switchList.get(indx).getNodeId(), switchList.get(indx).getPortId())!= null){
    					//get this switch port status from this Map
    					SwitchPortBandwidth switchPortBand = statisticsService.getBandwidthConsumption(switchList.get(indx).getNodeId(), switchList.get(indx).getPortId());
	                    Bandwidth = switchPortBand.getBitsPerSecondRx().getValue()/(1024*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(1024*1024);
		                if(max <= Bandwidth.intValue()){
		                	max = Bandwidth.intValue();
		                }
    				}else{
    					max = Integer.MAX_VALUE; //Low priority path
    				}
                }catch(Exception ex){}
    		}
    	FlowCost Cost = new FlowCost(pathSet.getRoute(i),max); //available bandwidth = link capacity - link consume bandwidth
    	pathList.add(Cost);
    	}
    	Collections.sort(pathList);
    	return pathList;
    }
    
    public MultiRoute finding_Node_Disjoint_Path(ArrayList<FlowCost> paths,int pathSize){
    	Set<Integer> locationMap = new HashSet<>();//store disjoint path location in "paths"
    	
    	ArrayList<NodePortTuple> r1;
    	ArrayList<NodePortTuple> r2;
    	
    	for (int i = 0; i < pathSize; i++){
    		if(locationMap.contains(i)) continue;
    		r1 = new ArrayList<NodePortTuple>(paths.get(i).getFlowCostPath().getPath());
    		for (int j = i+1; j < pathSize; j++){
    			r2 = new ArrayList<NodePortTuple>(paths.get(j).getFlowCostPath().getPath());
    			for (int indx = r2.size() - 1; indx > 0; indx -= 2) {
    				if(r1.contains(r2.get(indx))){
    					locationMap.add(j);
    				}
    			}
    			//System.out.println("paths" + "\n" + r1.toString() + "\n" + r2.toString() + "\n" +  Flag);
    		}
    	}
    	return transform_Type(paths, locationMap);
    }
    
    public MultiRoute finding_Link_Disjoint_Path(ArrayList<FlowCost> paths,int pathSize){
    	Set<Integer> locationMap = new HashSet<>();//store disjoint path location in "paths"
    	
    	ArrayList<NodePortTuple> r1;
    	ArrayList<NodePortTuple> r2;
    	
    	for (int i = 0; i < pathSize; i++){
    		if(locationMap.contains(i)) continue;
    		r1 = new ArrayList<NodePortTuple>(paths.get(i).getFlowCostPath().getPath());
    		HashSet<LinkWithCost> r1_Link = new HashSet<LinkWithCost>(build_Link(r1));
    		for (int j = i+1; j < pathSize; j++){
    			r2 = new ArrayList<NodePortTuple>(paths.get(j).getFlowCostPath().getPath());
    			HashSet<LinkWithCost> r2_Link = new HashSet<LinkWithCost>(build_Link(r2));
    			for(LinkWithCost L2 : r2_Link) {
    				if(r1_Link.contains(L2)) {
    					locationMap.add(j);
    				}
    			}
    		}
    	}
    	return transform_Type(paths, locationMap);
    }
    
    private MultiRoute transform_Type(ArrayList<FlowCost> paths,Set<Integer> locationMap) {
    	MultiRoute disjointPath = new MultiRoute();
    	for (int index = 0; index < paths.size(); index++){
    		if(locationMap != null) {
	    		if(!locationMap.contains(index)){
	    			disjointPath.addRoute(paths.get(index).getFlowCostPath());
	    			//record disjoint congestion path, in order to avoid use congestion path
	    			//It is the batter way that get link capacity * 0.8 to instead of fixed value
	    			if( paths.get(index).getCost() >= 80 ){//800Mb/s
	    				disjointPath.set_Congestion_Mark(index);
	    			}
	    		}
    		}else {
    			disjointPath.addRoute(paths.get(index).getFlowCostPath());
    		}
    	}
		return disjointPath;
    }
    
    private HashSet<LinkWithCost> build_Link(ArrayList<NodePortTuple> multipath){
    	HashSet<LinkWithCost> Link = new HashSet<LinkWithCost>();
    	for (int index = 0; index < multipath.size()-1; index += 2) {
    		
    		Link.add(new LinkWithCost(multipath.get(index).getNodeId(), multipath.get(index).getPortId(), 
    				multipath.get(index+1).getNodeId(), multipath.get(index+1).getPortId(),1));
    	}
		return Link;
    }
    
    private boolean Congestion_Detection(MultiRoute paths){
    	
    	for (int i = 0; i < paths.getRouteSize(); i++){
    		List<NodePortTuple> r = paths.getRoute(i).getPath();
	    	for (int indx = r.size() - 1; indx > 0; indx -= 2) {
	    		if(statisticsService.getBandwidthConsumption(r.get(indx).getNodeId(), r.get(indx).getPortId())!= null) {
	    			SwitchPortBandwidth switchPortBand = statisticsService.getBandwidthConsumption(r.get(indx).getNodeId(), r.get(indx).getPortId());
	    			Long Bandwidth = switchPortBand.getBitsPerSecondRx().getValue()/(1024*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(1024*1024);
	    			//System.out.println("Bandwidth:" + Bandwidth ); 
		            if(Bandwidth.intValue() >= 80){
		            	return true;
		    		}
	    		}else {
	    			//can't get the port status we ignore this situation
	    		}
	    	}
    	}
    	return false;
    }

	@Override
	public void resetcomputeDecision() {
		// TODO Auto-generated method stub
		flowcache.invalidateAll();
	}

}
