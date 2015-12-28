package com.falcon.server.server.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;

import com.falcon.server.server.Wrapper;
import com.falcon.server.server.core.ApplicationFilterChain;
import com.falcon.server.server.core.ApplicationFilterFactory;
import com.falcon.server.server.core.ServletDef;

/**
 * This class implements both the RequestDispatcher and FilterChain components.
 * On the first call to include() or forward(), it starts the filter chain
 * execution if one exists. On the final doFilter() or if there is no chain, we
 * call the include() or forward() again, and the servlet is executed.
 * 
 */
public class FalconRequestDispatcher implements javax.servlet.RequestDispatcher {

	static final String INCLUDE_REQUEST_URI = "javax.servlet.include.request_uri";
	static final String INCLUDE_CONTEXT_PATH = "javax.servlet.include.context_path";
	static final String INCLUDE_SERVLET_PATH = "javax.servlet.include.servlet_path";
	static final String INCLUDE_PATH_INFO = "javax.servlet.include.path_info";
	static final String INCLUDE_QUERY_STRING = "javax.servlet.include.query_string";

	static final String FORWARD_REQUEST_URI = "javax.servlet.forward.request_uri";
	static final String FORWARD_CONTEXT_PATH = "javax.servlet.forward.context_path";
	static final String FORWARD_SERVLET_PATH = "javax.servlet.forward.servlet_path";
	static final String FORWARD_PATH_INFO = "javax.servlet.forward.path_info";
	static final String FORWARD_QUERY_STRING = "javax.servlet.forward.query_string";

	static final String ERROR_STATUS_CODE = "javax.servlet.error.status_code";
	static final String ERROR_EXCEPTION_TYPE = "javax.servlet.error.exception_type";
	static final String ERROR_MESSAGE = "javax.servlet.error.message";
	static final String ERROR_EXCEPTION = "javax.servlet.error.exception";
	static final String ERROR_REQUEST_URI = "javax.servlet.error.request_uri";
	static final String ERROR_SERVLET_NAME = "javax.servlet.error.servlet_name";

	private Wrapper wrapper;
	private ServletDef servletDef;

	private String servletPath;
	private String pathInfo;
	private String queryString;
	private String requestURI;
	private String contextPath;

	private Boolean doInclude;

	private String name;

	/**
	 * Constructor. This initializes the filter chain and sets up the details
	 * needed to handle a servlet excecution, such as security constraints,
	 * filters, etc.
	 */
	public FalconRequestDispatcher(Wrapper wrapper, String name, String requestURI,
			String contextPath, String pathInfo, String servletPath, String queryString) {
		this.wrapper = wrapper;
		this.servletDef = wrapper.findServletDef(name);
		this.name = name;
		this.requestURI = requestURI;
		this.contextPath = contextPath;
		this.pathInfo = pathInfo;
		this.servletPath = servletPath;
		this.queryString = queryString;
	}

	/**
	 * Includes the execution of a servlet into the current request
	 * 
	 * Note this method enters itself twice: once with the initial call, and
	 * once again when all the filters have completed.
	 */
	public void include(ServletRequest request, ServletResponse response) throws ServletException,
			IOException {

		if (this.doInclude == null) {

			FalconRequest wrequest = getUnwrappedRequest(request);
			wrequest.addIncludeAttributes(requestURI, contextPath, servletPath, pathInfo,
					queryString);

			// Add another include buffer to the response stack
			FalconResponse wresponse = getUnwrappedResponse(response);

			if (servletPath != null)
				wrequest.setServletPath(servletPath);
			wrequest.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR, Integer
					.valueOf(ApplicationFilterFactory.INCLUDE));
			wrequest.setAttribute(ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
					servletPath);
			invoke(wrequest, wresponse);

			this.doInclude = Boolean.TRUE;
		}
	}

	/**
	 * Forwards to another servlet, and when it's finished executing that other
	 * servlet, cut off execution.
	 * 
	 * Note this method enters itself twice: once with the initial call, and
	 * once again when all the filters have completed.
	 */
	public void forward(ServletRequest request, ServletResponse response) throws ServletException,
			IOException {

		if (this.doInclude == null) {
			if (response.isCommitted()) {
				throw new IllegalStateException("RequestDispatcher's Forward has been Committed");
			}

			FalconRequest req = getUnwrappedRequest(request);
			FalconResponse rsp = getUnwrappedResponse(response);

			rsp.resetBuffer();

			req.setAttribute(FORWARD_REQUEST_URI, req.getRequestURI());
			req.setAttribute(FORWARD_CONTEXT_PATH, req.getContextPath());
			req.setAttribute(FORWARD_SERVLET_PATH, req.getServletPath());
			req.setAttribute(FORWARD_PATH_INFO, req.getPathInfo());
			req.setAttribute(FORWARD_QUERY_STRING, req.getQueryString());

			if (servletPath != null)
				req.setServletPath(servletPath);
			req.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR, Integer
					.valueOf(ApplicationFilterFactory.FORWARD));
			req.setAttribute(ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR, servletPath);
			invoke(req, rsp);
			this.doInclude = Boolean.FALSE;
			try {
				PrintWriter writer = response.getWriter();
				writer.close();
			} catch (IllegalStateException e) {
				try {
					ServletOutputStream stream = response.getOutputStream();
					stream.close();
				} catch (IllegalStateException f) {
					;
				} catch (IOException f) {
					;
				}
			} catch (IOException e) {
				;
			}
		}
	}

	/**
	 * Unwrap back to the original container allocated request object
	 */
	protected FalconRequest getUnwrappedRequest(ServletRequest request) {
		ServletRequest workingRequest = request;
		if (workingRequest instanceof ServletRequestWrapper) {
			workingRequest = ((ServletRequestWrapper) workingRequest).getRequest();
		}
		return (FalconRequest) workingRequest;
	}

	/**
	 * Unwrap back to the original container allocated response object
	 */
	protected FalconResponse getUnwrappedResponse(ServletResponse response) {
		ServletResponse workingResponse = response;
		if (workingResponse instanceof ServletResponseWrapper) {
			workingResponse = ((ServletResponseWrapper) workingResponse).getResponse();
		}
		return (FalconResponse) workingResponse;
	}

	private void invoke(ServletRequest request, ServletResponse response) throws IOException,
			ServletException {

		ClassLoader oldCCL = Thread.currentThread().getContextClassLoader();
		ClassLoader contextClassLoader = servletDef.getLoader();

		if (oldCCL != contextClassLoader) {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		} else {
			oldCCL = null;
		}

		Servlet servlet = null;
		IOException ioException = null;
		ServletException servletException = null;
		RuntimeException runtimeException = null;

		servlet = servletDef.getInstance();// servlet没有考虑实现SingleThreadModel接口。
		// Get the FilterChain Here
		ApplicationFilterFactory factory = ApplicationFilterFactory.getInstance();
		ApplicationFilterChain filterChain = factory.createFilterChain(request, wrapper,
				servlet,servletDef.getServletName());

		// TODO 此处没有考虑jsp-file标签的作用
		// for includes/forwards
		if ((servlet != null) && (filterChain != null)) {
			filterChain.doFilter(request, response);
		}
		// Release the filter chain (if any) for this request
		try {
			if (filterChain != null)
				filterChain.release();
		} catch (Throwable e) {
		}
		// Reset the old context class loader
		if (oldCCL != null)
			Thread.currentThread().setContextClassLoader(oldCCL);

		if (ioException != null)
			throw ioException;
		if (servletException != null)
			throw servletException;
		if (runtimeException != null)
			throw runtimeException;

	}
}
