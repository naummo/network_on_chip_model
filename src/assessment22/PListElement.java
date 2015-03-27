package lsi.noc.assessment22;

import java.util.Vector;
import ptolemy.data.RecordToken;

public class PListElement {	
	// Transmission packet
	public RecordToken packet;
	// Interference set containing references to all elements of 
	// plist that can interfere with the element
	public Vector<PListElement> interference;
	public boolean active;
	// Time active
	public double ta;
	public int remainingPayload;
	// The number of hops between source and destination
	public int numberOfHops;
	// The hop at which the leading packet was buffered, when
	// the message got suspended
	public int lastHop;
	// Boolean showing whether the message has been delivered or not 
	public boolean delivered;

	public PListElement() {
		interference = new Vector<PListElement>();
		active = false;
		ta = 0.0;
		remainingPayload = 0;
		numberOfHops = 0;
		lastHop = 0;
		delivered = false;
		
	}
}
