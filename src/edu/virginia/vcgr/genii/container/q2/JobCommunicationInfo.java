package edu.virginia.vcgr.genii.container.q2;

public class JobCommunicationInfo
{
	private long _jobID;
	private long _besID;
	
	public JobCommunicationInfo(long jobID, long besID)
	{
		_jobID = jobID;
		_besID = besID;
	}
	
	public long getJobID()
	{
		return _jobID;
	}
	
	public long getBESID()
	{
		return _besID;
	}
}