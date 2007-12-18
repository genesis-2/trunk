/*
 * Copyright 2006 University of Virginia
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package edu.virginia.vcgr.genii.client.security.gamlauthz;

import java.io.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.GeneralSecurityException;
import java.util.ArrayList;

import org.morgan.util.cmdline.*;
import org.morgan.util.configuration.ConfigurationException;
import org.morgan.util.io.StreamUtils;

import edu.virginia.vcgr.genii.client.byteio.ByteIOInputStream;
import edu.virginia.vcgr.genii.client.rns.RNSException;
import edu.virginia.vcgr.genii.client.rns.RNSMultiLookupResultException;
import edu.virginia.vcgr.genii.client.rns.RNSPath;
import edu.virginia.vcgr.genii.client.rns.RNSPathDoesNotExistException;
import edu.virginia.vcgr.genii.client.rns.RNSPathQueryFlags;
import edu.virginia.vcgr.genii.client.security.gamlauthz.assertions.*;
import edu.virginia.vcgr.genii.client.security.gamlauthz.identity.*;
import edu.virginia.vcgr.genii.common.security.*;
import edu.virginia.vcgr.genii.client.cmd.tools.GamlLoginTool;

public class GamlClientTool {

	static private final int _BLOCK_SIZE = 1024 * 4;

	static private final int NOT_READ = 32;
	static private final int NOT_WRITE = 16;
	static private final int NOT_EXECUTE = 8;
	static private final int READ = 4;
	static private final int WRITE = 2;
	static private final int EXECUTE = 1;

	static public final String CHMOD_SYNTAX = 
		"( <[<+|->r][<+|->w][<+|->x]> | <octal mode> ) ( [--local-src] <cert-file> | --everyone | --username=<username> --password=<password>)";

	static private void copy(InputStream in, OutputStream out)
			throws IOException {
		byte[] data = new byte[_BLOCK_SIZE];
		int r;

		while ((r = in.read(data)) >= 0) {
			out.write(data, 0, r);
		}
	}

	public AuthZConfig getEmptyAuthZConfig() throws AuthZSecurityException {
		return GamlAcl.encodeAcl(new GamlAcl());
	}

	public void displayAuthZConfig(AuthZConfig config, PrintStream out,
			PrintStream err, BufferedReader in) throws AuthZSecurityException {

		if (config == null) {
			return;
		}

		GamlAcl acl = GamlAcl.decodeAcl(config);

		out.println("  Requires message-level encryption: " + acl.requireEncryption);
		out.println("  Read-authorized trust certificates: ");
		for (int i = 0; i < acl.readAcl.size(); i++) {
			Identity ident = acl.readAcl.get(i);
			if (ident == null) {
				out.println("    [" + i + "] EVERYONE");
			} else {
				out.println("    [" + i + "] " + ident);
			}
		}
		out.println("  Write-authorized trust certificates: ");
		for (int i = 0; i < acl.writeAcl.size(); i++) {
			Identity ident = acl.writeAcl.get(i);
			if (ident == null) {
				out.println("    [" + i + "] EVERYONE");
			} else {
				out.println("    [" + i + "] " + ident);
			}
		}
		out.println("  Execute-authorized trust certificates: ");
		for (int i = 0; i < acl.executeAcl.size(); i++) {
			Identity ident = acl.executeAcl.get(i);
			if (ident == null) {
				out.println("    [" + i + "] EVERYONE");
			} else {
				out.println("    [" + i + "] " + ident);
			}
		}
	}

	public static int parseMode(String modeString)
			throws IllegalArgumentException {

		try {
			// this is precicely what they want: everything is either on or off
			return Integer.parseInt(modeString) | NOT_READ | NOT_WRITE | NOT_EXECUTE;
		} catch (NumberFormatException e) {
		}

		if ((modeString.length() % 2 != 0) || (modeString.length() / 2 < 1)) {
			throw new IllegalArgumentException();
		}

		int retval = 0;

		while (true) {
			switch (modeString.charAt(1)) {
			case 'r':
				if (modeString.charAt(0) == '+') {
					retval |= READ;
				} else {
					retval |= NOT_READ;
				}
				break;
			case 'w':
				if (modeString.charAt(0) == '+') {
					retval |= WRITE;
				} else {
					retval |= NOT_WRITE;
				}
				break;
			case 'x':
				if (modeString.charAt(0) == '+') {
					retval |= EXECUTE;
				} else {
					retval |= NOT_EXECUTE;
				}
				break;
			default:
				throw new IllegalArgumentException();
			}
			if (modeString.length() / 2 == 1) {
				break;
			} else {
				modeString = modeString.substring(2);
			}
		}

		return retval;
	}

	public Identity downloadIdentity(String sourcePath, boolean isLocalSource)
			throws ConfigurationException, FileNotFoundException, IOException,
			RNSException, GeneralSecurityException {

		
		if (isLocalSource) {
			InputStream in = new FileInputStream(sourcePath);;
			try {
				CertificateFactory cf = CertificateFactory.getInstance("X.509");
				X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
				X509Certificate[] chain = {cert};
				return new X509Identity(chain);
			} finally {
				StreamUtils.close(in);
			}
		} 
		
		RNSPath current = RNSPath.getCurrent();
		RNSPath path = current.lookup(sourcePath,
				RNSPathQueryFlags.MUST_EXIST);
		
		if (path.isFile()) {
			InputStream in = new ByteIOInputStream(path);
			try {
				CertificateFactory cf = CertificateFactory.getInstance("X.509");
				X509Certificate cert = (X509Certificate) cf.generateCertificate(in);
				X509Certificate[] chain = {cert};
				return new X509Identity(chain);
			} finally {
				StreamUtils.close(in);
			}
		} else if (path.isIDP()) {
			try {
				ArrayList<SignedAssertion> identities = 
					GamlLoginTool.doIdpLogin(path.getEndpoint(), null, 0);
				Attribute firstAttr = identities.get(0).getAttribute();
				if (firstAttr instanceof IdentityAttribute) {
					IdentityAttribute identAttr = (IdentityAttribute) firstAttr;
					return identAttr.getIdentity();
				}
			} catch (Throwable t) {
				throw new GeneralSecurityException(t.getMessage(), t);
			}
		}
		
		throw new RNSException(sourcePath + " is not of a valid identity type.");
	}

	public AuthZConfig modifyAuthZConfig(AuthZConfig config, PrintStream out,
			PrintStream err, BufferedReader in) throws IOException,
			AuthZSecurityException {

		boolean chosen = false;
		while (!chosen) {
			out.println("\nOptions:");
			out.println("  [1] Toggle message-level encryption requirement");
			out.println("  [2] Modify access control lists");
			out.println("  [3] Cancel");
			out.print("Please make a selection: ");
			out.flush();
		
			String input = in.readLine();
			out.println();
			int choice = 0;					
			try {
				choice = Integer.parseInt(input);
			} catch (NumberFormatException e) {
				out.println("Invalid choice.");
				continue;
			}
				
			switch (choice) {
			case 1:
				GamlAcl acl = GamlAcl.decodeAcl(config);
				acl.requireEncryption = !acl.requireEncryption;
				config = GamlAcl.encodeAcl(acl);
				chosen = true;
				break;
			case 2:
				CommandLine cLine;
				while (true) {
					out.println("Modification syntax:");
					out.println("  " + CHMOD_SYNTAX);
					out.print(">");
					out.flush();
		
					cLine = new CommandLine(in.readLine(), false);
					if (!validateChmodSyntax(cLine)) {
						out.println("Invalid syntax");
						continue;
					}
					break;
				}
				if (cLine.hasOption("username")) {
					config = chmod(config,
							cLine.hasFlag("local-src"),
							cLine.hasFlag("everyone"),
							cLine.getArgument(0),
							cLine.getOptionValue("username"), 
							cLine.getOptionValue("password"));
				} else {
					config = chmod(config,
							cLine.hasFlag("local-src"),
							cLine.hasFlag("everyone"),
							cLine.getArgument(0),
							cLine.getArgument(1), 
							null);
				}
				chosen = true;
				break;
			case 3:
				chosen = true;
			}
		}
		
		return config;
	}

	public boolean validateChmodSyntax(ICommandLine cLine) {
		
		// make sure we have the new perms and the cert file
		if (cLine.hasFlag("everyone")) {
			if ((cLine.numArguments() != 1) || (cLine.hasOption("local-src"))) {
				return false;
			}
		} else if (cLine.hasOption("username")) {
			// make sure password also supplied
			if (!cLine.hasOption("password")) {
				return false;
			}
		} else {
			if (cLine.numArguments() != 2) {
				return false;
			}
		}
		
		// make sure the new perms parses
		try {
			parseMode(cLine.getArgument(0));
		} catch (IllegalArgumentException e) {
			return false;
		}

		return true;
	}

	/**
	 * Parses the given command line and applies the indicated
	 * authz changes to the specified authz configuration.  Assumes
	 * that the syntax is valid (having been checked with 
	 * validateChmodSyntax())
	 */
	public AuthZConfig chmod(AuthZConfig config,
		boolean localSrc, boolean everyone, String permission, String user, String password)
			throws IOException, AuthZSecurityException 
	{
		if (config.get_any() == null) {
			return config;
		}
		
		int mode;
		String target;
		
		mode = parseMode(permission);

		Identity identity = null;

		if (password != null) {
		
			// username password
			identity = new UsernameTokenIdentity(user, password);

		} else {
			
			if (!everyone) {
	
				target = user;
				try {
					
					identity = downloadIdentity(target, localSrc);
		
				} catch (ConfigurationException e) {
					throw new AuthZSecurityException(
							"Could not load certificate file: " + e.getMessage(), e);
				} catch (FileNotFoundException e) {
					throw new AuthZSecurityException(
							"Could not load certificate file: " + e.getMessage(), e);
				} catch (RNSException e) {
					throw new AuthZSecurityException(
							"Could not load certificate file: " + e.getMessage(), e);
				} catch (GeneralSecurityException e) {
					throw new AuthZSecurityException(
							"Could not load certificate file: " + e.getMessage(), e);
				}
			}
		}

		GamlAcl acl = GamlAcl.decodeAcl(config);
		if ((mode & READ) > 0) {
			if (!acl.readAcl.contains(identity))
				acl.readAcl.add(identity);
		} else if ((mode & NOT_READ) > 0) {
			acl.readAcl.remove(identity);
		}

		if ((mode & WRITE) > 0) {
			if (!acl.writeAcl.contains(identity))
				acl.writeAcl.add(identity);
		} else if ((mode & NOT_WRITE) > 0) {
			acl.writeAcl.remove(identity);
		}

		if ((mode & EXECUTE) > 0) {
			if (!acl.executeAcl.contains(identity))
				acl.executeAcl.add(identity);
		} else if ((mode & NOT_EXECUTE) > 0) {
			acl.executeAcl.remove(identity);
		}

		return GamlAcl.encodeAcl(acl);
	}
}