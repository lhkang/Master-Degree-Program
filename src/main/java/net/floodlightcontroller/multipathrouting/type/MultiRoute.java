package net.floodlightcontroller.multipathrouting.type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.routing.Path;

public class MultiRoute {
	 protected int routeSize;
	 protected ArrayList<Path> routes;
	 protected Set<Integer> location; /*record congestion paths*/
	 boolean Flag;
	 
	 public MultiRoute(){
		 routeSize = 0;
		 routes = new ArrayList<Path>();
		 location = new HashSet<Integer>();
		 Flag = false;
	 }
	 
	 public Path getRoute(int routeCount){
	     return routes.get(routeCount);
	 }
	 
	 public ArrayList<Path> getAllRoute(){
		 return routes;
	 }
	 
	 public int getRouteSize(){
	     return routeSize;    
	 }
	
	public void addRoute(Path route){
	     routeSize++;
	     routes.add(route);
	}
	
	public void initialtion(){
		location = new HashSet<Integer>();
		Flag = false;
	}
	
	public void CongestionFlag(boolean Flag){
		this.Flag = Flag;
	}
	
	public void addlocation(Integer i){
		location.add(i);
	}
	
	public Set<Integer> getLocation(){
		return location;
	}
	
	public boolean getFlag(){
		return Flag;
	}
	
	public boolean isMultiple(){	
		if(routeSize >= 2){
			return true;
		}else{
			return false;
		}
	}

}

