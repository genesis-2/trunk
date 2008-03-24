package edu.virginia.vcgr.genii.container.bes.activity;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.bes.factory.ActivityStateEnumeration;
import org.ggf.jsdl.JobDefinition_Type;
import org.morgan.util.io.StreamUtils;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.bes.ActivityState;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.naming.EPRUtils;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.security.GenesisIISecurityException;
import edu.virginia.vcgr.genii.client.security.gamlauthz.identity.Identity;
import edu.virginia.vcgr.genii.client.ser.DBSerializer;
import edu.virginia.vcgr.genii.container.bes.BES;
import edu.virginia.vcgr.genii.container.bes.BESPolicyListener;
import edu.virginia.vcgr.genii.container.bes.execution.ContinuableExecutionException;
import edu.virginia.vcgr.genii.container.bes.execution.ExecutionContext;
import edu.virginia.vcgr.genii.container.bes.execution.ExecutionException;
import edu.virginia.vcgr.genii.container.bes.execution.ExecutionPhase;
import edu.virginia.vcgr.genii.container.bes.execution.SuspendableExecutionPhase;
import edu.virginia.vcgr.genii.container.bes.execution.TerminateableExecutionPhase;
import edu.virginia.vcgr.genii.container.context.WorkingContext;
import edu.virginia.vcgr.genii.container.db.DatabaseConnectionPool;
import edu.virginia.vcgr.genii.container.q2.QueueSecurity;

public class BESActivity implements Closeable
{
	static private Log _logger = LogFactory.getLog(BESActivity.class);
	
	private DatabaseConnectionPool _connectionPool;
	
	private BES _bes;
	private PolicyListener _policyListener;
	private String _activityid;
	private ActivityState _state;
	private boolean _suspendRequested;
	private boolean _terminateRequested;
	private File _activityCWD;
	private Vector<ExecutionPhase> _executionPlan;
	private int _nextPhase;
	private String _activityServiceName;
	private String _jobName;
	private ActivityRunner _runner;
	
	public BESActivity(DatabaseConnectionPool connectionPool,
		BES bes, String activityid, 
		ActivityState state, File activityCWD, 
		Vector<ExecutionPhase> executionPlan, int nextPhase, 
		String activityServiceName, String jobName,
		boolean suspendRequested, boolean terminateRequested)
	{
		_connectionPool = connectionPool;
		
		_bes = bes;
		_activityid = activityid;
		_state = state;
		_activityCWD = activityCWD;
		_executionPlan = executionPlan;
		_nextPhase = nextPhase;
		_activityServiceName = activityServiceName;
		_jobName = jobName;
		_suspendRequested = suspendRequested;
		_terminateRequested = terminateRequested;
		
		_runner = new ActivityRunner(_suspendRequested, _terminateRequested);
		_policyListener = new PolicyListener();
		_bes.getPolicyEnactor().addBESPolicyListener(_policyListener);
		
		Thread thread = new Thread(_runner, "BES Activity Runner Thread");
		thread.setDaemon(true);
		thread.start();
	}
	
	@SuppressWarnings("unchecked")
	synchronized public void verifyOwner() 
		throws GenesisIISecurityException, SQLException
	{
		Connection connection = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		try
		{
			connection = _connectionPool.acquire();
			stmt = connection.prepareStatement("SELECT owners FROM besactivitiestable " +
				"WHERE activityid = ?");
			stmt.setString(1, _activityid);
			rs = stmt.executeQuery();
			if (!rs.next())
				throw new SQLException(
					"Unable to load owner information from database " +
					"for bes activity.");
			
			if (!QueueSecurity.isOwner(
				(Collection<Identity>)DBSerializer.fromBlob(rs.getBlob(1))))
				throw new GenesisIISecurityException(
					"Caller does not have permission to get " +
					"activity status for activity \"" + _activityid + "\".");
		}
		finally
		{
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	synchronized public EndpointReferenceType getActivityEPR()
		throws SQLException, ResourceException
	{
		Connection connection = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		try
		{
			connection = _connectionPool.acquire();
			stmt = connection.prepareStatement(
				"SELECT activityepr FROM besactivitiestable " +
				"WHERE activityid = ?");
			stmt.setString(1, _activityid);
			rs = stmt.executeQuery();
			if (!rs.next())
				throw new SQLException("Unable to find activity in database.");
			return EPRUtils.fromBlob(rs.getBlob(1));
		}
		finally
		{
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	synchronized public JobDefinition_Type getJobDefinition()
		throws SQLException, IOException, ClassNotFoundException
	{
		Connection connection = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		try
		{
			connection = _connectionPool.acquire();
			stmt = connection.prepareStatement(
				"SELECT jsdl FROM besactivitiestable " +
				"WHERE activityid = ?");
			stmt.setString(1, _activityid);
			rs = stmt.executeQuery();
			if (!rs.next())
				throw new SQLException("Unable to find activity in database.");
			return DBSerializer.xmlFromBlob(JobDefinition_Type.class, 
				rs.getBlob(1));
		}
		finally
		{
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	synchronized public Collection<Throwable> getFaults()
		throws SQLException
	{
		Connection connection = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		Collection<Throwable> faults = new LinkedList<Throwable>();
		
		try
		{
			connection = _connectionPool.acquire();
			stmt = connection.prepareStatement(
				"SELECT fault FROM besactivityfaultstable " +
				"WHERE besactivityid = ?");
			stmt.setString(1, _activityid);
			rs = stmt.executeQuery();
			while (rs.next())
			{
				faults.add((Throwable)DBSerializer.fromBlob(rs.getBlob(1)));
			}
			
			return faults;
		}
		finally
		{
			StreamUtils.close(rs);
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	protected void finalize() throws Throwable
	{
		close();
	}
	
	synchronized public void close() throws IOException
	{
		if (_policyListener != null)
		{
			_bes.getPolicyEnactor().removeBESPolicyListener(_policyListener);
			_policyListener = null;
			
			try
			{
				terminate();
			}
			catch (Throwable ee)
			{
				_logger.error("Problem trying to early terminate activity.", 
					ee);
			}
		}
	}
	
	public String getJobName()
	{
		return _jobName;
	}
	
	synchronized public void suspend() throws ExecutionException,
		SQLException
	{
		if (_suspendRequested)
			return;
		
		updateState(true, _terminateRequested);
		_runner.requestSuspend();
	}
	
	synchronized public void terminate() throws ExecutionException,
		SQLException
	{
		if (_terminateRequested)
			return;
		
		updateState(false, true);
		_runner.requestTerminate();
	}
	
	synchronized public void resume() throws ExecutionException,
		SQLException
	{
		if (!_suspendRequested)
			return;
		
		updateState(false, _terminateRequested);
		_runner.requestResume();
	}
	
	public ActivityState getState()
	{
		ActivityState retState = (ActivityState)_state.clone();
		if (_runner.isSuspended())
			retState.suspend(true);
		
		return retState;
	}
	
	synchronized private void updateState(
		boolean suspendRequested, boolean terminateRequested)
			throws SQLException
	{
		Connection connection = null;
		PreparedStatement stmt = null;
		
		try
		{
			connection = _connectionPool.acquire();
			stmt = connection.prepareStatement(
				"UPDATE besactivitiestable " +
					"SET suspendrequested = ?, terminaterequested = ? " +
				"WHERE activityid = ?");
			stmt.setShort(1, suspendRequested ? (short)1 : (short)0);
			stmt.setShort(2, terminateRequested ? (short)1 : (short)0);
			stmt.setString(3, _activityid);
			if (stmt.executeUpdate() != 1)
				throw new SQLException("Unable to update database.");
			connection.commit();
			
			_suspendRequested = suspendRequested;
			_terminateRequested = terminateRequested;
		}
		finally
		{
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	synchronized private void updateState(int nextPhase, ActivityState state)
		throws SQLException
	{
		Connection connection = null;
		PreparedStatement stmt = null;
		
		try
		{
			connection = _connectionPool.acquire();
			stmt = connection.prepareStatement(
				"UPDATE besactivitiestable SET nextphase = ?, state = ? " +
				"WHERE activityid = ?");
			stmt.setInt(1, nextPhase);
			stmt.setBlob(2, DBSerializer.toBlob(state));
			stmt.setString(3, _activityid);
			if (stmt.executeUpdate() != 1)
				throw new SQLException("Unable to update database.");
			connection.commit();
			
			_nextPhase = nextPhase;
			_state = state;
		}
		finally
		{
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	private WorkingContext createWorkingContext()
		throws SQLException
	{
		Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try
        {
            connection = _connectionPool.acquire();
            stmt = connection.prepareStatement(
                "SELECT callingcontext, activityepr " +
                    "FROM besactivitiestable " +
                "WHERE activityid = ?");
            stmt.setString(1, _activityid);
            rs = stmt.executeQuery();
            if (!rs.next())
                throw new SQLException("Activity \"" + _activityid +
                    "\" does not exist.");
            ICallingContext cctxt = (ICallingContext)DBSerializer.fromBlob(
                rs.getBlob(1));
            EndpointReferenceType epr = EPRUtils.fromBlob(rs.getBlob(2));

            WorkingContext ret = new WorkingContext();
            ret.setProperty(WorkingContext.EPR_PROPERTY_NAME, epr);
            ret.setProperty(WorkingContext.TARGETED_SERVICE_NAME, 
            	_activityServiceName);
            ret.setProperty(WorkingContext.CURRENT_CONTEXT_KEY, cctxt);
            return ret;
        }
        catch (ResourceException re)
        {
            throw new SQLException("Unable to load working context.", re);
        }
        finally
        {
            StreamUtils.close(rs);
            StreamUtils.close(stmt);
            _connectionPool.release(connection);
        }
	}
	
	private void execute(ExecutionPhase phase) throws Throwable
	{
		WorkingContext ctxt = createWorkingContext();
		
		try
		{
			WorkingContext.setCurrentWorkingContext(ctxt);
			phase.execute(getExecutionContext());
		}
		finally
		{
			WorkingContext.setCurrentWorkingContext(null);
		}
	}
	
	private ExecutionContext getExecutionContext()
	{
		return new ExecutionContext()
		{
			@Override
			public ICallingContext getCallingContext()
					throws ExecutionException
			{
				Connection connection = null;
				PreparedStatement stmt = null;
				ResultSet rs = null;
				
				try
				{
					connection = _connectionPool.acquire();
					stmt = connection.prepareStatement(
						"SELECT callingcontext FROM besactivitiestable " +
						"WHERE activityid = ?");
					stmt.setString(1, _activityid);
					rs = stmt.executeQuery();
					if (!rs.next())
						throw new SQLException(
							"Unable to find activity in database.");
					return (ICallingContext)DBSerializer.fromBlob(rs.getBlob(1));
				}
				catch (SQLException sqe)
				{
					throw new ExecutionException(
						"Database error trying to get calling context.", sqe);
				}
				finally
				{
					StreamUtils.close(rs);
					StreamUtils.close(stmt);
					_connectionPool.release(connection);
				}
			}

			@Override
			public File getCurrentWorkingDirectory() throws ExecutionException
			{
				return _activityCWD;
			}
		};
	}
	
	private void addFault(Throwable cause)
	{
		Connection connection = null;
		PreparedStatement stmt = null;
		
		try
		{
			connection = _connectionPool.acquire();
			stmt = connection.prepareStatement(
				"INSERT INTO besactivityfaultstable " +
					"(besactivityid, fault) " +
				"VALUES(?, ?)");
			stmt.setString(1, _activityid);
			stmt.setBlob(2, DBSerializer.toBlob(cause));
			stmt.executeUpdate();
			connection.commit();
		}
		catch (Throwable cause2)
		{
			_logger.error("Unexpected error while adding fault " +
				"to bes activity fault database.", cause2);
		}
		finally
		{
			StreamUtils.close(stmt);
			_connectionPool.release(connection);
		}
	}
	
	private class ActivityRunner implements Runnable
	{
		private Thread _myThread = null;
		private boolean _terminateRequested = false;
		private boolean _suspendRequested = false;
		
		private boolean _suspended = false;
		
		private Object _phaseLock = new Object();
		private ExecutionPhase _currentPhase = null;
		
		public ActivityRunner(boolean suspendRequested, 
			boolean terminateRequested)
		{
			_terminateRequested = terminateRequested;
			_suspendRequested = suspendRequested;
		}
		
		final private boolean finishedExecution()
		{
			return _nextPhase >= _executionPlan.size();
		}
		
		final public boolean isSuspended()
		{
			synchronized(_phaseLock)
			{
				return _suspended ||
					(_currentPhase != null && 
						_currentPhase instanceof SuspendableExecutionPhase);
			}
		}
		
		public boolean requestSuspend() throws ExecutionException
		{
			synchronized(_phaseLock)
			{
				if (_suspendRequested || _terminateRequested)
					return true;
				
				_suspendRequested = true;
				if (_currentPhase != null)
				{
					if (_currentPhase instanceof SuspendableExecutionPhase)
						((SuspendableExecutionPhase)_currentPhase).suspend();
					else
						return false;
				} else
					return true;
			}

			return true;
		}
		
		public void requestTerminate() throws ExecutionException
		{
			synchronized(_phaseLock)
			{
				if (_terminateRequested)
					return;
				
				_suspendRequested = false;
				_terminateRequested = true;
				
				if (_currentPhase != null)
				{
					if (_currentPhase instanceof TerminateableExecutionPhase)
						((TerminateableExecutionPhase)_currentPhase).terminate();
				} else
				{
					_phaseLock.notify();
				}
			}
			
			synchronized(this)
			{
				if (_myThread != null)
					_myThread.interrupt();
			}
		}
		
		public void requestResume() throws ExecutionException
		{
			synchronized(_phaseLock)
			{
				if (!_suspendRequested && !_suspended)
					return;
				
				_suspendRequested = false;
				
				if (_currentPhase != null)
				{
					if (_currentPhase instanceof SuspendableExecutionPhase)
						((SuspendableExecutionPhase)_currentPhase).resume();
				}
				
				_phaseLock.notify();
			}
		}
		
		public void run()
		{
			synchronized(this)
			{
				_myThread = Thread.currentThread();
			}
			
			while (true)
			{
				try
				{
					synchronized(_phaseLock)
					{
						if (finishedExecution())
						{
							if (getFaults().size() > 0)
								updateState(_executionPlan.size(),
									new ActivityState(
										ActivityStateEnumeration.Failed, 
										null, false));
							else
								updateState(_executionPlan.size(),
									new ActivityState(
										ActivityStateEnumeration.Finished, 
										null, false));
							break;
						}
					
						if (_terminateRequested)
						{
							updateState(_executionPlan.size(), new ActivityState(
								ActivityStateEnumeration.Cancelled, null, false));
							break;
						}
	
						while (_suspendRequested)
						{
							_suspended = true;
							try
							{
								_phaseLock.wait();
							}
							catch (InterruptedException ie)
							{
								Thread.currentThread().isInterrupted();
							}
						}
						_suspended = false;
						
						if (_terminateRequested)
						{
							updateState(_executionPlan.size(), new ActivityState(
								ActivityStateEnumeration.Cancelled, null, false));
							break;
						}
						
						_currentPhase = _executionPlan.get(_nextPhase);
						updateState(_nextPhase, _currentPhase.getPhaseState());
					}
				
					try
					{
						execute(_currentPhase);
					}
					catch (ContinuableExecutionException cee)
					{
						addFault(cee);
					}
					catch (InterruptedException ie)
					{
						Thread.currentThread().isInterrupted();
					}
					
					synchronized(_phaseLock)
					{
						if (_terminateRequested)
							continue;
						
						_currentPhase = null;
						updateState(_nextPhase + 1, _state);
					}
				}
				catch (Throwable cause)
				{
					_logger.error("BES Activity Unrecoverably Faulted.", cause);
					addFault(cause);
					try
					{
						updateState(_executionPlan.size(),
							new ActivityState(ActivityStateEnumeration.Failed, 
								null, false));
					}
					catch (Throwable cause2)
					{
						_logger.error(
							"Unexpected exception occured in bes activity.", 
							cause2);
						return;
					}
				}
			}
			
			synchronized(this)
			{
				_myThread = null;
			}
		}
	}
	
	private class PolicyListener implements BESPolicyListener
	{
		@Override
		public void kill() throws ExecutionException
		{
			_runner.requestTerminate();
		}

		@Override
		public void resume() throws ExecutionException
		{
			_runner.requestResume();
		}

		@Override
		public void suspend() throws ExecutionException
		{
			_runner.requestSuspend();
		}

		@Override
		public void suspendOrKill() throws ExecutionException
		{
			if (!_runner.requestSuspend())
				kill();
		}
	}
}
