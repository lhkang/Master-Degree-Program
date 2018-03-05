package net.floodlightcontroller.test;

import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

public class Container {
	long queueId;
	U64 txBytes;
	U64 txPackets; 	
	OFPort portNo;
	
	public Container(OFPort ofPort,long queueId,U64 txBytes,U64 txPackets){
		this.portNo = ofPort;
		this.queueId = queueId;
		this.txBytes = txBytes;
		this.txPackets = txPackets;
	}
	
	public int getportNo(){
		return portNo.getPortNumber();
	}
	
	public long getQueId(){
		return queueId;
	}
	
	public long getTxb(){
		return txBytes.getValue();
	}
	
	public long getPac(){
		return txPackets.getValue();
	}
	
};