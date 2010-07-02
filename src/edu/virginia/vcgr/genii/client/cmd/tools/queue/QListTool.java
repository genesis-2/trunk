package edu.virginia.vcgr.genii.client.cmd.tools.queue;

import java.util.Collection;
import java.util.Iterator;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.cmd.tools.BaseGridTool;
import edu.virginia.vcgr.genii.client.queue.QueueManipulator;
import edu.virginia.vcgr.genii.client.queue.ReducedJobInformation;
import edu.virginia.vcgr.genii.client.security.credentials.identity.Identity;
import edu.virginia.vcgr.genii.client.gpath.*;

public class QListTool extends BaseGridTool
{
	static final private String _DESCRIPTION =
		"Lists all of the jobs currently running in this queue.";
	static final private String _USAGE =
		"qlist <queue-path>";
	
	public QListTool()
	{
		super(_DESCRIPTION, _USAGE, false);
	}
	
	@Override
	protected int runCommand() throws Throwable
	{
		GeniiPath gPath = new GeniiPath(getArgument(0));
		if(gPath.pathType() != GeniiPathType.Grid)
			throw new InvalidToolUsageException("<queue-path> must be a grid path. ");
		QueueManipulator manipulator = new QueueManipulator(gPath.path());
		Iterator<ReducedJobInformation> jobs = manipulator.list();
		
		printHeader();
		while (jobs.hasNext())
		{
			printJobInformation(jobs.next());
		}
		
		return 0;
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() != 1)
			throw new InvalidToolUsageException();
	}
	
	private void printHeader()
	{
		stdout.printf("%1$-36s   %2$-8s   %3$-30s\n", "Ticket", "State", 
			"Owner Identities");
	}

	private void printJobInformation(ReducedJobInformation jobInfo)
	{
		stdout.printf("%1$-36s   %2$-8s", jobInfo.getTicket(), jobInfo.getJobState());
		Collection<Identity> owners = jobInfo.getOwners();
		if (owners.size() <= 0)
			stdout.printf("\n");
		else
		{
			boolean first = true;
			for (Identity identity : owners)
			{
				if (first)
				{
					first = false;
					stdout.printf("   %1$-30s\n", identity);
				} else
				{
					stdout.printf("%1$-50s%2$-30s\n", "", identity);
				}
			}
		}
	}
}