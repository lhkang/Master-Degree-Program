package net.floodlightcontroller.multipathrouting.type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.routing.Path;

public class MultiRoute {
	 protected int routeSize;
	 protected ArrayList<Path> routes;
	 protected Set<Integer> Congestion_Mark; /*record congestion paths*/
	 
	 public MultiRoute(){
		 routeSize = 0;
		 routes = new ArrayList<Path>();
		 Congestion_Mark = new HashSet<Integer>();
	 }
	 
	 public Path getRoute(int i){
	     return routes.get(i);
	 }
	 
	 public Path getFristRoute(){
	     return routes.get(0);
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
	
 	public void remove_first_element() {
 		routes.remove(0);
 	}
 	
	public void set_Congestion_Mark(Integer i){
		Congestion_Mark.add(i);
	}
	
	public Set<Integer> get_Congestion_Mark(){
		return Congestion_Mark;
	}

}

