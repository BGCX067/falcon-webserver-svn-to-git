package com.falcon.server.server.core;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Implementation of <code>javax.servlet.FilterChain</code> used to manage the
 * execution of a set of filters for a particular request. When the set of
 * defined filters has all been executed, the next call to
 * <code>doFilter()</code> will execute the servlet's <code>service()</code>
 * method itself.
 * 
 */

public class ApplicationFilterChain implements FilterChain {

	// -------------------------------------------------------------- Constants

	public static final int INCREMENT = 10;

	// ----------------------------------------------------------- Constructors

	/**
	 * Construct a new chain instance with no defined filters.
	 */
	public ApplicationFilterChain() {

		super();

	}

	// ----------------------------------------------------- Instance Variables

	/**
	 * Filters.
	 */
	private ApplicationFilterConfig[] filters = new ApplicationFilterConfig[0];

	/**
	 * The int which is used to maintain the current position in the filter
	 * chain.
	 */
	private int pos = 0;

	/**
	 * The int which gives the current number of filters in the chain.
	 */
	private int n = 0;

	/**
	 * The servlet instance to be executed by this chain.
	 */
	private Servlet servlet = null;
	// ---------------------------------------------------- FilterChain Methods

	/**
	 * Invoke the next filter in this chain, passing the specified request and
	 * response. If there are no more filters in this chain, invoke the
	 * <code>service()</code> method of the servlet itself.
	 * 
	 * @param request
	 *            The servlet request we are processing
	 * @param response
	 *            The servlet response we are creating
	 * 
	 * @exception IOException
	 *                if an input/output error occurs
	 * @exception ServletException
	 *                if a servlet exception occurs
	 */
	public void doFilter(ServletRequest request, ServletResponse response) throws IOException,
			ServletException {

		internalDoFilter(request, response);
	}

	private void internalDoFilter(ServletRequest request, ServletResponse response)
			throws IOException, ServletException {

		if (pos < n) {
			ApplicationFilterConfig filterConfig = filters[pos++];
			Filter filter = null;
			try {
				filter = filterConfig.getFilter();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			filter.doFilter(request, response, this);
			return;
		}

		// We fell off the end of the chain -- call the servlet instance
		if ((request instanceof HttpServletRequest) && (response instanceof HttpServletResponse)) {
			servlet.service((HttpServletRequest) request, (HttpServletResponse) response);
		} else {
			servlet.service(request, response);
		}

	}

	// -------------------------------------------------------- Package Methods

	/**
	 * Add a filter to the set of filters that will be executed in this chain.
	 * 
	 * @param filterConfig
	 *            The FilterConfig for the servlet to be executed
	 */
	void addFilter(ApplicationFilterConfig filterConfig) {

		if (n == filters.length) {
			ApplicationFilterConfig[] newFilters = new ApplicationFilterConfig[n + INCREMENT];
			System.arraycopy(filters, 0, newFilters, 0, n);
			filters = newFilters;
		}
		filters[n++] = filterConfig;

	}

	/**
	 * Release references to the filters and wrapper executed by this chain.
	 */
	public void release() {

		for (int i = 0; i < n; i++) {
			filters[i] = null;
		}
		n = 0;
		pos = 0;
		servlet = null;
	}

	/**
	 * Prepare for reuse of the filters and wrapper executed by this chain.
	 */
	void reuse() {
		pos = 0;
	}

	/**
	 * Set the servlet that will be executed at the end of this chain.
	 * 
	 * @param servlet
	 *            The Wrapper for the servlet to be executed
	 */
	void setServlet(Servlet servlet) {

		this.servlet = servlet;

	}
}
