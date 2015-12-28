package com.falcon.server.server.core;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import com.falcon.server.server.Context;
import com.falcon.server.server.Request;
import com.falcon.server.server.Response;
import com.falcon.server.server.Valve;

public class FalconWrapperValve implements Valve {

	public static final String info = "com.falcon.server.server.core.FalconWrapperValve/0.1";

	/**
	 * The next Valve in the pipeline this Valve is a component of.
	 */
	protected Valve next = null;

	public void backgroundProcess() {
		// TODO Auto-generated method stub

	}

	public void event(Request request, Response response) throws IOException, ServletException {
		// TODO Auto-generated method stub

	}

	public String getInfo() {
		return info;
	}

	public Valve getNext() {
		return next;
	}

	public void invoke(Request request, Response response) throws IOException, ServletException {

		ServletDef servletDef = request.getServletDef();
		Context context = request.getContext();

		Servlet servlet = servletDef.getInstance();
		if (servlet == null) {
			try {
				servlet = (Servlet) context.getContextClassloader().loadClass(
						servletDef.getServletClass()).newInstance();
				servletDef.setInstance(servlet);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
				ApplicationFilterFactory.REQUEST_INTEGER);
		request.setAttribute(ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR, request
				.getServletPath());

		ApplicationFilterFactory factory = ApplicationFilterFactory.getInstance();
		ApplicationFilterChain filterChain = factory.createFilterChain(request, context
				.getWrapper(), servlet, servletDef.getServletName());

		if ((servlet != null) && (filterChain != null)) {
			filterChain.doFilter(request, response);
		}
		// Release the filter chain (if any) for this request
		try {
			if (filterChain != null)
				filterChain.release();
		} catch (Throwable e) {
		}
		if (getNext() != null)
			getNext().invoke(request, response);
	}

	public void setNext(Valve valve) {
		next = valve;
	}
}