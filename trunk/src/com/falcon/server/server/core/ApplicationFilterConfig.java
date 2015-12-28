package com.falcon.server.server.core;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import com.falcon.server.server.Context;

/**
 * Implementation of a <code>javax.servlet.FilterConfig</code> useful in
 * managing the filter instances instantiated when a web application is first
 * started.
 * 
 */

final class ApplicationFilterConfig implements FilterConfig, Serializable {

	// ----------------------------------------------------------- Constructors

	private static final long serialVersionUID = -8084058239988849263L;

	public ApplicationFilterConfig(Context context, FilterDef filterDef) {
		setFilterDef(filterDef);
		this.context = context;

	}

	// ----------------------------------------------------- Instance Variables

	private Context context;
	/**
	 * The application Filter we are configured for.
	 */
	private transient Filter filter = null;

	/**
	 * The <code>FilterDef</code> that defines our associated Filter.
	 */
	private FilterDef filterDef = null;

	/**
	 * Restricted filters (which can only be loaded by a privileged webapp).
	 */
	protected static Properties restrictedFilters = null;

	// --------------------------------------------------- FilterConfig Methods

	/**
	 * Return the name of the filter we are configuring.
	 */
	public String getFilterName() {

		return (filterDef.getFilterName());

	}

	/**
	 * Return a <code>String</code> containing the value of the named
	 * initialization parameter, or <code>null</code> if the parameter does not
	 * exist.
	 * 
	 * @param name
	 *            Name of the requested initialization parameter
	 */
	public String getInitParameter(String name) {

		return filterDef.getInitParameter(name);
	}

	/**
	 * Return an <code>Enumeration</code> of the names of the initialization
	 * parameters for this Filter.
	 */
	@SuppressWarnings("unchecked")
	public Enumeration getInitParameterNames() {

		return filterDef.getInitParameterNames();

	}

	/**
	 * Return the ServletContext of our associated web application.
	 */
	public ServletContext getServletContext() {

		return (this.context);

	}

	/**
	 * Return a String representation of this object.
	 */
	public String toString() {

		StringBuffer sb = new StringBuffer("ApplicationFilterConfig[");
		sb.append("name=");
		sb.append(filterDef.getFilterName());
		sb.append(", filterClass=");
		sb.append(filterDef.getFilterClass());
		sb.append("]");
		return (sb.toString());

	}

	/**
	 * Return the filter definition we are configured for.
	 */
	FilterDef getFilterDef() {

		return (this.filterDef);

	}

	/**
	 * Return the application Filter we are configured for.
	 * 
	 * @exception ClassCastException
	 *                if the specified class does not implement the
	 *                <code>javax.servlet.Filter</code> interface
	 * @exception ClassNotFoundException
	 *                if the filter class cannot be found
	 * @exception IllegalAccessException
	 *                if the filter class cannot be publicly instantiated
	 * @exception InstantiationException
	 *                if an exception occurs while instantiating the filter
	 *                object
	 * @exception ServletException
	 *                if thrown by the filter's init() method
	 */
	public Filter getFilter() throws ClassNotFoundException, InstantiationException,
			IllegalAccessException, ServletException {

		// Return the existing filter instance, if any
		if (this.filter != null)
			return (this.filter);

		// Identify the class loader we will be using
		String filterClass = filterDef.getFilterClass();
		ClassLoader classLoader = context.getContextClassloader();

		ClassLoader oldCtxClassLoader = Thread.currentThread().getContextClassLoader();

		Thread.currentThread().setContextClassLoader(classLoader);

		Class<?> clazz = classLoader.loadClass(filterClass);
		this.filter = (Filter) clazz.newInstance();
		filter.init(this);
		Thread.currentThread().setContextClassLoader(oldCtxClassLoader);
		return (this.filter);
	}

	/**
	 * Set the filter definition we are configured for. This has the side effect
	 * of instantiating an instance of the corresponding filter class.
	 * 
	 * @param filterDef
	 *            The new filter definition
	 * 
	 * @exception ClassCastException
	 *                if the specified class does not implement the
	 *                <code>javax.servlet.Filter</code> interface
	 * @exception ClassNotFoundException
	 *                if the filter class cannot be found
	 * @exception IllegalAccessException
	 *                if the filter class cannot be publicly instantiated
	 * @exception InstantiationException
	 *                if an exception occurs while instantiating the filter
	 *                object
	 * @exception ServletException
	 *                if thrown by the filter's init() method
	 * @throws NamingException
	 * @throws InvocationTargetException
	 */
	void setFilterDef(FilterDef filterDef) {

		this.filterDef = filterDef;
		if (filterDef == null) {
			// Release any previously allocated filter instance
			if (this.filter != null) {
				filter.destroy();
			}
			this.filter = null;
		}
	}
}
