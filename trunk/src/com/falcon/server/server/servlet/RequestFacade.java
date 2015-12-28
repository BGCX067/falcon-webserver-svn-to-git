package com.falcon.server.server.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.falcon.server.util.StringManager;

/**
 * Facade class that wraps a Coyote request object. All methods are delegated to
 * the wrapped request.
 * 
 */

public class RequestFacade implements HttpServletRequest {

	// ----------------------------------------------------------- Constructors

	/**
	 * Construct a wrapper for the specified request.
	 * 
	 * @param request
	 *            The request to be wrapped
	 */
	public RequestFacade(FalconRequest request) {

		this.request = request;

	}

	// ----------------------------------------------------- Instance Variables

	/**
	 * The wrapped request.
	 */
	protected FalconRequest request = null;

	/**
	 * The string manager for this package.
	 */
	protected static StringManager sm = StringManager.getManager(Constants.Package);

	// --------------------------------------------------------- Public Methods

	/**
	 * Clear facade.
	 */
	public void clear() {
		request = null;
	}

	/**
	 * Prevent cloning the facade.
	 */
	protected Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	// ------------------------------------------------- ServletRequest Methods

	public Object getAttribute(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getAttribute(name);
	}

	public Enumeration getAttributeNames() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}
		return request.getAttributeNames();
	}

	public String getCharacterEncoding() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getCharacterEncoding();
	}

	public void setCharacterEncoding(String env) throws java.io.UnsupportedEncodingException {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		request.setCharacterEncoding(env);
	}

	public int getContentLength() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getContentLength();
	}

	public String getContentType() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getContentType();
	}

	public ServletInputStream getInputStream() throws IOException {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getInputStream();
	}

	public String getParameter(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getParameter(name);
	}

	public Enumeration getParameterNames() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getParameterNames();
	}

	public String[] getParameterValues(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}
		String[] ret = null;
		ret = request.getParameterValues(name);
		return ret;
	}

	public Map getParameterMap() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}
		return request.getParameterMap();
	}

	public String getProtocol() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getProtocol();
	}

	public String getScheme() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getScheme();
	}

	public String getServerName() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getServerName();
	}

	public int getServerPort() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getServerPort();
	}

	public BufferedReader getReader() throws IOException {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getReader();
	}

	public String getRemoteAddr() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRemoteAddr();
	}

	public String getRemoteHost() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRemoteHost();
	}

	public void setAttribute(String name, Object o) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		request.setAttribute(name, o);
	}

	public void removeAttribute(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		request.removeAttribute(name);
	}

	public Locale getLocale() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getLocale();
	}

	public Enumeration getLocales() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getLocales();
	}

	public boolean isSecure() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.isSecure();
	}

	public RequestDispatcher getRequestDispatcher(String path) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRequestDispatcher(path);
	}

	public String getRealPath(String path) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRealPath(path);
	}

	public String getAuthType() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getAuthType();
	}

	public Cookie[] getCookies() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}
		Cookie[] ret = null;
		ret = request.getCookies();
		return ret;
	}

	public long getDateHeader(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getDateHeader(name);
	}

	public String getHeader(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getHeader(name);
	}

	public Enumeration getHeaders(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}
		return request.getHeaders(name);
	}

	public Enumeration getHeaderNames() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getHeaderNames();
	}

	public int getIntHeader(String name) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getIntHeader(name);
	}

	public String getMethod() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getMethod();
	}

	public String getPathInfo() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getPathInfo();
	}

	public String getPathTranslated() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getPathTranslated();
	}

	public String getContextPath() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getContextPath();
	}

	public String getQueryString() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getQueryString();
	}

	public String getRemoteUser() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRemoteUser();
	}

	public boolean isUserInRole(String role) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.isUserInRole(role);
	}

	public java.security.Principal getUserPrincipal() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getUserPrincipal();
	}

	public String getRequestedSessionId() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRequestedSessionId();
	}

	public String getRequestURI() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRequestURI();
	}

	public StringBuffer getRequestURL() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRequestURL();
	}

	public String getServletPath() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getServletPath();
	}

	public HttpSession getSession(boolean create) {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getSession(create);
	}

	public HttpSession getSession() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return getSession(true);
	}

	public boolean isRequestedSessionIdValid() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.isRequestedSessionIdValid();
	}

	public boolean isRequestedSessionIdFromCookie() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.isRequestedSessionIdFromCookie();
	}

	public boolean isRequestedSessionIdFromURL() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.isRequestedSessionIdFromURL();
	}

	public boolean isRequestedSessionIdFromUrl() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.isRequestedSessionIdFromURL();
	}

	public String getLocalAddr() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getLocalAddr();
	}

	public String getLocalName() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getLocalName();
	}

	public int getLocalPort() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getLocalPort();
	}

	public int getRemotePort() {

		if (request == null) {
			throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
		}

		return request.getRemotePort();
	}

}
