package lsi.noc.assessment22;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;

/**
 * Statistician actor processes the output data from producers
 * and outputs minimum, average and maximum durations of communication and 
 * end-to-end latency for each task. For each parameter it outputs a single record, whose
 * fields correspond to tasks and values correspond to relation between calculated data 
 * and the reference data. Updated parameter records are sent on each input record.
*/

@SuppressWarnings("serial")
public class statistician extends TypedAtomicActor {

	protected double[][] accumulator, minimum, maximum;
	protected int[] counter;
	
	protected RecordToken inputRecord, outputRecord;
	protected TypedIOPort input;
	protected TypedIOPort[] output;
	
	// Port count = number of parameters to output
	protected int portCount = 7;
	// Label count = number of tasks to track = number of fields in the output records
	protected int labelCount = 20;

	protected RecordType outputRecordType;
	private String[] parameterLabels;
	protected String[][] labels;
	protected Token[] values;	
	
	protected Double[][] reference;
	
	/**
	 * Constructor instantiates ports, output record types and parameter arrays.
	 * @param container
	 * @param name
	 * @throws NameDuplicationException
	 * @throws IllegalActionException
	 */
	public statistician (CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);
		
		input = new TypedIOPort(this, "input", true, false);
		output = new TypedIOPort[portCount];

		parameterLabels = new String[] {"id", "min_commtime", "avg_commtime", "max_commtime",
											 "min_totaltime", "avg_totaltime", "max_totaltime"};
		labels = new String[portCount][labelCount];
		
		// Labels for "id" record
		for (int l=0; l<portCount; l++)
			for (int i=0; i<labelCount; i++) {
				labels[l][i] = parameterLabels[l] + (i < 10 ? "0" : "") + Integer.toString(i);
			}		
		
		// Types for "id" record
		outputRecordType = new RecordType(labels[0], 
							new Type[] {BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT,
						   				BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT,
						   				BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT,
				   						BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT, BaseType.INT});		
		output[0] = new TypedIOPort(this, "output0", false, true);
		output[0].setTypeEquals(outputRecordType);

		// Labels & types of the other parameter records
		for (int i=1; i<portCount; i++) {
			output[i] = new TypedIOPort(this, "output" + Integer.toString(i), false, true);
			
			outputRecordType = new RecordType(labels[i], 
					new Type[] {BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE,
				   				BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE,
				   				BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE,
				   				BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE, BaseType.DOUBLE});
			
			output[i].setTypeEquals(outputRecordType);
		}

		// Instantiate parameter values
		accumulator = new double[labelCount][2];
		minimum = new double[labelCount][2];
		maximum = new double[labelCount][2];
		counter = new int[labelCount];
	}
	
	/**
	 * Initialises parameter and references values.
	 * @throws IllegalActionException
	 */
	public void initialize() throws IllegalActionException {
		super.initialize();
		
		for (int i=0; i<labelCount; i++) {
			accumulator[i][0] = 0;
			accumulator[i][1] = 0;
			minimum[i][0] = 10000;
			minimum[i][1] = 10000;
			maximum[i][0] = 0;
			maximum[i][1] = 0;
			counter[i] = 0;
		}
		reference = new Double[20][];
		reference[0] = new Double[] {0.00000161, 0.00000171, 0.00000214, 0.00080161, 0.00218632, 0.00590161};
		reference[1] = new Double[] {0.00000158, 0.00000253, 0.00000363, 0.00080158, 0.00080253, 0.00080363};
		reference[2] = new Double[] {0.00000312, 0.00000406, 0.00000515, 0.00080312, 0.00080406, 0.00080515};
		reference[3] = new Double[] {0.00000167, 0.00000183, 0.00000667, 0.00080167, 0.00080183, 0.00080667};
		reference[4] = new Double[] {0.00000310, 0.00000324, 0.00000818, 0.00080310, 0.00080324, 0.00080818};
		reference[5] = new Double[] {0.00000104, 0.00000104, 0.00000104, 0.00100104, 0.00100104, 0.00100104};
		reference[6] = new Double[] {0.00000095, 0.00000103, 0.00000222, 0.00100095, 0.00100103, 0.00100222};
		reference[7] = new Double[] {0.00000142, 0.00000142, 0.00000142, 0.00050142, 0.00071680, 0.00160142};
		reference[8] = new Double[] {0.00000139, 0.00000139, 0.00000139, 0.00040139, 0.00042097, 0.00070139};
		reference[9] = new Double[] {0.00000097, 0.00000097, 0.00000097, 0.00110097, 0.00110097, 0.00110097};
		reference[10] = new Double[] {0.00000064, 0.00000064, 0.00000064, 0.00080064, 0.00110513, 0.00170064};
		reference[11] = new Double[] {0.00000064, 0.00000067, 0.00000116, 0.00120064, 0.00124426, 0.00170064};
		reference[12] = new Double[] {0.00000061, 0.00000064, 0.00000115, 0.00040061, 0.00040064, 0.00040115};
		reference[13] = new Double[] {0.00000064, 0.00000110, 0.00000166, 0.00040113, 0.00043040, 0.00080064};
		reference[14] = new Double[] {0.00000070, 0.00000070, 0.00000070, 0.00090070, 0.00104274, 0.00160070};
		reference[15] = new Double[] {0.00000070, 0.00000070, 0.00000070, 0.00050070, 0.00050070, 0.00050070};
		reference[16] = new Double[] {0.00000520, 0.00000520, 0.00000520, 0.00550520, 0.00550520, 0.00550520};
		reference[17] = new Double[] {0.00000529, 0.00000529, 0.00000529, 0.00550529, 0.00559329, 0.00630529};
		reference[18] = new Double[] {0.00000520, 0.00000520, 0.00000520, 0.00550520, 0.00550520, 0.00550520};
		reference[19] = new Double[] {0.00000523, 0.00000523, 0.00000523, 0.00550523, 0.00550523, 0.00550523};

	}

	/**
	 * fire() processes the input data and send the resulting parameter 
	 * records to the output ports. 
	 * @throws IllegalActionException
	 */
	public void fire() throws IllegalActionException {
		super.fire();
		
		if (input.hasToken(0)) {
			inputRecord = (RecordToken)input.get(0);
			int id = ((IntToken)inputRecord.get("id")).intValue();
			
			// Communication latency
			double latency = ((DoubleToken)inputRecord.get("commfinishtime")).doubleValue() - 
							 ((DoubleToken)inputRecord.get("commstarttime")).doubleValue();
			if (latency < minimum[id][0])
				minimum[id][0] = latency;
			if (latency > maximum[id][0])
				maximum[id][0] = latency;
			accumulator[id][0] = accumulator[id][0] + latency;
			
			// End-to-end latency
			latency = ((DoubleToken)inputRecord.get("commfinishtime")).doubleValue() - 
					 ((DoubleToken)inputRecord.get("releasetime")).doubleValue() - 
					(((DoubleToken)inputRecord.get("commstarttime")).doubleValue() - // Delay between end of computation and start of communication
					 ((DoubleToken)inputRecord.get("compfinishtime")).doubleValue());
			if (latency < minimum[id][1])
				minimum[id][1] = latency;
			if (latency > maximum[id][1])
				maximum[id][1] = latency;
			accumulator[id][1] = accumulator[id][1] + latency;
			
			// Input record counter
			counter[id]++;
			
			// Send records for all parameters
			values = new Token[labelCount];
			
			for (int i=0; i<labelCount; i++)
				values[i] = new IntToken(i);
			output[0].send(0, new RecordToken(labels[0], values));
			
			for (int i=0; i<labelCount; i++)
				values[i] = new DoubleToken(Math.abs(minimum[i][0] - reference[i][0]) * 100 / reference[i][0]);
			output[1].send(0, new RecordToken(labels[1], values));
			
			for (int i=0; i<labelCount; i++)
				values[i] = new DoubleToken(Math.abs(accumulator[i][0] / counter[i] - reference[i][1]) * 100 / reference[i][1]);
			output[2].send(0, new RecordToken(labels[2], values));
			
			for (int i=0; i<labelCount; i++)
				values[i] = new DoubleToken(Math.abs(maximum[i][0] - reference[i][2]) * 100 / reference[i][2]);
			output[3].send(0, new RecordToken(labels[3], values));
			
			for (int i=0; i<labelCount; i++)
				values[i] = new DoubleToken(Math.abs(minimum[i][1] - reference[i][3]) * 100 / reference[i][3]);
			output[4].send(0, new RecordToken(labels[4], values));
			
			for (int i=0; i<labelCount; i++)
				values[i] = new DoubleToken(Math.abs(accumulator[i][1] / counter[i] - reference[i][4]) * 100 / reference[i][4]);
			output[5].send(0, new RecordToken(labels[5], values));
			
			for (int i=0; i<labelCount; i++)
				values[i] = new DoubleToken(Math.abs(maximum[i][1] - reference[i][5]) * 100 / reference[i][5]);
			output[6].send(0, new RecordToken(labels[6], values));
		}
	}

}
