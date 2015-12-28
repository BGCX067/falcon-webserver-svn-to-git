package com.falcon.server.server;

import java.io.IOException;

public interface Manager {

	public Session createSession(String sessionId);

	public void backgroundProcess();
	
	public Context getContext();
	
	public void remove(Session session);
	
	public void add(Session session);
	
    /**
     * Return the active Session, associated with this Manager, with the
     * specified session id (if any); otherwise return <code>null</code>.
     *
     * @param id The session id for the session to be returned
     *
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     * @exception IOException if an input/output error occurs while
     *  processing this request
     */
    public Session findSession(String id) throws IOException;

}
