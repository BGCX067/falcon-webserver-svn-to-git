package com.falcon.server.server.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;

import com.falcon.server.util.Enumerator;

public class ServletDef implements Serializable {

	private static final long serialVersionUID = 7354635309625752965L;
	/**
	 * The description of this filter.
	 */
	private String description = null;

	/**
	 * Mappings associated with the servlet.
	 */
	protected List<String> mappings = new ArrayList<String>();

	/**
	 *init parameter
	 * 
	 */
	Map<String, String> initParams = new HashMap<String, String>();
	
	/**
	 * class load
	 */
	ClassLoader loader;
	
	public ClassLoader getLoader() {
		return loader;
	}

	public void setLoader(ClassLoader loader) {
		this.loader = loader;
	}

	Servlet instance;

	public Servlet getInstance() {
		return instance;
	}

	public void setInstance(Servlet instance) {
		this.instance = instance;
	}

	public String getDescription() {
		return (this.description);
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * The display name of this filter.
	 */
	private String displayName = null;

	public String getDisplayName() {
		return (this.displayName);
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * The fully qualified name of the Java class that implements this filter.
	 */
	private String servletClass = null;

	public String getServletClass() {
		return servletClass;
	}

	public void setServletClass(String servletClass) {
		this.servletClass = servletClass;
	}

	/**
	 * The name of this filter, which must be unique among the filters defined
	 * for a particular web application.
	 */
	private String servletName = null;

	public String getServletName() {
		return servletName;
	}

	public void setServletName(String servletName) {
		this.servletName = servletName;
	}

	/**
	 * The load-on-startup order value (negative value means load on first call)
	 * for this servlet.
	 */
	protected int loadOnStartup = -1;

	/**
	 * Return the load-on-startup order value (negative value means load on
	 * first call).
	 */
	public int getLoadOnStartup() {
		return loadOnStartup;
	}

	/**
	 * Set the load-on-startup order value (negative value means load on first
	 * call).
	 * 
	 * @param value
	 *            New load-on-startup value
	 */
	public void setLoadOnStartup(int value) {
		loadOnStartup = value;
	}

	public void addMapping(String mapping) {
		synchronized (mappings) {
			mappings.add(mapping);
		}
	}

	public void addInitParameter(String key, String value) {
		synchronized (initParams) {
			initParams.put(key, value);
		}
	}

	public void addInitParameter(Map<String, String> map) {
		synchronized (initParams) {
			initParams.putAll(map);
		}
	}

	public String getInitParameter(String name) {
		synchronized (initParams) {
			return initParams.get(name);
		}
	}

	public Enumeration<String> getInitParameterNames() {
		synchronized (initParams) {
			return new Enumerator<String>(initParams.keySet());
		}
	}

	public void removeMapping(String mapping) {

		synchronized (mappings) {
			mappings.remove(mapping);
		}
	}
}
