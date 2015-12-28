package com.falcon.server.server.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import javax.servlet.FilterConfig;

import org.apache.log4j.Logger;

import com.falcon.server.server.Context;
import com.falcon.server.server.Wrapper;
import com.falcon.server.util.RequestUtil;

/**
 * 当前给配置只是处理filter[filter-mapping]、servlet[servlet-mapping]
 * 
 * @author panye
 * 
 */
public class FalconWrapper implements Wrapper {

	Context context;

	public FalconWrapper(Context context) {
		this.context = context;
		mapper.setContext(context.getContextName());
	}

	Logger log = Logger.getLogger(getClass().getName());
	/**
	 * The set of filter definitions for this application, keyed by filter name.
	 */
	private HashMap<String, FilterDef> filterDefs = new HashMap<String, FilterDef>();
	/**
	 * The servlet mappings for this web application, keyed by matching pattern.
	 */
	private HashMap<String, String> servletMappings = new HashMap<String, String>();
	/**
	 * The set of servlet definitions for this application, keyed by servlet
	 * name.
	 */
	private HashMap<String, ServletDef> servletDefs = new HashMap<String, ServletDef>();

	/**
	 * The set of filter mappings for this application, in the order they were
	 * defined in the deployment descriptor.
	 */
	private FilterMap filterMaps[] = new FilterMap[0];
	/**
	 * The set of filter configurations (and associated filter instances) we
	 * have initialized, keyed by filter name.
	 */
	private HashMap<String, FilterConfig> filterConfigs = new HashMap<String, FilterConfig>();

	public static Mapper mapper = new Mapper();

	public void addFilterDef(FilterDef filterDef) {

		synchronized (filterDefs) {
			filterDefs.put(filterDef.getFilterName(), filterDef);
		}
	}

	public void addServletDef(ServletDef servletDef) {

		synchronized (servletDefs) {
			servletDefs.put(servletDef.getServletName(), servletDef);
		}
	}

	public void addFilterMap(FilterMap filterMap) {

		// Validate the proposed filter mapping
		String filterName = filterMap.getFilterName();
		String[] servletNames = filterMap.getServletNames();
		String[] urlPatterns = filterMap.getURLPatterns();
		if (findFilterDef(filterName) == null)
			throw new IllegalArgumentException("Can't find filter " + filterName);

		if (!filterMap.getMatchAllServletNames() && !filterMap.getMatchAllUrlPatterns()
				&& (servletNames.length == 0) && (urlPatterns.length == 0))
			throw new IllegalArgumentException("filter mapping is error");

		for (int i = 0; i < urlPatterns.length; i++) {
			if (!validateURLPattern(urlPatterns[i])) {
				throw new IllegalArgumentException("filter url pattern " + urlPatterns[i]
						+ " is  not right");
			}
		}

		// Add this filter mapping to our registered set
		synchronized (filterMaps) {
			FilterMap results[] = new FilterMap[filterMaps.length + 1];
			System.arraycopy(filterMaps, 0, results, 0, filterMaps.length);
			results[filterMaps.length] = filterMap;
			filterMaps = results;
		}
	}

	public void addServletMapping(String contextPath, String pattern, String name,
			boolean jspWildCard) {
		// Validate the proposed mapping
		if (findServletDef(name) == null)
			throw new IllegalArgumentException(String.format(
					"The servlet mapping can't find servlet s%", name));
		pattern = adjustURLPattern(RequestUtil.URLDecode(pattern));
		if (!validateURLPattern(pattern))
			throw new IllegalArgumentException("filter url pattern " + pattern + " is  not right");
		// Add this mapping to our registered set
		synchronized (servletMappings) {
			String name2 = (String) servletMappings.get(pattern);
			if (name2 != null) {
				// Don't allow more than one servlet on the same pattern
				ServletDef wrapper = findServletDef(name2);
				wrapper.removeMapping(pattern);
			}
			servletMappings.put(pattern, name);
		}
		ServletDef wrapper = findServletDef(name);
		wrapper.addMapping(pattern);
		mapper.addServlet(contextPath, pattern, findServletDef(name));
	}

	public void addJspMapping(String contextPath, String pattern) {
		String servletName = findServletMapping("*.jsp");
		if (servletName == null) {
			servletName = "jsp";
		}

		if (findServletDef(servletName) != null) {
			addServletMapping(contextPath, pattern, servletName, true);
		} else {
			if (log.isDebugEnabled())
				log.debug("Skiping " + pattern + " , no servlet " + servletName);
		}
	}

	public FilterDef findFilterDef(String filterName) {

		synchronized (filterDefs) {
			return ((FilterDef) filterDefs.get(filterName));
		}
	}

	public ServletDef findServletDef(String servletName) {

		synchronized (servletDefs) {
			return ((ServletDef) servletDefs.get(servletName));
		}

	}

	public String findServletMapping(String pattern) {

		synchronized (servletMappings) {
			return ((String) servletMappings.get(pattern));
		}

	}

	public String[] findServletMappings() {

		synchronized (servletMappings) {
			String results[] = new String[servletMappings.size()];
			return ((String[]) servletMappings.keySet().toArray(results));
		}

	}

	/**
	 * Return the set of filter mappings for this Context.
	 */
	public FilterMap[] findFilterMaps() {

		return (filterMaps);

	}

	public Collection<ServletDef> getServletDefs() {
		return servletDefs.values();
	}

	public void removeServlet(String name) {
		synchronized (servletDefs) {
			ServletDef def = servletDefs.remove(name);
			def.getInstance().destroy();
		}
	}

	public void removeServletMapping(String pattern) {

		synchronized (servletMappings) {
			servletMappings.remove(pattern);
		}
	}

	public void removeFilter(String name) {
		synchronized (filterDefs) {
			FilterDef def = filterDefs.remove(name);
			def.getFilterInstance().destroy();
			for (FilterMap map : filterMaps) {
				if (map.getFilterName().equals(name)) {
					map = null;
				}
			}
		}
	}

	public void removeAllServlets() {
		synchronized (servletDefs) {
			servletDefs.clear();
			servletMappings.clear();
		}
	}

	public void removeAllFilter() {
		synchronized (filterDefs) {
			filterDefs.clear();
			filterMaps = null;
		}
	}

	/**
	 * Validate the syntax of a proposed <code>&lt;url-pattern&gt;</code> for
	 * conformance with specification requirements.
	 * 
	 * @param urlPattern
	 *            URL pattern to be validated
	 */
	private boolean validateURLPattern(String urlPattern) {

		if (urlPattern == null)
			return (false);
		if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
			return (false);
		}
		if (urlPattern.startsWith("*.")) {
			if (urlPattern.indexOf('/') < 0) {
				checkUnusualURLPattern(urlPattern);
				return (true);
			} else
				return (false);
		}
		if ((urlPattern.startsWith("/")) && (urlPattern.indexOf("*.") < 0)) {
			checkUnusualURLPattern(urlPattern);
			return (true);
		} else
			return (false);
	}

	/**
	 * Adjust the URL pattern to begin with a leading slash, if appropriate
	 * (i.e. we are running a servlet 2.2 application). Otherwise, return the
	 * specified URL pattern unchanged.
	 * 
	 * @param urlPattern
	 *            The URL pattern to be adjusted (if needed) and returned
	 */
	protected String adjustURLPattern(String urlPattern) {

		if (urlPattern == null)
			return (urlPattern);
		if (urlPattern.startsWith("/") || urlPattern.startsWith("*."))
			return (urlPattern);
		return null;
	}

	/**
	 * Check for unusual but valid <code>&lt;url-pattern&gt;</code>s. See
	 * Bugzilla 34805, 43079 & 43080
	 */
	private void checkUnusualURLPattern(String urlPattern) {
		if (log.isInfoEnabled()) {
			if (urlPattern.endsWith("*")
					&& (urlPattern.length() < 2 || urlPattern.charAt(urlPattern.length() - 2) != '/')) {
				log.info("Suspicious url pattern: \"" + urlPattern + "\"" + "- see"
						+ " section SRV.11.2 of the Servlet specification");
			}
		}
	}

	/**
	 * Configure and initialize the set of filters Return <code>true</code> if
	 * all filter initialization completed successfully, or <code>false</code>
	 * otherwise.
	 */
	public boolean ConfigureAndInitializeFilter() {

		log.info("Configure and initialize the set of filters");
		// Instantiate and record a FilterConfig for each defined filter
		boolean ok = true;
		synchronized (filterConfigs) {
			filterConfigs.clear();
			Iterator<String> names = filterDefs.keySet().iterator();
			while (names.hasNext()) {
				String name = (String) names.next();
				if (log.isDebugEnabled())
					log.debug(" Starting filter '" + name + "'");
				ApplicationFilterConfig filterConfig = null;
				try {
					filterConfig = new ApplicationFilterConfig(context, (FilterDef) filterDefs
							.get(name));
					filterConfigs.put(name, filterConfig);
				} catch (Throwable t) {
					log.error("Configure and initialize filter error");
					ok = false;
				}
			}
		}
		return ok;
	}

	/**
	 * Add a new servlet mapping, replacing any existing mapping for the
	 * specified pattern.
	 * 
	 * @param pattern
	 *            URL pattern to be mapped
	 * @param name
	 *            Name of the corresponding servlet to execute
	 * 
	 * @exception IllegalArgumentException
	 *                if the specified servlet name is not known to this Context
	 */
	public void addServletMapping(String contextPath, String pattern, String name) {
		addServletMapping(contextPath, pattern, name, false);
	}

	/**
	 * Find and return the initialized <code>FilterConfig</code> for the
	 * specified filter name, if any; otherwise return <code>null</code>.
	 * 
	 * @param name
	 *            Name of the desired filter
	 */
	public FilterConfig findFilterConfig(String name) {

		return ((FilterConfig) filterConfigs.get(name));

	}

	public void setServletContext(WebAppConfiguration context) {
		this.context = context;
	}

	public Context getContext() {
		return context;
	}

	/**
	 * Get the mapper associated with the wrapper.
	 */
	public Mapper getMapper() {
		return (mapper);
	}

	public void addContext(Context context,String[] welcomeResources) {
		mapper.addContext(context.getContextPath(), context,welcomeResources);
	}
}
