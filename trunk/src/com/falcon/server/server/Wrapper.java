package com.falcon.server.server;

import java.util.Collection;

import javax.servlet.FilterConfig;

import com.falcon.server.server.core.FilterDef;
import com.falcon.server.server.core.FilterMap;
import com.falcon.server.server.core.Mapper;
import com.falcon.server.server.core.ServletDef;
import com.falcon.server.server.core.WebAppConfiguration;

public interface Wrapper {

	public void addFilterDef(FilterDef filterDef);

	public void addServletDef(ServletDef servletDef);

	public void addFilterMap(FilterMap filterMap);

	/**
	 * Add a new servlet mapping, replacing any existing mapping for the
	 * specified pattern.
	 * 
	 * @param pattern
	 *            URL pattern to be mapped
	 * @param name
	 *            Name of the corresponding servlet to execute
	 * 
	 */
	public void addServletMapping(String contextPath,String pattern, String name);

	/**
	 * Return the filter definition for the specified filter name, if any;
	 * otherwise return <code>null</code>.
	 * 
	 * @param filterName
	 *            Filter name to look up
	 */
	public FilterDef findFilterDef(String filterName);

	/**
	 * Return the servlet definition for the specified servlet name, if any;
	 * otherwise return <code>null</code>.
	 * 
	 * @param filterName
	 *            Filter name to look up
	 */
	public ServletDef findServletDef(String servletName);

	/**
	 * Return the servlet name mapped by the specified pattern (if any);
	 * otherwise return <code>null</code>.
	 * 
	 * @param pattern
	 *            Pattern for which a mapping is requested
	 */
	public String findServletMapping(String pattern);

	/**
	 * Return the patterns of all defined servlet mappings for this Context. If
	 * no mappings are defined, a zero-length array is returned.
	 */
	public String[] findServletMappings();

	/**
	 * Return the set of filter mappings for this Context.
	 */
	public FilterMap[] findFilterMaps();

	/**
	 * Configure and initialize the set of filters Return <code>true</code> if
	 * all filter initialization completed successfully, or <code>false</code>
	 * otherwise.
	 */
	public boolean ConfigureAndInitializeFilter();

	/**
	 * Find and return the initialized <code>FilterConfig</code> for the
	 * specified filter name, if any; otherwise return <code>null</code>.
	 * 
	 * @param name
	 *            Name of the desired filter
	 */
	public FilterConfig findFilterConfig(String name);

	public void addJspMapping(String contextPath,String pattern);

	public void addContext(Context context,String[] welcomeResources);

	public Context getContext();

	public Mapper getMapper();

	public Collection<ServletDef> getServletDefs();

	public void setServletContext(WebAppConfiguration context);

	public void removeServlet(String name);

	public void removeFilter(String name);

	public void removeAllServlets();

	public void removeAllFilter();

}