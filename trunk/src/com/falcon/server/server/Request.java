package com.falcon.server.server;

import javax.servlet.http.HttpServletRequest;

import com.falcon.server.server.core.ServletDef;

public interface Request extends HttpServletRequest {

	public Session getSessionInternal(boolean create);

	public void setServletDef(ServletDef servlet);

	public ServletDef getServletDef();
	
	public Context getContext();

}
