package edu.virginia.vcgr.genii.container.exportdir;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.HashMap;

import javax.xml.namespace.QName;

import org.apache.axis.message.MessageElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ggf.rns.Add;
import org.ggf.rns.AddResponse;
import org.ggf.rns.RNSEntryExistsFaultType;
import org.ggf.rns.RNSEntryNotDirectoryFaultType;
import org.ggf.rns.RNSFaultType;
import org.oasis_open.wsrf.basefaults.BaseFaultType;
import org.oasis_open.wsrf.basefaults.BaseFaultTypeDescription;
import org.ws.addressing.EndpointReferenceType;

import edu.virginia.vcgr.genii.client.WellKnownPortTypes;
import edu.virginia.vcgr.genii.client.comm.ClientUtils;
import edu.virginia.vcgr.genii.client.exportdir.ExportedDirUtils;
import edu.virginia.vcgr.genii.client.resource.AttributedURITypeSmart;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.recursived.RNSRecursiveDescent;
import edu.virginia.vcgr.genii.client.rns.recursived.RNSRecursiveDescentCallback;
import edu.virginia.vcgr.genii.client.security.authz.rwx.RWXCategory;
import edu.virginia.vcgr.genii.client.security.authz.rwx.RWXMapping;

import org.oasis_open.docs.wsrf.r_2.ResourceUnknownFaultType;
import edu.virginia.vcgr.genii.common.rfactory.ResourceCreationFaultType;
import edu.virginia.vcgr.genii.container.Container;
import edu.virginia.vcgr.genii.container.context.WorkingContext;
import edu.virginia.vcgr.genii.container.resource.ResourceKey;
import edu.virginia.vcgr.genii.container.resource.ResourceManager;
import edu.virginia.vcgr.genii.container.util.FaultManipulator;
import edu.virginia.vcgr.genii.exportdir.ExportedDirPortType;
import edu.virginia.vcgr.genii.exportdir.ExportedRootPortType;
import edu.virginia.vcgr.genii.exportdir.QuitExport;
import edu.virginia.vcgr.genii.exportdir.QuitExportResponse;

public class ExportedRootServiceImpl extends ExportedDirServiceImpl implements
		ExportedRootPortType
{
	static private Log _logger = LogFactory.getLog(ExportedRootServiceImpl.class);
	
	public ExportedRootServiceImpl() throws RemoteException
	{
		this("ExportedRootPortType");
	}
	
	protected ExportedRootServiceImpl(String serviceName) throws RemoteException
	{
		super(serviceName);
		
		addImplementedPortType(WellKnownPortTypes.EXPORTED_ROOT_SERVICE_PORT_TYPE);
	}
	
	protected ResourceKey createResource(HashMap<QName, Object> creationParameters)
		throws ResourceException, BaseFaultType
	{
		_logger.debug("Creating new ExportedRoot Resource.");
		
		ExportedDirUtils.ExportedDirInitInfo initInfo = 
			ExportedDirUtils.extractCreationProperties(creationParameters);
		
		//ensure that local dir to be exported is readable
		//if so, proceed with export creation
		try
		{
			// check if directory exists
			if (!ExportedDirUtils.dirReadable(initInfo.getPath()))
			{
				throw FaultManipulator.fillInFault(
					new ResourceCreationFaultType(null, null, null, null, 
						new BaseFaultTypeDescription[] {
							new BaseFaultTypeDescription("Target directory " + 
								initInfo.getPath() + 
								" does not exist or is not readable.  " +
								"Cannot create export from this path.")	
				}, null));
			}
		}
		catch (IOException ioe)
		{
			throw new ResourceException(
					"Could not determine if export localpath is readable.", ioe);
		}
		
		return super.createResource(creationParameters);
	}
	
	@RWXMapping(RWXCategory.INHERITED)
	public AddResponse add(Add addRequest) throws RemoteException,
		RNSEntryExistsFaultType, ResourceUnknownFaultType,
		RNSEntryNotDirectoryFaultType, RNSFaultType
	{
		_logger.debug("ADDING Exported Root");
		
		EndpointReferenceType myEPR = 
			(EndpointReferenceType)WorkingContext.getCurrentWorkingContext().getProperty(
				WorkingContext.EPR_PROPERTY_NAME);
		myEPR.setAddress(new AttributedURITypeSmart(
			Container.getServiceURL("ExportedDirPortType")));
		ExportedDirPortType ed = ClientUtils.createProxy(ExportedDirPortType.class, myEPR);
		return ed.add(addRequest);
	}
	
	@RWXMapping(RWXCategory.EXECUTE)
	public QuitExportResponse quitExport(QuitExport quitExportRequest) throws RemoteException, ResourceUnknownFaultType
	{
		IExportedRootResource resource = 
			(IExportedRootResource)ResourceManager.getCurrentResource().dereference();
		
		resource.destroy(false);
		resource.commit();
		
		return new QuitExportResponse(true);
	}
	
	public void postCreate(ResourceKey rKey, EndpointReferenceType myEPR,
			HashMap<QName, Object> constructionParameters, 
			Collection<MessageElement> resolverCreationParams)
		throws ResourceException, BaseFaultType, RemoteException
	{
		//get construction params
		ExportedDirUtils.ExportedDirInitInfo initInfo = 
			ExportedDirUtils.extractCreationProperties(constructionParameters);
		
		//if replicated, package construction params for resolver
		if (initInfo.getReplicationState().equals("true"))
			ExportedDirUtils.createResolverCreationProperties(resolverCreationParams,
				initInfo);
		 
		super.postCreate(rKey, myEPR, constructionParameters, resolverCreationParams);	

		try
		{
			RNSRecursiveDescent descent = RNSRecursiveDescent.createDescent();
			descent.setAllowedRetries(5);
			descent.setAvoidCycles(false);
			descent.asyncDescend(new RNSPath(myEPR),
				new RNSRecursiveDescentCallback()
				{
					@Override
					public void finish() throws Throwable
					{
						// do nothing	
					}
	
					@Override
					public boolean handleRNSPath(RNSPath path) throws Throwable
					{
						// do nothing
						return true;
					}
				});
		}
		catch (Throwable cause)
		{
			_logger.warn("RNS Asynchronous Sync operation failed.", cause);
		}
	}
}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	