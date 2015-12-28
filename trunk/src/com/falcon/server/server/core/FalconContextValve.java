package com.falcon.server.server.core;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletResponse;

import com.falcon.server.server.Context;
import com.falcon.server.server.Request;
import com.falcon.server.server.Response;
import com.falcon.server.server.Valve;
import com.falcon.server.util.Globals;

public class FalconContextValve implements Valve {

	public static final String info = "com.falcon.server.server.core.FalconContextValve/0.1";

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

		String requestPath = request.getRequestURI();
		if ((requestPath.indexOf("/META-INF/") != -1)
				|| (requestPath.equalsIgnoreCase("/META-INF"))
				|| (requestPath.indexOf("/WEB-INF/") != -1)
				|| (requestPath.equalsIgnoreCase("/WEB-INF"))) {
			notFound(response);
			return;
		}

		Context context = request.getContext();
		if (context == null)
			throw new NullPointerException("Context is null");

		Object instances[] = request.getContext().getApplicationEventListeners();

		ServletRequestEvent event = null;

		if ((instances != null) && (instances.length > 0)) {
			event = new ServletRequestEvent(context, request);
			// create pre-service event
			for (int i = 0; i < instances.length; i++) {
				if (instances[i] == null)
					continue;
				if (!(instances[i] instanceof ServletRequestListener))
					continue;
				ServletRequestListener listener = (ServletRequestListener) instances[i];
				try {
					listener.requestInitialized(event);
				} catch (Throwable t) {
					ServletRequest sreq = request;
					sreq.setAttribute(Globals.EXCEPTION_ATTR, t);
					return;
				}
			}
		}

		if (getNext() != null)
			getNext().invoke(request, response);

		if ((instances != null) && (instances.length > 0)) {
			// create post-service event
			for (int i = 0; i < instances.length; i++) {
				if (instances[i] == null)
					continue;
				if (!(instances[i] instanceof ServletRequestListener))
					continue;
				ServletRequestListener listener = (ServletRequestListener) instances[i];
				try {
					listener.requestDestroyed(event);
				} catch (Throwable t) {
					ServletRequest sreq = request;
					sreq.setAttribute(Globals.EXCEPTION_ATTR, t);
				}
			}
		}

	}

	/**
	 * Report a "not found" error for the specified resource. FIXME: We should
	 * really be using the error reporting settings for this web application,
	 * but currently that code runs at the wrapper level rather than the context
	 * level.
	 * 
	 * @param response
	 *            The response we are creating
	 */
	private void notFound(HttpServletResponse response) {

		try {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		} catch (IllegalStateException e) {
			;
		} catch (IOException e) {
			;
		}
	}

	public void setNext(Valve valve) {
		next = valve;
	}
}
