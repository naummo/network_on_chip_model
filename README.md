# network_on_chip_model
This is a source code developed by Naums Mogers for an open assessment in the module "Embedded Software Design & Implementation" at the University of York.
It is a Ptolemy II Java simulation of a multiprocessor platform with 16 homogeneous processing elements (PEs) interconnected by a wormhole Network-on-Chip (NoC) in a 4x4 mesh topology. 

Source code documentation
-------------------------------
StandaloneVCProducerCBwithPriorityNonpreemptiveScheduler class:
Based on the same class from the cycle-accurate model, this class is responsible for receiving
new tasks from Subscribers, simulating their computation and sending a message on the
completion of execution. The time it took to complete the computation is added to the message
in (this, fire, 123-127).

StandaloneVCConsumerCBwithPriority class:
Based on the same class from the cycle-accurate model, this class is responsible for receiving
messages, updating them with the time at which they arrive (this, fire, 67-71) and passing the
message forward to Publishers.

interConnect class:
This actor implements the interconnection between all producers and consumers. It maintains
the list of messages in transmission, where each message is of class PListElement defined in
PListElement.java. On fire() event (interConnect, fire, 123) the actor first processeses all the new
packets awaiting sending, and then updates plist. Processing new packets involves:
- Integrating the input packet into a PListELement object, which also contains other
information useful for simulating packet transmission such as lastHop variable containing
the number of the hop along the path, which was reached by the leading packet during its
active time.This value is maintained in the updatePList and introduces a low-cost cycle-level
precision into this transaction-level simulation.
- Recording communication start time (interConnect, processNewPackets, 150).
- Computing the Manhattan distance to the destination (interConnect, processNewPackets,
161).
- Constructing the interference set for the new message and updating interference sets for
existing messages in plist (interConnect, processNewPackets, 167-183). Assessing
interference involves deciding whether two routes may overlap - this logic is implemented
in (interConnect, overlap, 300).
- Inserting the new message into plist according to its priority, thus maintaining plist
priority-sort order (interConnect, processNewPackets, 186).
Updating plist happens in (interConnect, updatePList, 198). Of interest here are the following
functionalities:
- Calculating remaining payload (interConnect, updatePList, 214-215). For this I use sentFlits()
function (interConnect, sentFlits, 356), which returns the number of flits that have reached
their destination. This is calculated using values numberOfHops and lastHop (interConnect,
sentFlits, 361-362), which contain Manhattan distance from source to destination and the
hop of the leading packet.
- When the message has reached its destination, its record boolean delivered is set to true
(interConnect, updatePList, 229) and the record is removed. The records, which reference
this record in their interference lists will check for the delivered boolean and delete the
record if needed (interConnect, updatePList, 243, 269).
- If the task becomes active, we schedule next firing to the approximate time of
transmission completion (interConnect, updatePList, 287). For that noLoadLatency() function
was implemented (interConnect, noLoadLatency, 371), which also takes advantage of the
precision introduced by the lastHop field of the packet.

Reporter and statistician classes:
These classes are used to evaluate packet latencies.
