package com.falcon.server.server.core;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

import javax.servlet.Filter;

import com.falcon.server.util.Enumerator;

/**
 * Representation of a filter definition for a web application, as represented
 * in a <code>&lt;filter&gt;</code> element in the deployment descriptor.
 * 
 */

@SuppressWarnings("serial")
public class FilterDef implements Serializable {

	// ------------------------------------------------------------- Properties
	/**
	 *init parameter
	 * 
	 */
	Map<String, String> initParams = new HashMap<String, String>();

	Filter filterInstance;

	public Filter getFilterInstance() {
		return filterInstance;
	}

	public void setFilterInstance(Filter filterInstance) {
		this.filterInstance = filterInstance;
	}

	/**
	 * The description of this filter.
	 */
	private String description = null;

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
	private String filterClass = null;

	public String getFilterClass() {
		return (this.filterClass);
	}

	public void setFilterClass(String filterClass) {
		this.filterClass = filterClass;
	}

	/**
	 * The name of this filter, which must be unique among the filters defined
	 * for a particular web application.
	 */
	private String filterName = null;

	public String getFilterName() {
		return (this.filterName);
	}

	public void setFilterName(String filterName) {
		this.filterName = filterName;
	}

	/**
	 * The large icon associated with this filter.
	 */
	private String largeIcon = null;

	public String getLargeIcon() {
		return (this.largeIcon);
	}

	public void setLargeIcon(String largeIcon) {
		this.largeIcon = largeIcon;
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

	/**
	 * The small icon associated with this filter.
	 */
	private String smallIcon = null;

	public String getSmallIcon() {
		return (this.smallIcon);
	}

	public void setSmallIcon(String smallIcon) {
		this.smallIcon = smallIcon;
	}

	// --------------------------------------------------------- Public Methods

	/**
	 * Add an initialization parameter to the set of parameters associated with
	 * this filter.
	 * 
	 * @param name
	 *            The initialization parameter name
	 * @param value
	 *            The initialization parameter value
	 */
	public void addInitParameter(String key, String value) {
		synchronized (initParams) {
			initParams.put(key, value);
		}
	}

	/**
	 * Render a String representation of this object.
	 */
	public String toString() {

		StringBuffer sb = new StringBuffer("FilterDef[");
		sb.append("filterName=");
		sb.append(this.filterName);
		sb.append(", filterClass=");
		sb.append(this.filterClass);
		sb.append("]");
		return (sb.toString());

	}

}
