package com.falcon.server.server.servlet;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import com.falcon.server.server.core.ServletDef;

/**
 * Facade for the <b>StandardWrapper</b> object.
 * 
 */

public final class FalconServletConfigFacade implements ServletConfig {

	// ----------------------------------------------------------- Constructors

	/**
	 * Create a new facede around a StandardWrapper.
	 */
	public FalconServletConfigFacade(ServletDef config, ServletContext context) {

		super();
		this.config = config;
		this.context = context;

	}

	// ----------------------------------------------------- Instance Variables

	private ServletDef config = null;

	/**
	 * Wrapped context (facade).
	 */
	private ServletContext context = null;

	// -------------------------------------------------- ServletConfig Methods

	public String getServletName() {
		return config.getServletName();
	}

	public ServletContext getServletContext() {

		return context;
	}

	public String getInitParameter(String name) {
		return config.getInitParameter(name);
	}

	public Enumeration getInitParameterNames() {
		return config.getInitParameterNames();
	}

}
