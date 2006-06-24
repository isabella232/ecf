package org.eclipse.ecf.provider.xmpp.identity;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.ecf.core.identity.BaseID;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.presence.IChatID;

public class XMPPID extends BaseID implements IChatID {

	private static final long serialVersionUID = 3257289140701049140L;
	public static final char USER_HOST_DELIMITER = '@';
	public static final char PORT_DELIMITER = ':';
	
	URI uri;
	String username;
	String hostname;
	int port = -1;
	
	protected static String fixEscape(String src) {
		if (src == null) return null;
		return src.replaceAll("%","%25");
	}
	public XMPPID(Namespace namespace, String unamehost) throws URISyntaxException {
		super(namespace);
		unamehost = fixEscape(unamehost);
		if (unamehost == null) throw new URISyntaxException(unamehost,"username/host string cannot be null");
		// Handle parsing of user@host string
		int atIndex = unamehost.lastIndexOf(USER_HOST_DELIMITER);
		if (atIndex == -1) throw new URISyntaxException(unamehost,"username/host string not valid.  Must be of form <username>@<hostname>[:port]");
		username = unamehost.substring(0,atIndex);
		hostname = unamehost.substring(atIndex+1);
		// Handle parsing of host:port
		atIndex = hostname.lastIndexOf(PORT_DELIMITER);
		if (atIndex != -1) {
			try {
				port = Integer.parseInt(hostname.substring(atIndex+1));
			} catch (NumberFormatException e) {
				throw new URISyntaxException(unamehost,"invalid port value");
			}			
			hostname = hostname.substring(0,atIndex);
		}
		uri = new URI(namespace.getScheme(),username,hostname,port,null,null,null);
	}
	
	protected int namespaceCompareTo(BaseID o) {
        return getName().compareTo(o.getName());
	}

	protected boolean namespaceEquals(BaseID o) {
		if (!(o instanceof XMPPID)) {
			return false;
		}
		XMPPID other = (XMPPID) o;
		return uri.equals(other.uri);
	}

	protected String namespaceGetName() {
		return getUsernameAtHost();
	}

	protected int namespaceHashCode() {
		return uri.hashCode();
	}

	protected URI namespaceToURI() throws URISyntaxException {
		return uri;
	}
	
	public String getUsername() {
		return username;
	}
	
	public String getHostname() {
		return hostname;
	}
	
	public int getPort() {
		return port;
	}
	public String getUsernameAtHost() {
		return getUsername()+"@"+getHostname();
	}
	public String toString() {
		StringBuffer sb = new StringBuffer("XMPPID[");
		sb.append(uri.toString()).append("]");
		return sb.toString();
	}
	public Object getAdapter(Class clazz) {
	    if (clazz.isInstance(this)) {
	    	return this;
	    } else return super.getAdapter(clazz);
	}
}
