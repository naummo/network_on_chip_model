package lsi.noc.application.greenpringle;

import java.util.Vector;

import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.DoubleToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import lsi.noc.application.PlatformCommunicationInterface;

/**
 * This producer is based on the one created by Leandro Soares Indrusiak:
 * it accepts tasks for execution, executes them for the specified period of time
 * and on completion it sends their messages to the output port.
 */

@SuppressWarnings("serial")
public class StandaloneVCProducerCBwithPriorityNonpreemptiveScheduler extends PlatformCommunicationInterface {

	protected Time taskReadyTime;
	TypedIOPort trigger, data_out; // port declarations
	protected Vector<RecordToken> taskBuffer; 
	protected boolean taskBusy;
	protected RecordToken runningTask;
    protected String[] labels;

    /**
     * Constructor instantiates ports.
     * @param container
     * @param name
     * @throws NameDuplicationException
     * @throws IllegalActionException
     */
	public StandaloneVCProducerCBwithPriorityNonpreemptiveScheduler(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException  {
		super(container, name);

		// port instantiations
		trigger = new TypedIOPort(this, "trigger", true, false); // receives tokens representing tasks triggered to execute
		data_out = new TypedIOPort(this, "data_out", false, true);
		
		// Labels and types for a packet 
        labels = new String[10];
        Type[] types = new Type[10];
        
        labels[0] = "x";
        labels[1] = "y";
        labels[2] = "size";
        labels[3] = "priority";
        labels[4] = "id";
        labels[5] = "releasetime";
        labels[6] = "period";
        labels[7] = "comptime";
        labels[8] = "compfinishtime";
        labels[9] = "commfinishtime";

        types[0] = BaseType.INT;
        types[1] = BaseType.INT;
        types[2] = BaseType.INT;
        types[3] = BaseType.INT;
        types[4] = BaseType.INT;
        types[5] = BaseType.DOUBLE;
        types[6] = BaseType.DOUBLE;
        types[7] = BaseType.DOUBLE;
        types[8] = BaseType.DOUBLE;
        types[9] = BaseType.DOUBLE;

        RecordType declaredType = new RecordType(labels, types);
        data_out.setTypeEquals(declaredType);
	}

	/**
	 * Initialize() initialises variables.
	 */
	public void initialize() throws IllegalActionException {
		super.initialize();

		taskBusy=false;
		runningTask = null;

		taskBuffer=new Vector<RecordToken>();
	}

	/**
	 * fire() accepts incoming records, buffers them,
	 * executes for the period of time specified by records and
	 * sends their packets to the output port.
	 */
	public void fire() throws IllegalActionException {
		Time ctime = getDirector().getModelTime();
		
		// deals with new tasks
		while(trigger.hasToken(0)){
			
			Token newTask = trigger.get(0);
			if(newTask instanceof RecordToken){
				
				RecordToken task = (RecordToken)newTask;
				
				// adds new task to scheduler process list at the respective priority level
				addToTaskBuffer(task);				
			}			
		}
		
		if(taskBusy){ // if CPU busy with tasks, check whether current task has terminated

			// Time error
			if(ctime.compareTo(taskReadyTime)>=0){
				
				taskBusy= false;
								
				// updates "compfinishtime" field of the task representation (or adds one if it hasn't)
				String[] labels = new String[1];
				Token[] values = new Token[1];
				
				labels[0] = "compfinishtime";
				values[0] = new DoubleToken(ctime.getDoubleValue());
				RecordToken finishTime = new RecordToken(labels, values);
				
				runningTask = RecordToken.merge(finishTime, runningTask);
				
				// packet transmission
				data_out.send(0, runningTask);				
			}	
		}
		
		if(!taskBusy){ // if CPU idle, schedule the highest priority task from the queues
			if(!taskBuffer.isEmpty()){ // if there are queued tasks at this priority
				
				runningTask = (RecordToken)taskBuffer.remove(0);
				
				taskBusy=true;
				
				double comptime = ((DoubleToken)runningTask.get("comptime")).doubleValue();
				
		        taskReadyTime = ctime.add(comptime);
				getDirector().fireAt(this, taskReadyTime);
			}
		} 
	}

	/**
	 * addToTaskBuffer() buffers task for a later processing.
	 * @param packet
	 */
	protected void addToTaskBuffer(RecordToken packet) {
		taskBuffer.addElement(packet);		
	}
}
