package com.falcon.server.server;

import javax.servlet.ServletContext;

public interface Context extends ServletContext {

	public Object[] getApplicationEventListeners();

	public ClassLoader getContextClassloader();

	public String getContextName();

	public Manager getManager();

	public boolean getCookies();

	public String getEncodedPath();
	
	public Wrapper getWrapper();

}
