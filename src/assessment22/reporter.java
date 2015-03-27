package lsi.noc.assessment22;

import ptolemy.actor.TypedCompositeActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.TypedIORelation;
import ptolemy.actor.lib.gui.Display;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.data.RecordToken;

/**
 * Historian actor passes the data to the Statistician and
 * displays processed results on a display.
*/

@SuppressWarnings("serial")
public class reporter extends TypedCompositeActor {
	
	protected statistician myStatistician;
	protected RecordToken inputRecord, outputRecord;
	protected TypedIOPort input;
	
	protected Display disp;
	protected TypedIORelation dispRelation, inputRelation;
	
	public reporter (CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);

		input = new TypedIOPort(this, "input", true, false);
		
		myStatistician = new statistician(this, "myStatistician");
		
		inputRelation = new TypedIORelation(this, "inputRelation");
		input.link(inputRelation);
		myStatistician.input.link(inputRelation);
		
		disp = new Display(this, "myDisplay");
		
		dispRelation = new TypedIORelation(this, "dispRelation");
		for (int i = 0; i < 7; i++)
			myStatistician.output[i].link(dispRelation);
		disp.input.link(dispRelation);
	}
	
	public void initialize() throws IllegalActionException {
		super.initialize();	
		
		disp.initialize();
	}
}
