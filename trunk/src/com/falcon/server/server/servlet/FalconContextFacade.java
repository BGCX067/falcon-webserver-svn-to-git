package com.falcon.server.server.servlet;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import com.falcon.server.server.core.WebAppConfiguration;

/**
 * Facade object which masks the internal <code>ApplicationContext</code> object
 * from the web application.
 * 
 */
@SuppressWarnings("unchecked")
public final class FalconContextFacade implements ServletContext {

	// ----------------------------------------------------------- Constructors

	/**
	 * Construct a new instance of this class, associated with the specified
	 * Context instance.
	 * 
	 * @param context
	 *            The associated Context instance
	 */
	public FalconContextFacade(WebAppConfiguration context) {
		super();
		this.context = context;

	}

	/**
	 * Wrapped application context.
	 */
	private WebAppConfiguration context = null;

	// ------------------------------------------------- ServletContext Methods

	public int getMajorVersion() {
		return context.getMajorVersion();
	}

	public int getMinorVersion() {
		return context.getMinorVersion();
	}

	public String getMimeType(String file) {
		return context.getMimeType(file);
	}

	public Set getResourcePaths(String path) {
		return context.getResourcePaths(path);
	}

	public URL getResource(String path) throws MalformedURLException {
		return context.getResource(path);
	}

	public InputStream getResourceAsStream(String path) {
		return context.getResourceAsStream(path);
	}

	public RequestDispatcher getRequestDispatcher(final String path) {
		return context.getRequestDispatcher(path);
	}

	public RequestDispatcher getNamedDispatcher(String name) {
		return context.getNamedDispatcher(name);
	}

	public Servlet getServlet(String name) throws ServletException {
		return context.getServlet(name);
	}

	public Enumeration getServlets() {
		return context.getServlets();
	}

	public Enumeration getServletNames() {
		return context.getServletNames();
	}

	public void log(String msg) {
		context.log(msg);
	}

	public void log(Exception exception, String msg) {
		context.log(exception, msg);
	}

	public void log(String message, Throwable throwable) {
		context.log(message, throwable);
	}

	public String getRealPath(String path) {
		return context.getRealPath(path);
	}

	public String getServerInfo() {
		return context.getServerInfo();
	}

	public String getInitParameter(String name) {
		return context.getInitParameter(name);
	}

	public Enumeration getInitParameterNames() {
		return context.getInitParameterNames();
	}

	public Object getAttribute(String name) {
		return context.getAttribute(name);
	}

	public Enumeration getAttributeNames() {
		return context.getAttributeNames();
	}

	public void setAttribute(String name, Object object) {
		context.setAttribute(name, object);
	}

	public void removeAttribute(String name) {
		context.removeAttribute(name);
	}

	public String getServletContextName() {
		return context.getServletContextName();
	}

	public String getContextPath() {
		return context.getContextPath();
	}

	public ServletContext getContext(String uripath) {
		return context.getContext(uripath);
	}
}
