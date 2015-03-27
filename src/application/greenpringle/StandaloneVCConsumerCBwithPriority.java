package lsi.noc.application.greenpringle;

import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.DoubleToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import lsi.noc.application.PlatformCommunicationInterface;

/**
 * Consumer receives packets, updates their arrival times and 
 * sends them to the output port.
 */

@SuppressWarnings("serial")
public class StandaloneVCConsumerCBwithPriority extends PlatformCommunicationInterface{

	public TypedIOPort data_in; // received flits
	public TypedIOPort packet_out; // packet received notification
	
	public Parameter periodParameter;
	protected double period;
	
	public StandaloneVCConsumerCBwithPriority(CompositeEntity container,
			String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);
		
		//port instantiations
		packet_out   = new TypedIOPort(this, "packet_out", false, true);
        data_in  = new TypedIOPort(this, "data_in", true, false);
        
        periodParameter = new Parameter(this, "Period");
        periodParameter.setTypeEquals(BaseType.DOUBLE);
        periodParameter.setExpression("period");
        
	}
	
	/**
	 * initialize() gets period value.
	 */
    public void initialize() throws IllegalActionException {
        super.initialize();

        period = ((DoubleToken)periodParameter.getToken()).doubleValue();
    }
	
	/**
	 * fire() receives incoming record and injects their arrival
	 * time as communication finish time. The result is sent to
	 * the output port
	 */
    public void fire() throws IllegalActionException {
    	Time ctime = getDirector().getModelTime();
       
        if (data_in.hasToken(0)) {
    		RecordToken inputRecord = (RecordToken)data_in.get(0);
     			
			String[] labels = new String[1];
			Token[] values = new Token[1];
			
			labels[0] = "commfinishtime";
			values[0] = new DoubleToken(ctime.getDoubleValue()+period); // reception ends at the end of the clock cycle
			RecordToken finishTime = new RecordToken(labels, values); 
			
			inputRecord = RecordToken.merge(finishTime, inputRecord); // add the communication finish time to the RecordToken
 			
			packet_out.send(0, inputRecord); // sends out the packet received notification 
        }
    }
    
	public void pruneDependencies() {
		super.pruneDependencies();

       	removeDependency(packet_out, data_in);
	}
}
