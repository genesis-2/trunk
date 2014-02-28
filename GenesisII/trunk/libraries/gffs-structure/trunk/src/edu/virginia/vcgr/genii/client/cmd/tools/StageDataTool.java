package edu.virginia.vcgr.genii.client.cmd.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;

import org.ggf.jsdl.JobDefinition_Type;
import org.xml.sax.InputSource;

import edu.virginia.vcgr.genii.client.cmd.InvalidToolUsageException;
import edu.virginia.vcgr.genii.client.cmd.ToolException;
import edu.virginia.vcgr.genii.client.context.CallingContextImpl;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.gpath.GeniiPath;
import edu.virginia.vcgr.genii.client.io.LoadFileResource;
import edu.virginia.vcgr.genii.client.io.URIManager;
import edu.virginia.vcgr.genii.client.jsdl.JSDLInterpreter;
import edu.virginia.vcgr.genii.client.jsdl.personality.PersonalityProvider;
import edu.virginia.vcgr.genii.client.ser.ObjectDeserializer;
import edu.virginia.vcgr.genii.client.jsdl.ContainerDataStage;
import edu.virginia.vcgr.genii.client.jsdl.JobRequest;
import edu.virginia.vcgr.genii.client.jsdl.parser.ExecutionProvider;
import edu.virginia.vcgr.genii.context.ContextType;
import edu.virginia.vcgr.genii.security.credentials.identity.UsernamePasswordIdentity;

public class StageDataTool extends BaseGridTool
{

	private String _type = "jsdl";
	private String _direction = "in";

	static private final String _DESCRIPTION = "config/tooldocs/description/dstageData";

	static private final String _USAGE = "config/tooldocs/usage/ustageData";

	static final private String _MANPAGE = "config/tooldocs/man/stageData";

	@Option({ "type" })
	public void setType(String type)
	{
		_type = type;
	}

	@Option({ "direction" })
	public void setDirection(String direction)
	{
		_direction = direction;
	}

	public StageDataTool()
	{
		super(new LoadFileResource(_DESCRIPTION), new LoadFileResource(_USAGE), true, ToolCategory.DATA);
		addManPage(new LoadFileResource(_MANPAGE));
	}

	@Override
	protected int runCommand() throws Throwable
	{

		// get the local identity's key material (or create one if necessary)
		ICallingContext callContext = ContextManager.getCurrentContext();
		if (callContext == null) {
			callContext = new CallingContextImpl(new ContextType());
		}

		InputStream in = null;

		File wDir = new File(getArgument(0));
		GeniiPath source = new GeniiPath(getArgument(1));

		if (!source.exists())
			throw new FileNotFoundException(String.format("Unable to find source file %s!", source));
		if (!source.isFile())
			throw new IOException(String.format("Source path %s is not a file!", source));

		in = source.openInputStream();

		JobRequest tJob = null;

		if (_type.equals("jsdl")) {
			JobDefinition_Type jsdl =
				(JobDefinition_Type) ObjectDeserializer.deserialize(new InputSource(in), JobDefinition_Type.class);
			PersonalityProvider provider = new ExecutionProvider();
			tJob = (JobRequest) JSDLInterpreter.interpretJSDL(provider, jsdl);
			in.close();
		} else if (_type.equals("binary")) {
			ObjectInputStream oIn = new ObjectInputStream(in);
			tJob = (JobRequest) oIn.readObject();
			in.close();
		} else {
			stdout.println("Invalid input type");
			return 0;
		}

		if (tJob != null) {
			if (_direction.equals("in")) {
				for (ContainerDataStage tStage : tJob.getStageIns()) {
					stageIN(wDir.getAbsolutePath() + "/" + tStage.getFileName(), new URI(tStage.getSourceURI()),
						tStage.getCredentials());
				}
			} else if (_direction.equals("out")) {
				for (ContainerDataStage tStage : tJob.getStageOuts()) {
					stageOUT(wDir.getAbsolutePath() + "/" + tStage.getFileName(), new URI(tStage.getTargetURI()),
						tStage.getCredentials());
				}
			} else {
				stdout.println("Invalid direction");
				return 0;
			}
		} else
			stdout.println("Error");

		return 0;
	}

	private void stageIN(String target, URI source, UsernamePasswordIdentity upi)
	{
		File fTarget = new File(target);
		try {
			URIManager.get(source, fTarget, upi);
		} catch (IOException e) {
			stdout.println("Unable to stage in file");
			return;
		}
	}

	private void stageOUT(String source, URI target, UsernamePasswordIdentity upi)
	{
		File fSource = new File(source);

		if (!fSource.exists()) {
			stdout.println("Unable to locate source");
			return;
		}

		try {
			URIManager.put(fSource, target, upi);
		} catch (Throwable cause) {
			stdout.println("Unable to stage out data");
			return;
		}
	}

	@Override
	protected void verify() throws ToolException
	{
		if (numArguments() != 2)
			throw new InvalidToolUsageException();
	}

}