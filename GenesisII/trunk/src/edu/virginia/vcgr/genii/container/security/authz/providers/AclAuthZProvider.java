/*
 * Copyright 2006 University of Virginia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package edu.virginia.vcgr.genii.container.security.authz.providers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.morgan.util.configuration.ConfigurationException;
import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.algorithm.application.ProgramTools;
import edu.virginia.vcgr.genii.client.configuration.DeploymentName;
import edu.virginia.vcgr.genii.client.configuration.Installation;
import edu.virginia.vcgr.genii.client.configuration.Security;
import edu.virginia.vcgr.genii.client.context.ContextManager;
import edu.virginia.vcgr.genii.client.context.ICallingContext;
import edu.virginia.vcgr.genii.client.resource.IResource;
import edu.virginia.vcgr.genii.client.resource.ResourceException;
import edu.virginia.vcgr.genii.client.security.axis.AuthZSecurityException;
import edu.virginia.vcgr.genii.client.security.axis.AxisAcl;
import edu.virginia.vcgr.genii.client.sync.VersionVector;
import edu.virginia.vcgr.genii.client.wsrf.wsn.NotificationMessageContents;
import edu.virginia.vcgr.genii.common.security.AclEntryListType;
import edu.virginia.vcgr.genii.common.security.AuthZConfig;
import edu.virginia.vcgr.genii.container.Container;
import edu.virginia.vcgr.genii.container.sync.VersionedResourceUtils;
import edu.virginia.vcgr.genii.container.wsrf.wsn.topic.PublisherTopic;
import edu.virginia.vcgr.genii.container.wsrf.wsn.topic.TopicSet;
import edu.virginia.vcgr.genii.security.RWXCategory;
import edu.virginia.vcgr.genii.security.SecurityConstants;
import edu.virginia.vcgr.genii.security.TransientCredentials;
import edu.virginia.vcgr.genii.security.VerbosityLevel;
import edu.virginia.vcgr.genii.security.acl.Acl;
import edu.virginia.vcgr.genii.security.acl.AclEntry;
import edu.virginia.vcgr.genii.security.axis.MessageLevelSecurityRequirements;
import edu.virginia.vcgr.genii.security.credentials.NuCredential;
import edu.virginia.vcgr.genii.security.credentials.TrustCredential;
import edu.virginia.vcgr.genii.security.credentials.X509Identity;
import edu.virginia.vcgr.genii.security.identity.Identity;
import edu.virginia.vcgr.genii.security.rwx.RWXManager;

/**
 * AuthZ provider implementation of GII Acls: an access-control mechanism comprised of
 * read/write/execute policy-sets.
 * 
 * NOTES: - The presence of a NULL certificate in the ACL indicates open access. - A NULL ACL
 * indicates no access
 * 
 * @author dmerrill
 */
public class AclAuthZProvider implements IAuthZProvider, AclTopics
{
	/*
	 * cak: this one seems too dangerous to change over to a SAML-named counterpart. it is the name
	 * under which all the Acl information is stored in any grid we would want to migrate.
	 */
	static public final String GENII_ACL_PROPERTY_NAME = "genii.container.security.authz.gaml-acl";

	static protected final MessageLevelSecurityRequirements _defaultMinMsgSec = new MessageLevelSecurityRequirements(
		MessageLevelSecurityRequirements.NONE);

	static protected HashMap<String, X509Certificate> _defaultCertCache = new HashMap<String, X509Certificate>();

	static private Log _logger = LogFactory.getLog(AclAuthZProvider.class);

	/*
	 * NOTE: 'static' below is an implementation change--we are only scanning the default owners
	 * once now. we have already been telling people to stop the container if they want to update
	 * the admin certificate or default owners...
	 */
	static private X509Certificate[] _defaultInitialResourceOwners = null;

	public AclAuthZProvider() throws GeneralSecurityException, IOException
	{
		if (_defaultInitialResourceOwners == null) {
			Collection<File> ownerFiles = Installation.getDeployment(new DeploymentName()).security().getDefaultOwnerFiles();

			// read in the certificates that are to serve as default owner
			if ((ownerFiles != null) && (ownerFiles.size() > 0)) {
				synchronized (_defaultCertCache) {
					Collection<X509Certificate> ownerCerts = new ArrayList<X509Certificate>(ownerFiles.size());

					for (File ownerFile : ownerFiles) {
						if (_logger.isDebugEnabled())
							_logger.debug("adding " + ownerFile + " as admin certificate file.");
						X509Certificate ownerCert = _defaultCertCache.get(ownerFile.getName());
						if (ownerCert == null) {
							CertificateFactory cf = CertificateFactory.getInstance("X.509");
							FileInputStream fin = null;
							try {
								ownerCert = (X509Certificate) cf.generateCertificate(fin = new FileInputStream(ownerFile));
								_defaultCertCache.put(ownerFile.getName(), ownerCert);
							} finally {
								StreamUtils.close(fin);
							}
						}
						ownerCerts.add(ownerCert);
						if (_logger.isDebugEnabled())
							_logger.debug("setting up administrator access for " + ownerCert.getSubjectDN());
					}

					_defaultInitialResourceOwners = ownerCerts.toArray(new X509Certificate[ownerCerts.size()]);
				}
			} else {
				_defaultInitialResourceOwners = new X509Certificate[] { Container.getContainerCertChain()[0] };
			}
		}
	}

	/**
	 * Presently configures the specified resource to have default access allowed for every
	 * credential in the bag of credentials. We may want to look at restricting this in the future
	 * to special credentials.
	 */
	public void setDefaultAccess(ICallingContext callingContext, IResource resource, X509Certificate[] serviceCertChain)
		throws AuthZSecurityException, ResourceException
	{

		HashSet<Identity> defaultOwners = new HashSet<Identity>();

		// Add desired authorized identities from incoming cred
		if (callingContext != null) {
			TransientCredentials transientCredentials = TransientCredentials.getTransientCredentials(callingContext);

			for (NuCredential cred : transientCredentials.getCredentials()) {

				if (cred instanceof Identity) {
					if (_logger.isTraceEnabled())
						_logger.trace("adding identity to UMask... " + cred.describe(VerbosityLevel.HIGH));
					if (((Identity) cred).placeInUMask())
						defaultOwners.add((Identity) cred);
				} else if (cred instanceof TrustCredential) {
					X509Identity assertedIdentity = ((TrustCredential) cred).getRootIdentity();
					if (assertedIdentity.placeInUMask()) {
						if (_logger.isTraceEnabled())
							_logger.trace("adding cred to UMask... " + assertedIdentity.describe(VerbosityLevel.HIGH));
						defaultOwners.add(assertedIdentity);
					} else {
						if (_logger.isTraceEnabled())
							_logger.trace("NOT adding cred to UMask... " + assertedIdentity.describe(VerbosityLevel.HIGH));
					}

				} else {
					if (_logger.isTraceEnabled())
						_logger.trace("NOT adding this thing to UMask... " + cred.describe(VerbosityLevel.HIGH));
				}
			}
		}

		// if no incoming credentials, use the default owner identity indicated
		// by the container
		if (defaultOwners.isEmpty()) {
			if (_defaultInitialResourceOwners != null) {
				for (X509Certificate cert : _defaultInitialResourceOwners) {
					defaultOwners.add(new X509Identity(new X509Certificate[] { cert }));
				}
			}
		}

		/*
		 * dgm4d: this is a security issue // Add the resoure's static creating service if
		 * (serviceCertChain != null) { defaultOwners.add(new X509Identity(serviceCertChain)); }
		 */

		// Add the resource itself
		defaultOwners
			.add(new X509Identity((X509Certificate[]) resource.getProperty(IResource.CERTIFICATE_CHAIN_PROPERTY_NAME)));

		Acl acl = new Acl();
		acl.readAcl.addAll(defaultOwners);
		acl.writeAcl.addAll(defaultOwners);
		acl.executeAcl.addAll(defaultOwners);

		resource.setProperty(GENII_ACL_PROPERTY_NAME, acl);
	}

	private boolean checkAclAccess(Identity identity, RWXCategory category, Acl acl)
	{
		Collection<AclEntry> trustList = null;
		switch (category) {
			case READ:
				trustList = acl.readAcl;
				break;
			case WRITE:
				trustList = acl.writeAcl;
				break;
			case EXECUTE:
				trustList = acl.executeAcl;
				break;
			case OPEN:
				return true;
			case CLOSED:
				return false;
			case INHERITED:
				_logger.warn("unprocessed case for inherited attribute.");
		}
		if (trustList == null) {
			// Empty ACL
			_logger.error("failing ACL access check due to null trust list");
			return false;
		} else if (trustList.contains(null)) {
			// ACL contains null (the wildcard certificate)
			if (_logger.isTraceEnabled())
				_logger.trace("passing ACL access check due to wildcard in trust list");
			return true;
		} else {
			// go through the AclEntries
			for (AclEntry entry : trustList) {
				try {
					if (entry.isPermitted(identity)) {
						if (_logger.isTraceEnabled())
							_logger.trace("passing ACL access check due to permission for identity");
						return true;
					}
				} catch (Exception e) {
					_logger.error("caught exception coming from isPermitted check on identity: " + identity.toString());
				}
			}
		}
		if (_logger.isTraceEnabled())
			_logger.trace("bailing on ACL access check after exhausting all options.");
		return false;
	}

	static public String getResourceName(IResource resource)
	{
		String toReturn = resource.getKey();
		try {
			if (resource.getProperty(SecurityConstants.NEW_IDP_NAME_QNAME.getLocalPart()) != null) {
				toReturn.concat("--" + (String) resource.getProperty(SecurityConstants.NEW_IDP_NAME_QNAME.getLocalPart()));
			} else {
				toReturn.concat("--" + "unknown details");
			}
		} catch (Throwable e) {
			// ignore, will just miss part of print-out.
		}		
		return toReturn;
	}
	
	@Override
	public boolean checkAccess(Collection<NuCredential> authenticatedCallerCredentials, IResource resource,
		Class<?> serviceClass, Method operation, String errorText)
	{
		RWXCategory category = RWXManager.lookup(serviceClass, operation);

		if (!checkAccess(authenticatedCallerCredentials, resource, category, errorText)) {
			String msg = "denying access for operation: " + operation.getName();
			String asset = getResourceName(resource);
			msg.concat(asset + " at " + ProgramTools.showLastFewOnStack(4));
			_logger.error(msg);
			errorText = msg;
			return false;
		}
		return true;
	}

	public void blurtCredentials(String prefixedMessage, Collection<NuCredential> creds)
	{
		if (!_logger.isDebugEnabled())
			return; // no printing unless debug mode allowed.
		StringBuilder credsAsString = new StringBuilder(prefixedMessage);
		credsAsString.append("\n");
		for (NuCredential cred : creds) {
			credsAsString.append("\n----\n");
			credsAsString.append(cred.toString());
			credsAsString.append("\n----\n");
		}
		_logger.debug(credsAsString);
	}

	@Override
	public boolean
		checkAccess(Collection<NuCredential> authenticatedCallerCredentials, IResource resource, RWXCategory category, String errorText)
	{
		String messagePrefix = "checkAccess for " + category + " on ";
		String resourceName = null;
		try {
			resourceName = (String) resource.getProperty(IResource.ENDPOINT_IDENTIFIER_PROPERTY_NAME);
		} catch (Throwable e) {
			// ignore.
		}
		if (resourceName == null) {
			resourceName = resource.getKey();
		}
		messagePrefix = messagePrefix.concat(resourceName + " ");

		try {
			ICallingContext callContext = ContextManager.getExistingContext();
			Acl acl = (Acl) resource.getProperty(GENII_ACL_PROPERTY_NAME);
			
			// pre-emptive check of wildcard access.
			if ((acl == null) || checkAclAccess(null, category, acl)) {
				if (_logger.isDebugEnabled())
					_logger.debug(messagePrefix + "granted to everyone.");
				return true;
			}

			// try each identity in the caller's credentials.
			for (NuCredential cred : authenticatedCallerCredentials) {
				if (cred instanceof Identity) {
					if (cred instanceof X509Identity) {
						if (((X509Identity) cred).checkRWXAccess(category)) {
							if (checkAclAccess((Identity) cred, category, acl)) {
								if (_logger.isDebugEnabled())
									_logger.debug(messagePrefix + "granted to identity with x509: "
										+ cred.describe(VerbosityLevel.LOW));
								return true;
							}
						}
					} else if (checkAclAccess((Identity) cred, category, acl)) {
						// straight-up identity (username/password)
						if (_logger.isDebugEnabled())
							_logger.debug(messagePrefix + "access granted to identity: " + cred.describe(VerbosityLevel.LOW));
						return true;
					}
				} else if (cred instanceof TrustCredential) {
					TrustCredential sa = (TrustCredential) cred;
					if (sa.checkRWXAccess(category)) {
						// check the root identity of the trust delegation to see if they have
						// access.
						X509Identity ia = (X509Identity) sa.getRootIdentity();
						if (checkAclAccess(ia, category, acl)) {
							if (_logger.isDebugEnabled())
								_logger
									.debug(messagePrefix + "granted to trust credential: " + sa.describe(VerbosityLevel.LOW));
							return true;
						}
					}
				}
			}

			// Check Administrator Access
			if (Security.isAdministrator(callContext)) {
				if (_logger.isDebugEnabled())
					_logger.debug(messagePrefix + "granted because caller is admin.");
				return true;
			}

			// Nobody appreciates us
			if (_logger.isTraceEnabled()) {
				errorText = messagePrefix + " was not granted for credential set: ";
				blurtCredentials(errorText, authenticatedCallerCredentials);
			}
			return false;
		} catch (AuthZSecurityException ase) {
			errorText = "failure, saw authorization security exception for " + messagePrefix + ":" + ase.getMessage();
			_logger.error(errorText);
			return false;
		} catch (IOException e) {
			errorText = "failure, saw IO exception processing credential for " + messagePrefix + ":" + e.getMessage();
			_logger.error(errorText);
			return false;
		} catch (ConfigurationException e) {
			errorText = "saw config exception for " + messagePrefix + ":" + e.getMessage();
			_logger.error(errorText);
			return false;
		}
	}

	public MessageLevelSecurityRequirements getMinIncomingMsgLevelSecurity(IResource resource) throws AuthZSecurityException,
		ResourceException
	{

		try {
			// get ACL
			Acl acl = (Acl) resource.getProperty(GENII_ACL_PROPERTY_NAME);

			if (acl == null) {

				// return no security requirements if null ACL
				return new MessageLevelSecurityRequirements(MessageLevelSecurityRequirements.NONE);

			} else if (acl.requireEncryption) {

				// add in encryption
				return _defaultMinMsgSec.computeUnion(new MessageLevelSecurityRequirements(
					MessageLevelSecurityRequirements.ENCRYPT));
			}

			return _defaultMinMsgSec;
		} catch (ResourceException e) {
			throw new AuthZSecurityException("Could not retrieve minimum incoming message level security.", e);
		}
	}

	public AuthZConfig getAuthZConfig(IResource resource) throws AuthZSecurityException, ResourceException
	{
		return getAuthZConfig(resource, true);
	}

	public AuthZConfig getAuthZConfig(IResource resource, boolean sanitize) throws AuthZSecurityException, ResourceException
	{
		try {
			// get ACL
			Acl acl = (Acl) resource.getProperty(GENII_ACL_PROPERTY_NAME);

			if (acl != null) {
				return AxisAcl.encodeAcl(acl, true);
			}

			return new AuthZConfig(null);

		} catch (ResourceException e) {
			throw new AuthZSecurityException("Unable to load AuthZ config.", e);
		}
	}

	public void setAuthZConfig(AuthZConfig config, IResource resource) throws AuthZSecurityException, ResourceException
	{
		Acl acl = AxisAcl.decodeAcl(config);
		resource.setProperty(GENII_ACL_PROPERTY_NAME, acl);
		resource.commit();
	}

	/**
	 * Inform subscribers that one or more entries has been added or removed from one or more ACLs.
	 */
	public void sendAuthZConfig(AuthZConfig oldConfig, AuthZConfig newConfig, IResource resource)
		throws AuthZSecurityException, ResourceException
	{
		if (resource.isServiceResource())
			return;
		Acl oldAcl = AxisAcl.decodeAcl(oldConfig);
		if (oldAcl == null)
			oldAcl = new Acl();
		Acl newAcl = AxisAcl.decodeAcl(newConfig);
		if (newAcl == null)
			newAcl = new Acl();
		List<AclEntry> entryList = new ArrayList<AclEntry>();
		List<String> tagList = new ArrayList<String>();
		findDeltas(entryList, tagList, newAcl.readAcl, oldAcl.readAcl, "r");
		findDeltas(entryList, tagList, newAcl.writeAcl, oldAcl.writeAcl, "w");
		findDeltas(entryList, tagList, newAcl.executeAcl, oldAcl.executeAcl, "x");
		if (entryList.size() > 0) {
			VersionVector vvr = VersionedResourceUtils.incrementResourceVersion(resource);
			TopicSet space = TopicSet.forPublisher(getClass());
			PublisherTopic publisherTopic = space.createPublisherTopic(GENII_ACL_CHANGE_TOPIC);
			AclEntryListType entryArray = AxisAcl.encodeIdentityList(entryList, false);
			String[] tagArray = tagList.toArray(new String[0]);
			publisherTopic.publish(new AclChangeContents(entryArray, tagArray, vvr));
		}
	}

	private static void findDeltas(List<AclEntry> entryList, List<String> tagList, Collection<AclEntry> newAcl,
		Collection<AclEntry> oldAcl, String mode)
	{
		if (newAcl == null)
			newAcl = new ArrayList<AclEntry>();
		if (oldAcl == null)
			oldAcl = new ArrayList<AclEntry>();
		Iterator<AclEntry> newIter = newAcl.iterator();
		while (newIter.hasNext()) {
			AclEntry entry = newIter.next();
			boolean found = false;
			Iterator<AclEntry> oldIter = oldAcl.iterator();
			while ((!found) && oldIter.hasNext()) {
				AclEntry oldEntry = oldIter.next();
				if (((entry == null) && (oldEntry == null)) || ((entry != null) && entry.equals(oldEntry))) {
					found = true;
					oldIter.remove();
				}
			}
			if (!found) {
				if (_logger.isTraceEnabled())
					_logger.trace("adding acl to list: " + entry == null ? "wildcard" : entry);
				entryList.add(entry);
				tagList.add("+" + mode);
			}
		}
		Iterator<AclEntry> oldIter = oldAcl.iterator();
		while (oldIter.hasNext()) {
			AclEntry oldEntry = oldIter.next();
			if (_logger.isTraceEnabled())
				_logger.trace("removing acl from list " + oldEntry == null ? "wildcard" : oldEntry);
			entryList.add(oldEntry);
			tagList.add("-" + mode);
		}
	}

	/**
	 * Update the resource ACLs with the changes that have been applied to a replica.
	 */
	public void receiveAuthZConfig(NotificationMessageContents message, IResource resource) throws ResourceException,
		AuthZSecurityException
	{
		Acl acl = (Acl) resource.getProperty(GENII_ACL_PROPERTY_NAME);
		AclChangeContents contents = (AclChangeContents) message;
		AclEntryListType encodedList = contents.aclEntryList();
		List<AclEntry> entryList = AxisAcl.decodeIdentityList(encodedList);
		int length = entryList.size();
		if (length == 0)
			return;
		String[] tagList = contents.tagList();
		if ((tagList == null) || (tagList.length != length))
			throw new AuthZSecurityException("AclChangeContents " + "entry list does not match tag list");
		for (int idx = 0; idx < length; idx++) {
			AclEntry entry = entryList.get(idx);
			String tag = tagList[idx];
			AxisAcl.chmod(acl, tag, entry);
		}
		resource.setProperty(GENII_ACL_PROPERTY_NAME, acl);
		resource.commit();
	}
}
