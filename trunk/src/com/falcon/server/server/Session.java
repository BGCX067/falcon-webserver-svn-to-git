package com.falcon.server.server;

import javax.servlet.http.HttpSession;

public interface Session {

	public boolean isValid();

	public String getId();

	/**
	 * Update the accessed time information for this session. This method should
	 * be called by the context when a request comes in for a particular
	 * session, even if the application does not reference it.
	 */
	public void access();

	/**
	 * Return the <code>HttpSession</code> for which this object is the facade.
	 */
	public HttpSession getSession();

	public void setValid(boolean isValid);

	public void setCreationTime(long time);

	public void setId(String id);

	public void setMaxInactiveInterval(int interval);

	public void setNew(boolean isNew);
}
