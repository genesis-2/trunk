package edu.virginia.vcgr.genii.network.ftp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import edu.virginia.vcgr.genii.client.cmd.tools.LoginTool;
import edu.virginia.vcgr.genii.client.cmd.tools.LogoutTool;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.context.IContextResolver;
import edu.virginia.vcgr.genii.client.context.InMemorySerializedContextResolver;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;

public class GeniiBackendConfiguration implements Cloneable
{
	private ICallingContext _callingContext;
	private RNSPath _root;

	private GeniiBackendConfiguration(ICallingContext callingContext, RNSPath root) throws IOException
	{
		_callingContext = callingContext;
		_root = root;

		// This always gets called in a new thread, so...
		ContextManager.setResolver(new InMemorySerializedContextResolver());
		ContextManager.storeCurrentContext(_callingContext);
	}

	public GeniiBackendConfiguration(BufferedReader stdin, PrintWriter stdout, PrintWriter stderr,
		ICallingContext callingContext) throws Throwable
	{
		IContextResolver oldResolver = ContextManager.getResolver();
		try {
			IContextResolver newResolver = new InMemorySerializedContextResolver();
			ContextManager.setResolver(newResolver);

			newResolver.store(callingContext);
			_callingContext = newResolver.load();

			_root = _callingContext.getCurrentPath().getRoot();
			_callingContext.setCurrentPath(_root);

			LogoutTool logout = new LogoutTool();
			logout.setAll();
			logout.run(stdout, stderr, stdin);

			// Assume normal user/pass -> idp login
			LoginTool login = new LoginTool();
			login.run(stdout, stderr, stdin);
			_callingContext = newResolver.load();
		} finally {
			ContextManager.setResolver(oldResolver);
		}
	}

	public GeniiBackendConfiguration(BufferedReader stdin, PrintWriter stdout, PrintWriter stderr) throws Throwable
	{
		this(stdin, stdout, stderr, ContextManager.getExistingContext());
	}

	public void setSandboxPath(String sandboxPath) throws RNSException
	{
		_root = _root.lookup(sandboxPath, RNSPathQueryFlags.MUST_EXIST);
		_callingContext.setCurrentPath(_root);
	}

	public RNSPath getRoot()
	{
		return _root;
	}

	public ICallingContext getCallingContext()
	{
		return _callingContext;
	}

	public Object clone()
	{
		try {
			return new GeniiBackendConfiguration(_callingContext.deriveNewContext(), _root);
		} catch (IOException ioe) {
			throw new RuntimeException("Unexpected internal exception.", ioe);
		}
	}
}