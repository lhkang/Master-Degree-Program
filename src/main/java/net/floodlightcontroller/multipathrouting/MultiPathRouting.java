package net.floodlightcontroller.multipathrouting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.LinkedList;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.multipathrouting.type.FlowId;
import net.floodlightcontroller.multipathrouting.type.LinkWithCost;
import net.floodlightcontroller.multipathrouting.type.MultiRoute;
import net.floodlightcontroller.multipathrouting.type.NodeCost;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.PathId;
import net.floodlightcontroller.test.IComputeDecisionService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.restserver.IRestApiService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.Collection;

public class MultiPathRouting implements IFloodlightModule, ITopologyListener, IMultiPathRoutingService {
	protected IComputeDecisionService computeDecision;
	
	protected static Logger logger;
    protected IFloodlightProviderService floodlightProvider;
    protected ITopologyService topologyService;
    protected IRestApiService restApi;
    protected final int ROUTE_LIMITATION = 15;
    protected HashMap<DatapathId, HashSet<LinkWithCost>> dpidLinks;
    protected static int pathCount = 0;
    protected static double Density = 0;
    
    protected class FlowCacheLoader extends CacheLoader<FlowId,MultiRoute> {
        MultiPathRouting mpr;
        FlowCacheLoader(MultiPathRouting mpr) {
            this.mpr = mpr;
    }

        @Override
        public MultiRoute load(FlowId fid) {
            return mpr.buildFlowRoute(fid);
        }
    }
    
    private final FlowCacheLoader flowCacheLoader = new FlowCacheLoader(this);
    protected LoadingCache<FlowId,MultiRoute> flowcache;
    
    protected class PathCacheLoader extends CacheLoader<PathId,MultiRoute> {
        MultiPathRouting mpr;
        PathCacheLoader(MultiPathRouting mpr) {
            this.mpr = mpr;
        }

        @Override
        public MultiRoute load(PathId rid) {
            return mpr.buildMultiRoute(rid);
        }
    }
    
    private final PathCacheLoader pathCacheLoader = new PathCacheLoader(this);
    protected LoadingCache<PathId,MultiRoute> pathcache;
    
	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		// TODO Auto-generated method stub
		for (LDUpdate update : linkUpdates){
            if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_REMOVED) || update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)) {
                LinkWithCost srcLink = new LinkWithCost(update.getSrc(), update.getSrcPort(), update.getDst(), update.getDstPort(),1);
                LinkWithCost dstLink = srcLink.getInverse();
                if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_REMOVED)){
                    removeLink(srcLink);
                    removeLink(dstLink);
                    clearRoutingCache();
                }
                else if (update.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)){
                    addLink(srcLink);
                    addLink(dstLink);
                }
            }
		}
		computeNetworkDensity();
	}
	
    public void clearRoutingCache(){
        flowcache.invalidateAll();
        pathcache.invalidateAll();
        computeDecision.resetcomputeDecision();
    }
	
    public void removeLink(LinkWithCost link){
    	DatapathId dpid = link.getSrcDpid();
        if( null == dpidLinks.get(dpid)){
            return;
        }
        else{
            dpidLinks.get(dpid).remove(link);
            if( 0 == dpidLinks.get(dpid).size())
                dpidLinks.remove(dpid);
        }
    }
    
    public void addLink(LinkWithCost link){    
    	DatapathId dpid = link.getSrcDpid();
        if (null == dpidLinks.get(dpid)){
            HashSet<LinkWithCost> links = new HashSet<LinkWithCost>();
            links.add(link);
            dpidLinks.put(dpid,links);
        }
        else{
            dpidLinks.get(dpid).add(link);
        }
    }
    
    public MultiRoute buildFlowRoute(FlowId fid){
        DatapathId srcDpid = fid.getSrc();
        DatapathId dstDpid = fid.getDst();
        //OFPort srcPort = fid.getSrcPort();
        //OFPort dstPort = fid.getDstPort();

        MultiRoute routes = null;
        
        try{
            routes = pathcache.get(new PathId(srcDpid,dstDpid));
        }catch (Exception e){
            logger.error("error {}",e.toString());
        }
        /* cause com.google.common.cache.CacheLoader$InvalidCacheLoadException */
        
        if( 0 == routes.getRouteSize())
            return null;
        else
            return routes;
    }
    
    public MultiRoute buildMultiRoute(PathId rid){
        return computeMultiPath(rid);
    }
    
    /*can do well*/
    public void computeNetworkDensity(){
    	int nodeSize = dpidLinks.size();
    	int linkCount = 0;
    	float Pc = (nodeSize*(nodeSize-1))/2;
    	
    	for(Map.Entry<DatapathId, HashSet<LinkWithCost>> e : dpidLinks.entrySet()){
    		HashSet<LinkWithCost> link = e.getValue();
    		linkCount += link.size();
    	}
    	
    	Density = Math.round(((linkCount / 2)/Pc) * 10);
    	//System.out.println("Density: " + Density + " node " + nodeSize + " link " + linkCount/2);
    }
    
    
    /*can do well*/
    public List<LinkWithCost> visitOrder( HashSet<LinkWithCost> visitLink, HashSet<DatapathId> seen){
    	
    	List<LinkWithCost> orderLink = new ArrayList<LinkWithCost> ();
    	
    	for(LinkWithCost l: visitLink){
    		l.initalLinkSize();
    		int count = 0;
    		for(LinkWithCost id: dpidLinks.get(l.getDstDpid())){
    			if(!seen.contains(id.getDstDpid())){
    				count++;
    			}
    		}
    		l.setLinkSize(count); 
    		orderLink.add(l);
    	}
    	
    	Collections.sort(orderLink);
    	
    	/*
    	//show cost after sorting
    	for(LinkWithCost l: orderLink){
    		System.out.println("src = " + l.getSrcDpid() + " dst = " + l.getDstDpid() + " cost = " + l.getLinkSize());
    	}
    	*/
		return orderLink;
    }
    
    public MultiRoute computeMultiPath(PathId rid){
    	DatapathId srcDpid = rid.getSrc();
    	DatapathId dstDpid = rid.getDst();
        MultiRoute routes = new MultiRoute();

        if( srcDpid.equals(dstDpid))
            return routes;
        if( null == dpidLinks.get(srcDpid) || null == dpidLinks.get(dstDpid))
            return routes;
        
        HashMap<DatapathId, HashSet<LinkWithCost>> previous = new HashMap<DatapathId, HashSet<LinkWithCost>>();
        HashMap<DatapathId, HashSet<LinkWithCost>> links = dpidLinks;
        HashMap<DatapathId, Integer> costs = new HashMap<DatapathId, Integer>();
        
        for(DatapathId dpid : links.keySet()){
            costs.put(dpid,Integer.MAX_VALUE);
            previous.put(dpid,new HashSet<LinkWithCost>());
        }
        
        ArrayList <NodeCost> queue = new ArrayList<NodeCost>();
        HashSet<DatapathId> seen = new HashSet<DatapathId>();
        
        queue.add(new NodeCost(dstDpid,0));
        NodeCost node;
        
        while(!queue.isEmpty()){
        	node = queue.get(0);
        	queue.remove(0);
        	if(node.equals(srcDpid))
        		break;
        	
        	Integer cost = node.getCost();
        	DatapathId node_m = node.getDpid();
        	seen.add(node_m);
        	
        	//Link can sorting by node connect 
        	for(LinkWithCost link: visitOrder(links.get(node.getDpid()), seen)){
        		DatapathId node_n = link.getDstDpid();
        		Integer totalCost = cost + link.getCost();
        		if(seen.contains(node_n))
        			continue;
        		
        		if(totalCost < costs.get(node_n)){
        			 costs.put(node_n, totalCost);
        			 NodeCost ndTemp = new NodeCost(node_n,totalCost);
        			 queue.remove(ndTemp);
        			 queue.add(ndTemp);
        		}
        		previous.get(node_m).add(link.getInverse());
        	}
        }
        /*
        for(Map.Entry <DatapathId, HashSet<LinkWithCost>> entrys : previous.entrySet()){
        	System.out.println("Switch MacAddress : " + entrys.getKey()+ "\n");
        	HashSet<LinkWithCost> set = entrys.getValue();
        	for(LinkWithCost s : set){
        		System.out.println("neighbor node : " + s.getSrcDpid() + "  " + s.getDstDpid());
        	}
        }
        */
        LinkedList<NodePortTuple> switchPorts = new LinkedList<NodePortTuple>();
        pathCount = 0;
        Integer hop = 0;
        
        generateMultiPath(routes,srcDpid,dstDpid,dstDpid,previous,switchPorts,costs,hop);
        return routes;
    }
    
    public void generateMultiPath(MultiRoute routes, DatapathId srcDpid, DatapathId dstDpid, DatapathId current, 
    		HashMap<DatapathId, HashSet<LinkWithCost>> previous,LinkedList<NodePortTuple> switchPorts,
    		HashMap<DatapathId, Integer> costs, Integer hop){   
    	
    	//if (pathCount >=ROUTE_LIMITATION)
        //    return ; 
        if( current.equals(srcDpid) && hop <= costs.get(current) + (Density) ){
            pathCount++;
            Path result = new Path(new PathId(srcDpid,dstDpid), new LinkedList<NodePortTuple>(switchPorts));
            routes.addRoute(result);
            return ;
        }
        
        HashSet<LinkWithCost> links = previous.get(current);
        for(LinkWithCost link: links){
        	int count = hop;
        	//System.out.println(link.getDstDpid() + " " + link.getSrcDpid());
        	//System.out.println("count :" + count + " threshold " + (costs.get(link.getSrcDpid()) + (Density)) + "\n");
        	if( count > (costs.get(link.getSrcDpid()) + (Density)) ) continue;
        	
        	NodePortTuple npt = new NodePortTuple(link.getDstDpid(), link.getDstPort());
	        NodePortTuple npt2 = new NodePortTuple(link.getSrcDpid(), link.getSrcPort());
	        switchPorts.addFirst(npt);
	        switchPorts.addFirst(npt2);
	        generateMultiPath(routes,srcDpid, dstDpid, link.getSrcDpid(),previous,switchPorts,costs,hop+1);
	        switchPorts.removeFirst();
	        switchPorts.removeFirst();
        }
        return ;
    }
    
    private void updateLinkCost(DatapathId srcDpid,DatapathId dstDpid,int cost){
        if( null != dpidLinks.get(srcDpid)){
            for(LinkWithCost link: dpidLinks.get(srcDpid)){
                if(link.getSrcDpid().equals(srcDpid)  && link.getDstDpid().equals(dstDpid)){
                    link.setCost(cost);
                    return;
                }
            }
        }
    }
    
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IMultiPathRoutingService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>,IFloodlightService>();
        m.put(IMultiPathRoutingService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		        l.add(IFloodlightProviderService.class);
		        l.add(ITopologyService.class);
		        l.add(IComputeDecisionService.class);
		        //l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        topologyService    = context.getServiceImpl(ITopologyService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        logger = LoggerFactory.getLogger(MultiPathRouting.class);
        computeDecision = context.getServiceImpl(IComputeDecisionService.class);
        dpidLinks = new HashMap<DatapathId, HashSet<LinkWithCost>>();

        flowcache = CacheBuilder.newBuilder().concurrencyLevel(4)
                    .maximumSize(1000L)
                    .build(
                            new CacheLoader<FlowId,MultiRoute>() {
                                public MultiRoute load(FlowId fid) {
                                    return flowCacheLoader.load(fid);
                                }
                            });
        
        pathcache = CacheBuilder.newBuilder().concurrencyLevel(4)
                    .maximumSize(1000L)
                    .build(
                            new CacheLoader<PathId,MultiRoute>() {
                                public MultiRoute load(PathId rid) {
                                    return pathCacheLoader.load(rid);
                                }
                            });
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		topologyService.addListener(this);
	}

	@Override
	public void modifyLinkCost(DatapathId srcDpid, DatapathId dstDpid, short cost) {
		updateLinkCost(srcDpid,dstDpid,cost);
        updateLinkCost(dstDpid,srcDpid,cost);
        clearRoutingCache();
	}

	@Override
	public MultiRoute getRoute(DatapathId srcDpid, OFPort srcPort, DatapathId dstDpid, OFPort dstPort) {
		// Return null the route source and desitnation are the
        // same switchports.
        if (srcDpid.equals(dstDpid) && srcPort.equals(dstPort))
            return null;

        FlowId id = new FlowId(srcDpid,srcPort,dstDpid,dstPort);
        MultiRoute result = null;
        try{
            result = flowcache.get(id);
        }catch (Exception e){
            //logger.error("error {}",e.toString());
        }
        
        if (result == null && srcDpid.getLong() != dstDpid.getLong()) return null;
        return result;
	}
}