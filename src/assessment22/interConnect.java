package lsi.noc.assessment22;

import java.util.Vector;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/**
 * @version 1.0
 * @author Naums Mogers
 *
 * interConnect actor implements a transaction-level simulation of 
 * the task communication on the NoC. Algorithms are based on [1].
 * 
 * References:
 * [1] Leandro Soares Indrusiak, James Harbin, Osmar Marchi dos Santos: Fast Simulation of Networks-on-Chip
 *     with Priority-Preemptive Arbitration. EMBS Module website: http://www-course.cs.york.ac.uk/embs/
*/

@SuppressWarnings("serial")
public class interConnect extends TypedAtomicActor {
	
	// An input and output port for each PE in the mesh
	protected TypedIOPort[] input;
	protected TypedIOPort[] output;
	
	// Priority-sorted resizable vector of packets
	protected Vector<PListElement> plist;
	
	// XY coordinates array for converting to different PE indexing mode
	// Second indexing mode is useful to choose output/input ports
	protected int[][] xyCoors;
	
	// Period
	protected Parameter periodParameter;
	protected double period; 	
	// Arbitration cycles - how much cycles does it take for a packet
	// to be processed by a single router (hop)
	protected int arbitrationCycles = 3;
	// Same as arbitration cycles, but in seconds
	protected double routerLatency;
	
	// Variables for specifying output port type
	protected String[] labels;
	protected Type[] types;
	protected RecordType outputPacketType;
    
	/**
	 * Constructor sets up parameters and ports
	 * @param container
	 * @param name
	 * @throws NameDuplicationException
	 * @throws IllegalActionException
	 */
	public interConnect(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);

		periodParameter = new Parameter(this, "Period");
		periodParameter.setTypeEquals(BaseType.DOUBLE);
		periodParameter.setExpression("period");
		
		// Output record fields
		labels = new String[] {"id", "priority", "size", "src_x", "src_y", "x", "y", "period",  
							   "releasetime", "comptime", "compfinishtime", "commfinishtime", "commstarttime"};
		// Output record field types
	    types = new Type[] {BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT, 
	    					BaseType.INT, BaseType.INT, BaseType.INT, BaseType.DOUBLE,
    						BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, 
    						BaseType.DOUBLE, BaseType.DOUBLE };
	    // Output record type
	    outputPacketType = new RecordType(labels, types);
		
		input = new TypedIOPort[16];
		output = new TypedIOPort[16];
		for(int i=0; i<16; i++) {
			input[i]= new TypedIOPort(this, "input" + Integer.toString(i), true, false);
			output[i]= new TypedIOPort(this, "output" + Integer.toString(i), false, true);
			output[i].setTypeEquals(outputPacketType);
		}
	}
	
	/**
	 * Initialize() instantiates variables
	 * @throws IllegalActionException
	 */
	public void initialize() throws IllegalActionException {
		super.initialize();

		// Construct an empty plist
		plist = new Vector<PListElement>();
		
		xyCoors = new int[][] {{0,0}, {0,1}, {0,2}, {0,3}, 
							   {1,0}, {1,1}, {1,2}, {1,3}, 
							   {2,0}, {2,1}, {2,2}, {2,3}, 
							   {3,0}, {3,1}, {3,2}, {3,3}};

		// Read period value from the environment
		period = ((DoubleToken)periodParameter.getToken()).doubleValue();
		
		// Time for a packet to pass a single router
		routerLatency = period * arbitrationCycles;

	}

	/**
	 * Fire() is called when a new packet is arrived or
     * on schedule.
	 * @throws IllegalActionException
	 */
	public void fire() throws IllegalActionException {
		processNewPackets();
		// Doesn't matter how many packets we received, we can update just once
		updatePList();
	}

	/**
	 * processNewPackets() checks all incoming ports and adds new messages
	 * to the pList, while maintaining its priority-sorting order.
	 * It also updates respective interference sets by checking if the new message
	 * route overlaps with any of the existing routes.
	 * @throws IllegalActionException
	 */
	protected void processNewPackets() throws IllegalActionException {
		RecordToken inputPacket, additional_fields;
		PListElement newElement, currentElement;
		String[] labels;
		Token[] values;
		
		// Check all input ports
		for (int i=0; i < 16; i++) {
			if (input[i].hasToken(0)) {
				inputPacket = (RecordToken)input[i].get(0);
			    
				// Add new fields into the packet representing the time the packed arrived 
				// (the communication has started) and where it arrived from in XY coordinates.
				labels = new String[] {"commstarttime", "src_x", "src_y"};
				values = new Token[] {new DoubleToken(this.getDirector().getModelTime().getDoubleValue()),
									  new IntToken(xyCoors[i][0]),
						   	    	  new IntToken(xyCoors[i][1])};
				additional_fields = new RecordToken(labels, values);
				inputPacket = RecordToken.merge(additional_fields, inputPacket);
				
				// Create a new pList element
				newElement = new PListElement();
				// A packet to transfer
				newElement.packet = inputPacket;
				// Number of hops (routers) between source and destination
				newElement.numberOfHops = manhattanDistance(newElement);
				// Initially remaining payload is the full payload, i.e. size
				newElement.remainingPayload = ((IntToken)newElement.packet.get("size")).intValue();
				
				// For all elements in pList, check if they will interfere with
				// the new element, or if the element will interfere with them
				for (int l=0; l < plist.size(); l++) {
					
					currentElement = (PListElement)plist.elementAt(l);
					
					// If part of their routes overlap
					if (overlap(newElement, currentElement)) {
						
						if (((IntToken)newElement.packet.get("priority")).intValue() >
							((IntToken)currentElement.packet.get("priority")).intValue())
							// Priority of the new element is lower, so element L will
							// interfere with the new element
							newElement.interference.add(currentElement);
						else
							// Priority of the new element is bigger, so it will interfere
							// with element L
							currentElement.interference.add(newElement);
					}
				}
				// Add new element to plist while maintaining the priority sort order
				plist.insertElementAt(newElement, sortedIndex(newElement));
			}
		}
	}
	
	/**
	 * updatePList() maintains packet status during the communication time.
     * It controls which messages should be active or inactive and it records
     * how much time it would take for a message to get to destination based on
     * the route and interference.
	 * @throws IllegalActionException
	 */
	protected void updatePList() throws IllegalActionException {
		PListElement currentElement, interferingElement;
		int destination;
		int hopsVisited;
		boolean noneActive;
		Time ctime_raw = this.getDirector().getModelTime();
		Double ctime = ctime_raw.getDoubleValue();
		
		// Iterate through all elements of the pList
		for (int i = 0; i < plist.size(); i++) {
			currentElement = (PListElement)plist.elementAt(i);
			if (currentElement.active) {
				// The element is currently active
				
				// Update how many packets have been fully transmitted during
				// the active time
				currentElement.remainingPayload = currentElement.remainingPayload -
													sentFlits(currentElement);
				// The number of hops that could be visited by the leading packet 
				// during the active time
				hopsVisited = (int)Math.ceil((ctime - currentElement.ta) / routerLatency);
				// The hop at which the leading packet currently is buffered
				currentElement.lastHop = Math.min(
												currentElement.numberOfHops,
												currentElement.lastHop + hopsVisited);
				currentElement.ta = ctime;
				
				if (currentElement.remainingPayload == 0) {
					// All packets delivered - remove the element from pList
					// and mark that it is delivered so that those elements, which contain
					// it in their interference sets can get rid of it 
					currentElement.delivered = true;
					plist.remove(i);					
					
					destination = ((IntToken)currentElement.packet.get("x")).intValue() * 4 +
								  ((IntToken)currentElement.packet.get("y")).intValue();					
					output[destination].send(0, currentElement.packet);		
				}
				else {
					// Message is still not fully delivered
					// Check if any of the higher-priority interfering messages have changed 
					// their status and whether it affect the current element
					for (int l = 0; l < currentElement.interference.size(); l++) {
						interferingElement = (PListElement)currentElement.interference.elementAt(l);
						
						if (interferingElement.delivered) {
							// The message is already delivered, so remove it from the interference set
							currentElement.interference.remove(l);
							l--;
						}
						else
							if (interferingElement.active) {
								// The higher-priority message is active, so it preempts the current message
								currentElement.active = false;
								// Update the current progress in terms of hops
								hopsVisited = (int)Math.ceil((ctime - currentElement.ta) / routerLatency);
								currentElement.lastHop = Math.min(
																currentElement.numberOfHops,
																currentElement.lastHop + hopsVisited);
								// The message was preempted, so we don't care if any other higher-priority
								// messages became active
								break;
							}
					}
				}
			}
			else {
				// The element is not active - check if it should be
				noneActive = true;
				for (int l = 0; l < currentElement.interference.size(); l++) {
					interferingElement = (PListElement)currentElement.interference.elementAt(l);
					if (interferingElement.delivered) {
						// The message is already delivered, so remove it from the interference set
						currentElement.interference.remove(l);
						l--;
					}
					else
						if (interferingElement.active) {
							// At least one of the higher-priority messages became active
							noneActive = false;
							break;
						}
				}
				if (noneActive) {
					// None of the higher-priority messages are active, so get to work
					currentElement.active = true;
					currentElement.ta = ctime;

					// Schedule update to the time when we expect the message to be fully delivered 
					getDirector().fireAt(this, ctime_raw.add(noLoadLatency(currentElement)));
				}
			}
		}
	}
	
	/**
	 * Overlap() checks if the routes of two elements overlap at least partly and hence
     * can interfere with each other.
	 * @param firstElement
	 * @param secondElement
	 * @return boolean representing overlapping status
	 */
	protected boolean overlap(PListElement firstElement, PListElement secondElement) {
		int src1x = ((IntToken)firstElement.packet.get("src_x")).intValue();
		int src1y = ((IntToken)firstElement.packet.get("src_y")).intValue();
		int src2x = ((IntToken)secondElement.packet.get("src_x")).intValue();
		int src2y = ((IntToken)secondElement.packet.get("src_y")).intValue();
		int dst1x = ((IntToken)firstElement.packet.get("x")).intValue();
		int dst1y = ((IntToken)firstElement.packet.get("y")).intValue();
		int dst2x = ((IntToken)secondElement.packet.get("x")).intValue();
		int dst2y = ((IntToken)secondElement.packet.get("y")).intValue();
			
		// Route overlap occurs when two routes have same direction horizontally or
		// vertically and share a part of the path, i.e. share same links between routers
		if (((src1y == src2y) &&						// Routes start from the same horizontal line
			 ((src1x > dst1x) && (src2x > dst2x) ||		// Routed in the same direction horizontally
		      (src1x < dst1x) && (src2x < dst2x)) &&	// Routed in the same direction horizontally
		     ((src1x > dst2x) && (src2x > dst1x) ||		// Overlapping restriction in the positive horizontal direction
 		      (src1x < dst2x) && (src2x < dst1x))) ||	// Overlapping restriction in the negative horizontal direction
			((dst1x == dst2x) &&						// Routes start from the same vertical line
		     ((src1y > dst1y) && (src2y > dst2y) ||		// Routed in the same direction vertically
		      (src1y < dst1y) && (src2y < dst2y)) &&	// Routed in the same direction vertically
		     ((src1y > dst2y) && (src2y > dst1y) ||		// Overlapping restriction in the positive vertical direction
		      (src1y < dst2y) && (src2y < dst1y))))		// Overlapping restriction in the negative vertical direction
		    return true;
		return false;
	}
	
	/**
	 * sortedIndex() finds a position for the new element
	 * in the pList so that a priority-sorted order is maintained.
	 * @param newElement
	 * @return index of the correct position
	 */
	protected int sortedIndex(PListElement newElement) {
		PListElement currentElement;
		
		for (int i = 0; i < plist.size(); i++) {
			currentElement = (PListElement)plist.elementAt(i);
			
			if (((IntToken)newElement.packet.get("priority")).intValue() <
				((IntToken)currentElement.packet.get("priority")).intValue()) {
				// The priority of newElement is higher than that of the currentElement
				// so return the index of the currentElement
				return i;
			}			
		}
		// The new element has the least priority, so its correct
		// position in pList is the end
		return plist.size();
	}
	
	/**
	 * sentFlits() calculates how many packets have reached the destination
	 * during the active time.
	 * @param plistElement
	 * @return integer number of packets
	 */
	protected int sentFlits(PListElement plistElement) {
		// A period of activity
		double timeElapsed = this.getDirector().getModelTime().getDoubleValue() - plistElement.ta;
		
		// The number of flits that have reached the destination
		return Math.min(plistElement.remainingPayload, (int)((Math.floor(timeElapsed / routerLatency)) - 
							   Math.floor((plistElement.numberOfHops - plistElement.lastHop) * routerLatency) + 1));		
	}
	
	/**
	 * noLoadLatency() returns the time required for all remaining packages 
	 * of the message to reach the destination.
	 * @param plistElement
	 * @return double value representing time
	 */
	protected double noLoadLatency(PListElement plistElement) {
		return (plistElement.numberOfHops - plistElement.lastHop + plistElement.remainingPayload - 1) * routerLatency;	
	}
	
	/**
	 * manhattanDistance() returns the number of hops between
	 * message source and destination.
	 * @param plistElement
	 * @return integer number of hops
	 */
	protected int manhattanDistance(PListElement plistElement) {
		return Math.abs(((IntToken)plistElement.packet.get("src_x")).intValue() - 
					    ((IntToken)plistElement.packet.get("x")).intValue()) +
			   Math.abs(((IntToken)plistElement.packet.get("src_y")).intValue() - 
					    ((IntToken)plistElement.packet.get("y")).intValue());
	}
}
