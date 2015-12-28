package com.falcon.server.server.servlet;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import com.falcon.server.util.Enumerator;

public class FalconConfig implements ServletConfig {

	private ServletContext context;

	private HashMap<String, String> init_params;

	private String servletName;

	public FalconConfig(ServletContext context) {
		this(context, null, "undefined");
	}

	public FalconConfig(ServletContext context, HashMap<String, String> initParams,
			String servletName) {
		this.context = context;
		this.init_params = initParams;
		this.servletName = servletName;
	}

	// Methods from ServletConfig.

	// / Returns the context for the servlet.
	public ServletContext getServletContext() {
		return context;
	}

	// / Gets an initialization parameter of the servlet.
	// @param name the parameter name
	public String getInitParameter(String name) {
		// This server supports servlet init params. :)
		synchronized (init_params) {
			if (init_params != null)
				return (String) init_params.get(name);
			return null;
		}
	}

	// / Gets the names of the initialization parameters of the servlet.
	// @param name the parameter name
	@SuppressWarnings("unchecked")
	public Enumeration getInitParameterNames() {
		// This server does:) support servlet init params.
		synchronized (init_params) {
			if (init_params != null)
				return new Enumerator(init_params.keySet());
			return new Enumerator(new HashSet());
		}
	}

	// 2.2
	public String getServletName() {
		return servletName;
	}
}