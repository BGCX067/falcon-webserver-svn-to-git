package com.falcon.server.server.core;

import static com.falcon.server.start.ServerConstants.ARG_COMPILE_OUT;
import static com.falcon.server.start.ServerConstants.DEFAULT_SERVLET_CLASS;
import static com.falcon.server.start.ServerConstants.DEFAULT_SERVLET_MAPPING;
import static com.falcon.server.start.ServerConstants.DEFAULT_SERVLET_NAME;
import static com.falcon.server.start.ServerConstants.JSPX_SERVLET_MAPPING;
import static com.falcon.server.start.ServerConstants.JSP_SERVLET_CLASS;
import static com.falcon.server.start.ServerConstants.JSP_SERVLET_LOG_LEVEL;
import static com.falcon.server.start.ServerConstants.JSP_SERVLET_MAPPING;
import static com.falcon.server.start.ServerConstants.JSP_SERVLET_NAME;
import static com.falcon.server.start.ServerConstants.WAR_DEPLOY_IN_ROOT;
import static com.falcon.server.start.ServerConstants.WEBAPPCLASSLOADER;
import static com.falcon.server.start.ServerConstants.WEBAPPINITTIMEOUT;
import static com.falcon.server.start.ServerConstants.WEBAPP_DESCTIPTOR;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionListener;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.falcon.server.http.util.CharChunk;
import com.falcon.server.http.util.MessageBytes;
import com.falcon.server.server.ErrorPage;
import com.falcon.server.server.Wrapper;
import com.falcon.server.server.servlet.FalconContextFacade;
import com.falcon.server.server.servlet.FalconRequestDispatcher;
import com.falcon.server.server.servlet.FalconServletConfigFacade;
import com.falcon.server.util.Enumerator;
import com.falcon.server.util.Globals;
import com.falcon.server.util.Utils;

public class WebAppConfiguration extends FalconContextBase implements ServletContext {

	Wrapper wrapper;

	FalconContextFacade contextFacade = new FalconContextFacade(this);

	FalconServer server;

	private static Node root;

	private static String prefix;

	XPath xpath = XPathFactory.newInstance().newXPath();

	int sessionTimeout;

	/**
	 * 设置调用servlet.init方法的线程join的时间。也就是，使用另一个线程调用
	 * init方法，并设置Thread.join(initTimeout*60)
	 */
	int initTimeout;

	protected HashMap<String, Object> attributes = new HashMap<String, Object>();

	protected HashMap<String, String> contextParameters;

	protected HashMap<String, ResourceEnvRef> resourceEnvRef;

	protected HashMap<String, ResourceRef> resourceRef;

	protected HashMap<String, EnvEntry> envEntry;

	protected List<String> welcomeFiles;

	protected List<ErrorPage> errorPages;

	protected List<EventListener> listeners;

	protected List<HttpSessionListener> sessionListeners;

	protected ArrayList<ServletRequestListener> requestListeners;

	protected ArrayList<ServletRequestAttributeListener> attributeListeners;

	protected Map<String, String> mimes;

	URL[] cpUrls;

	URLClassLoader ucl;

	File deployDir;

	private static List<String> typeArray = new ArrayList<String>();

	long lastDeployed;

	/**
	 * Thread local data used during request dispatch.
	 */
	private ThreadLocal<DispatchData> dispatchData = new ThreadLocal<DispatchData>();

	static {
		typeArray.add("java.lang.Boolean");
		typeArray.add("java.lang.Byte");
		typeArray.add("java.lang.Character");
		typeArray.add("java.lang.String");
		typeArray.add("java.lang.Short");
		typeArray.add("java.lang.Long");
		typeArray.add("java.lang.Integer");
		typeArray.add("java.lang.Float");
		typeArray.add("java.lang.Double");
	}

	public WebAppConfiguration() {
	}

	public WebAppConfiguration(File deployDir, String context, FalconServer server)
			throws ServletException {
		this.server = server;
		manager = new SessionManager(this);
		wrapper = new FalconWrapper(this);
		backgroundProcessorDelay = 10;
		try {
			makeCP(deployDir);
			initNamespace(deployDir);
			initContext(context);
			initCompileDir();
			initWelcomeFile();// 在welcome中注册context所以需要在注册servlet之前先注册context
			boolean useJasper = booleanArg(server.arguments, "useJasper", true);
			if (useJasper) {
				initJSP();
			}
			initContextParameters();
			initSessionConfig();
			Thread.currentThread().setContextClassLoader(ucl);
			initResource();
			initEJB();
			initListener();
			initFilter();
			initServlet();
			initJSPMapping();
			initErrorPage();
			initMimeType();
			initSecurity();
			clear();
		} catch (IOException ioe) {
			throw new ServletException("A problem in reading web.xml.", ioe);
		} catch (XPathExpressionException xpe) {
			xpe.printStackTrace();
			throw new ServletException("A problem in parsing web.xml.", xpe);
		} catch (Exception e) {
			e.printStackTrace();
		}
		lastDeployed = System.currentTimeMillis();
		threadStart();
	}

	private void initCompileDir() throws IOException {

		String cout = (String) server.getArguments().get(ARG_COMPILE_OUT);
		File filecout = null;
		if (cout != null) {
			filecout = new File(cout + File.separator + contextName);
			if (!filecout.isAbsolute()) {
				filecout = new File(getFalconBaseHome() + File.separator + filecout.getPath())
						.getCanonicalFile();
			}
		} else {
			filecout = new File(getFalconBaseHome() + File.separator + "work" + File.separator
					+ contextName).getCanonicalFile();
		}
		if (!filecout.exists()) {
			filecout.mkdir();
		}
		setAttribute(Globals.WORK_DIR_ATTR, filecout);
	}

	private void initJSP() throws ServletException {

		Map<String, String> jspParams = new HashMap<String, String>();
		addJspServletParams(jspParams);
		ServletDef servletDef = new ServletDef();
		servletDef.setServletName(JSP_SERVLET_NAME);
		servletDef.setServletClass(JSP_SERVLET_CLASS);
		servletDef.setLoadOnStartup(1);
		servletDef.addInitParameter(jspParams);
		wrapper.addServletDef(servletDef);
		wrapper.addServletMapping(contextPath, JSP_SERVLET_MAPPING, JSP_SERVLET_NAME);
		wrapper.addServletMapping(contextPath, JSPX_SERVLET_MAPPING, JSP_SERVLET_NAME);
		newInstance(servletDef);

		Map<String, String> defaultParams = new HashMap<String, String>();
		defaultParams.put("webroot", deployDir.getPath());
		ServletDef defaultDef = new ServletDef();
		defaultDef.setServletName(DEFAULT_SERVLET_NAME);
		defaultDef.setServletClass(DEFAULT_SERVLET_CLASS);
		defaultDef.setLoadOnStartup(1);
		defaultDef.addInitParameter(defaultParams);
		wrapper.addServletDef(defaultDef);
		wrapper.addServletMapping(contextPath, DEFAULT_SERVLET_MAPPING, DEFAULT_SERVLET_NAME);
		newInstance(defaultDef);
	}

	public static void addJspServletParams(Map<String, String> jspParams) {
		jspParams.put("logVerbosityLevel", JSP_SERVLET_LOG_LEVEL);
		jspParams.put("fork", "false");
	}

	private void initNamespace(File deployDir) throws XPathExpressionException,
			FileNotFoundException {
		root = (Node) xpath.evaluate("/*", new InputSource(new FileInputStream(new File(deployDir,
				WEBAPP_DESCTIPTOR))), XPathConstants.NODE);
		final String namespaceURI = root.getNamespaceURI();
		prefix = namespaceURI == null ? "" : "j2ee:";
		xpath.setNamespaceContext(new NamespaceContext() {
			public String getNamespaceURI(String prefix) {
				if (prefix == null)
					throw new IllegalArgumentException("Namespace prefix is null.");
				if (namespaceURI == null)
					return XMLConstants.NULL_NS_URI;
				if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX))
					return namespaceURI;
				if ("j2ee".equals(prefix))
					return namespaceURI;
				return XMLConstants.NULL_NS_URI;
			}

			public String getPrefix(String prefix) {
				throw new UnsupportedOperationException("getPrefix(" + prefix + ");");
			}

			public Iterator<?> getPrefixes(String prefix) {
				throw new UnsupportedOperationException("getPrefixes(" + prefix + ");");
			}
		});
	}

	private void initContext(String context) {

		setContextName(context);
		setContextPath("/" + getContextName());
		if (getContextName().equals(System.getProperty(WAR_DEPLOY_IN_ROOT))) {
			contextPath = "/";
			contextName = "";
		}
	}

	private void initContextParameters() throws Exception {

		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "context-param", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		for (int p = 0; p < nodesLen; p++) {
			getContextParameters().put(
					(String) getNodeByXpath(prefix + "param-name", nodes.item(p),
							XPathConstants.STRING),
					(String) getNodeByXpath(prefix + "param-value", nodes.item(p),
							XPathConstants.STRING));
		}
	}

	private void initSessionConfig() throws Exception {

		Number num = (Number) getNodeByXpath("//" + prefix + "session-config/" + prefix
				+ "session-timeout", null, XPathConstants.NUMBER);
		if (num != null)
			if (num.intValue() < 0)
				setSessionTimeout(0);
			else
				setSessionTimeout(num.intValue() * 60);

		initJoinTimeOut();
	}

	private void initJoinTimeOut() {
		setInitTimeout(10);
		String initTimeoutStr = System.getProperty(String.format(WEBAPPINITTIMEOUT, contextName
				+ "."));
		if (initTimeoutStr == null)
			initTimeoutStr = System.getProperty(String.format(WEBAPPINITTIMEOUT, ""));
		if (initTimeoutStr != null)
			try {
				int timeout = Integer.parseInt(initTimeoutStr);
				if (timeout > 2)
					setInitTimeout(timeout);
			} catch (Exception e) {

			}
	}

	private void initResource() throws Exception {

		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "resource-env-ref", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		if (nodesLen != 0)
			resourceEnvRef = new HashMap<String, ResourceEnvRef>();
		for (int i = 0; i < nodesLen; i++) {
			Node n = nodes.item(i);

			String description = (String) getNodeByXpath(prefix + "description", n,
					XPathConstants.STRING);
			log("Processing resource-env-ref " + description);
			String name = (String) getNodeByXpath(prefix + "resource-env-ref-name", n,
					XPathConstants.STRING);
			if (name == null) {
				throw new IllegalArgumentException("resource-env-ref-name must exist");
			}
			String type = (String) getNodeByXpath(prefix + "resource-env-ref-type", n,
					XPathConstants.STRING);
			if (type == null) {
				type = "java.lang.String";
			}
			resourceEnvRef.put(name, new ResourceEnvRef(description, name, type));
		}

		nodes = (NodeList) getNodeByXpath("//" + prefix + "resource-ref", null,
				XPathConstants.NODESET);
		nodesLen = nodes.getLength();
		if (nodesLen != 0)
			resourceRef = new HashMap<String, ResourceRef>();
		for (int i = 0; i < nodesLen; i++) {
			Node n = nodes.item(i);
			String description = (String) getNodeByXpath(prefix + "description", n,
					XPathConstants.STRING);
			log("Processing resource-ref " + description);

			String name = (String) getNodeByXpath(prefix + "res-ref-name", n, XPathConstants.STRING);
			if (name == null) {
				throw new IllegalArgumentException("resource-env-ref-name must exist");
			}
			String type = (String) getNodeByXpath(prefix + "res-type", n, XPathConstants.STRING);
			if (type == null) {
				type = "java.lang.String";
			}
			String auth = (String) getNodeByXpath(prefix + "res-auth", n, XPathConstants.STRING);
			String scope = (String) getNodeByXpath(prefix + "res-sharing-scope", n,
					XPathConstants.STRING);
			resourceRef.put(name, new ResourceRef(description, name, type, auth, scope));
		}
		nodes = (NodeList) getNodeByXpath("//" + prefix + "env-entry", null, XPathConstants.NODESET);
		nodesLen = nodes.getLength();
		if (nodesLen != 0)
			envEntry = new HashMap<String, EnvEntry>();
		for (int i = 0; i < nodesLen; i++) {
			Node n = nodes.item(i);
			String description = (String) getNodeByXpath(prefix + "description", n,
					XPathConstants.STRING);
			log("Processing env-entry " + description);
			String value = (String) getNodeByXpath(prefix + "env-entry-value", n,
					XPathConstants.STRING);
			String type = null;
			if (value != null) {
				type = (String) getNodeByXpath(prefix + "env-entry-type", n, XPathConstants.STRING);
				if (type != null) {
					if (!typeArray.contains(type)) {
						throw new IllegalArgumentException("env-entry-type must be of value:"
						/*+ StringUtils.toStringArray(typeArray)*/);
					}
				}
			}
			String name = (String) getNodeByXpath(prefix + "env-entry-name", n,
					XPathConstants.STRING);
			if (name == null) {
				throw new IllegalArgumentException("env-ref-name must exist");
			}
			envEntry.put(name, new EnvEntry(description, name, type));
		}
	}

	private void initEJB() throws Exception {

		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "ejb-ref", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		if (nodesLen > 0)
			log("EJB references are not supported");
		nodes = (NodeList) getNodeByXpath(prefix + "ejb-local-ref", null, XPathConstants.NODESET);
		nodesLen = nodes.getLength();
		if (nodesLen > 0)
			log("Local EJB references are not supported");
	}

	private void initListener() throws Exception {
		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "listener/" + prefix
				+ "listener-class", null, XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		if (nodesLen > 0) {
			listeners = new ArrayList<EventListener>(nodesLen);
			for (int i = 0; i < nodesLen; i++)
				try {
					EventListener eventListener = (EventListener) ucl.loadClass(
							nodes.item(i).getTextContent().trim()).newInstance();
					if (eventListener instanceof HttpSessionListener) {
						if (sessionListeners == null)
							sessionListeners = new ArrayList<HttpSessionListener>(nodesLen);
						sessionListeners.add((HttpSessionListener) eventListener);
					}
					if (eventListener instanceof ServletRequestListener) {
						if (requestListeners == null)
							requestListeners = new ArrayList<ServletRequestListener>(nodesLen);
						requestListeners.add((ServletRequestListener) eventListener);
					}
					if (eventListener instanceof ServletRequestAttributeListener) {
						if (attributeListeners == null)
							attributeListeners = new ArrayList<ServletRequestAttributeListener>(
									nodesLen);
						attributeListeners.add((ServletRequestAttributeListener) eventListener);
					}
					listeners.add(eventListener);
				} catch (Exception e) {
					log("Event listener " + nodes.item(i).getTextContent()
							+ " can't be created due an exception.", e);
				} catch (Error e) {
					log("Event listener " + nodes.item(i).getTextContent()
							+ " can't be created due an error.", e);
				}
		}
		// notify context listeners
		if (listeners != null)
			for (EventListener listener : listeners) {
				if (listener instanceof ServletContextListener) {
					final ServletContextListener contListener = (ServletContextListener) listener;
					contListener.contextInitialized(new ServletContextEvent(this));
				}
			}
	}

	private void initFilter() throws Exception {
		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "filter", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		FilterDef filter;
		for (int i = 0; i < nodesLen; i++) {
			Node n = nodes.item(i);
			String name = (String) getNodeByXpath(prefix + "filter-name", n, XPathConstants.STRING);
			if (name == null) {
				throw new IllegalArgumentException("filter-name dose not exist");
			}
			String className = (String) getNodeByXpath(prefix + "filter-class", n,
					XPathConstants.STRING);
			if (className == null)
				throw new ServletException(String.format(
						"Filter %s specified without or empty class.", name));
			else
				className = className.trim();

			filter = new FilterDef();
			filter.setFilterName(name);
			filter.setFilterClass(className);

			filter.setDisplayName((String) getNodeByXpath(prefix + "display-name", n,
					XPathConstants.STRING));
			filter.setDescription((String) getNodeByXpath(prefix + "description", n,
					XPathConstants.STRING));

			NodeList params = (NodeList) getNodeByXpath(prefix + "init-param", n,
					XPathConstants.NODESET);

			for (int p = 0; p < params.getLength(); p++) {
				filter.addInitParameter((String) getNodeByXpath(prefix + "param-name", params
						.item(p), XPathConstants.STRING), (String) getNodeByXpath(prefix
						+ "param-value", params.item(p), XPathConstants.STRING));
			}
			wrapper.addFilterDef(filter);
			log(String.format("registry filter %s", name));
		}
		// process filter's mapping

		nodes = (NodeList) getNodeByXpath("//" + prefix + "filter-mapping", null,
				XPathConstants.NODESET);
		nodesLen = nodes.getLength();
		for (int i = 0; i < nodesLen; i++) {
			Node n = nodes.item(i);

			NodeList clarifications = (NodeList) getNodeByXpath(prefix + "url-pattern", n,
					XPathConstants.NODESET);
			int claLen = clarifications.getLength();

			String filterName = (String) getNodeByXpath(prefix + "filter-name", n,
					XPathConstants.STRING);
			if (filterName == null)
				throw new ServletException(String.format(
						"Filter Mapping %s specified without or empty filter-name.", filterName));

			FilterMap filterMap = new FilterMap();
			filterMap.setFilterName(filterName);

			for (int j = 0; j < claLen; j++) {
				String mapUrl = clarifications.item(j).getTextContent();
				if (mapUrl == null || mapUrl.length() == 0)
					continue;

				filterMap.addURLPattern(mapUrl);
			}
			clarifications = (NodeList) getNodeByXpath(prefix + "dispatcher", n,
					XPathConstants.NODESET);
			claLen = clarifications.getLength();
			for (int j = 0; j < claLen; j++) {
				String filterType = clarifications.item(j).getTextContent();
				filterMap.setDispatcher(filterType);
			}
			clarifications = (NodeList) getNodeByXpath(prefix + "servlet-name", n,
					XPathConstants.NODESET);
			claLen = clarifications.getLength();
			for (int j = 0; j < claLen; j++) {
				filterMap.addServletName(clarifications.item(j).getTextContent());
			}
			wrapper.addFilterMap(filterMap);
		}
		wrapper.ConfigureAndInitializeFilter();
	}

	private void initServlet() throws Exception {

		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "servlet", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		ServletDef servletDef = null;
		for (int i = 0; i < nodesLen; i++) {
			Node n = nodes.item(i);
			String name = (String) getNodeByXpath(prefix + "servlet-name", n, XPathConstants.STRING);
			if (name == null) {
				throw new IllegalArgumentException("servlet name does not exist");
			}
			servletDef = new ServletDef();
			servletDef.setServletName(name);
			servletDef.setLoader(ucl);

			String className = (String) getNodeByXpath(prefix + "servlet-class", n,
					XPathConstants.STRING);
			if (className == null || className.length() == 0) {
				String jspFile = (String) getNodeByXpath(prefix + "jsp-file", n,
						XPathConstants.STRING);
				if (jspFile != null) {
					log(String.format("Not supported servlet option jsp-file %s for %s, ignored.",
							jspFile, name));
					continue;
				} else
					throw new ServletException(String.format(
							"Servlet %s specified without class or jsp file.", name));
			} else
				className = className.trim();
			servletDef.setServletClass(className);
			servletDef.setDisplayName((String) getNodeByXpath(prefix + "display-name", n,
					XPathConstants.STRING));
			servletDef.setDescription((String) getNodeByXpath(prefix + "description", n,
					XPathConstants.STRING));
			String loadOnStartVal = (String) getNodeByXpath(prefix + "load-on-startup", n,
					XPathConstants.STRING);
			if (loadOnStartVal != null&&!loadOnStartVal.equals(""))
				servletDef.setLoadOnStartup(Integer.parseInt(loadOnStartVal));
			NodeList params = (NodeList) getNodeByXpath(prefix + "init-param", n,
					XPathConstants.NODESET);
			for (int p = 0; p < params.getLength(); p++) {
				servletDef.addInitParameter((String) getNodeByXpath(prefix + "param-name", params
						.item(p), XPathConstants.STRING), (String) getNodeByXpath(prefix
						+ "param-value", params.item(p), XPathConstants.STRING));
			}
			wrapper.addServletDef(servletDef);
		}
		nodes = (NodeList) getNodeByXpath("//" + prefix + "servlet-mapping", null,
				XPathConstants.NODESET);
		nodesLen = nodes.getLength();
		for (int i = 0; i < nodesLen; i++) {
			String pattern = (String) getNodeByXpath(prefix + "url-pattern", nodes.item(i),
					XPathConstants.STRING);
			String servletname = (String) getNodeByXpath(prefix + "servlet-name", nodes.item(i),
					XPathConstants.STRING);
			if (pattern == null) {
				// mapping with empty pattern
				// 映射全部
				String urlPat = "/*";
				wrapper.addServletMapping(contextPath, urlPat, servletname);
			} else {
				wrapper.addServletMapping(contextPath, pattern, servletname);
			}
		}
		if (servletDef != null && servletDef.getLoadOnStartup() > 0)
			newInstance(servletDef);
	}

	protected Servlet newInstance(final ServletDef servletDef) throws ServletException {
		try {
			final Servlet servlet = (Servlet) ucl.loadClass(servletDef.getServletClass())
					.newInstance();
			final ServletException[] exHolder = new ServletException[1];
			Thread initThread = new Thread("Init thread of " + contextName) {
				public void run() {
					try {
						servlet.init(new FalconServletConfigFacade(servletDef, getFacade()));
					} catch (ServletException se) {
						exHolder[0] = se;
					}
				}
			};
			initThread.start();
			initThread.join(initTimeout * 1000);
			if (initThread.isAlive() == true)
				throw new ServletException("Init of " + contextName + " exceeded allocated time ("
						+ initTimeout + " secs)");
			if (exHolder[0] != null)
				throw exHolder[0];
			servletDef.setInstance(servlet);
			return servlet;
		} catch (InstantiationException ie) {
			throw new ServletException("Servlet class " + servletDef.getServletClass()
					+ " can't instantiate. ", ie);
		} catch (IllegalAccessException iae) {
			throw new ServletException("Servlet class " + servletDef.getServletClass()
					+ " can't access. ", iae);
		} catch (ClassNotFoundException cnfe) {
			log("", cnfe);
			throw new ServletException("Servlet class " + servletDef.getServletClass()
					+ " not found. ", cnfe);
		} catch (Error e) {
			throw new ServletException("Servlet class " + servletDef.getServletClass()
					+ " can't be instantiated or initialized due an error.", e);
		} catch (Throwable t) {
			if (t instanceof ThreadDeath)
				throw (ThreadDeath) t;
			throw new ServletException("Servlet class " + servletDef.getServletClass()
					+ " can't be instantiated or initialized due an exception.", t);
		}
	}

	private void initJSPMapping() throws Exception {
		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "jsp-config/" + prefix
				+ "jsp-property-group/" + prefix + "url-pattern", null, XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		if (nodesLen > 0) {
			List<String> jspPats = new ArrayList<String>(nodesLen);
			for (int i = 0; i < nodesLen; i++) {
				jspPats.add(nodes.item(i).getTextContent());
			}
			addJSPServlet(jspPats);
		} else
			addJSPServlet(null);
	}

	protected void addJSPServlet(List<String> patterns) {
		// 处理jsp
	}

	private void initWelcomeFile() throws Exception {
		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "welcome-file-list/" + prefix
				+ "welcome-file", null, XPathConstants.NODESET);
		welcomeFiles = new ArrayList<String>(nodes.getLength() + 1);
		int nodesLen = nodes.getLength();
		if (nodesLen > 0)
			for (int wfi = 0; wfi < nodesLen; wfi++)
				welcomeFiles.add(nodes.item(wfi).getTextContent());
		else {
			welcomeFiles.add("index.html");
			welcomeFiles.add("index.htm");
			welcomeFiles.add("index.jsp");
		}
		// 在添加了欢迎页面之后，添加context
		wrapper.addContext(this, welcomeFiles.toArray(new String[0]));
	}

	private void initErrorPage() throws Exception {
		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "error-page", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		if (nodesLen > 0) {
			errorPages = new ArrayList<ErrorPage>(nodesLen);
			for (int i = 0; i < nodesLen; i++) {
				Node n = nodes.item(i);
				errorPages.add(new ErrorPage((String) getNodeByXpath(prefix + "location", n,
						XPathConstants.STRING), (String) getNodeByXpath(prefix + "exception-type",
						n, XPathConstants.STRING), (String) getNodeByXpath(prefix + "error-code",
						n, XPathConstants.STRING)));
			}
		}
	}

	private void initMimeType() throws Exception {
		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "mime-mapping", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		if (nodesLen > 0) {
			mimes = new HashMap<String, String>(nodesLen);
			for (int i = 0; i < nodesLen; i++) {
				Node n = nodes.item(i);
				mimes.put(((String) getNodeByXpath(prefix + "extension", n, XPathConstants.STRING))
						.toLowerCase(), (String) getNodeByXpath(prefix + "mime-type", n,
						XPathConstants.STRING));
			}
		}
	}

	private void initSecurity() throws Exception {
		NodeList nodes = (NodeList) getNodeByXpath("//" + prefix + "security-constraint", null,
				XPathConstants.NODESET);
		int nodesLen = nodes.getLength();
		if (nodesLen > 0)
			log("Security constraints are not supported");
		if (getNodeByXpath(prefix + "login-config", null, XPathConstants.NODE) != null)
			log("Login config is not supported");
		nodes = (NodeList) getNodeByXpath(prefix + "security-role", null, XPathConstants.NODESET);
		nodesLen = nodes.getLength();
		if (nodesLen > 0)
			log("Security roles are not supported");
		nodes = (NodeList) getNodeByXpath(prefix + "ejb-ref", null, XPathConstants.NODESET);
		nodesLen = nodes.getLength();
	}

	private Object getNodeByXpath(String path, Object relativeNode, QName qname) throws Exception {
		if (relativeNode == null)
			relativeNode = root;
		return xpath.evaluate(path, relativeNode, qname);
	}

	protected void makeCP(File dd) throws IOException {
		deployDir = dd.getCanonicalFile();
		final List<URL> urls = new ArrayList<URL>();
		File classesFile = new File(deployDir, "WEB-INF/classes");
		if (classesFile.exists() && classesFile.isDirectory())
			try {
				urls.add(classesFile.toURL());
			} catch (java.net.MalformedURLException mfe) {

			}
		File libFile = new File(deployDir, "WEB-INF/lib");
		libFile.listFiles(new FileFilter() {
			public boolean accept(File file) {
				String name = file.getName().toLowerCase();
				if (name.endsWith(".jar") || name.endsWith(".zip"))
					try {
						urls.add(file.toURL());
					} catch (java.net.MalformedURLException mfe) {

					}
				return false;
			}
		});
		cpUrls = urls.toArray(new URL[urls.size()]);
		ucl = new URLClassLoader(cpUrls, getClass().getClassLoader()) {
			@Override
			public URL getResource(String name) {
				URL url = super.getResource(name);

				if (url == null && name.startsWith("/")) {
					url = super.getResource(name.substring(1));
				}
				return url;
			}
		};

		setAttribute(WEBAPPCLASSLOADER, ucl);
	}

	public void log(java.lang.String msg) {
		server.log((contextName == null ? "" : contextName) + "> " + msg);
	}

	public void log(java.lang.Exception exception, java.lang.String msg) {
		server.log(exception, (contextName == null ? "" : contextName) + "> " + msg);
	}

	public void log(java.lang.String message, java.lang.Throwable throwable) {
		server.log((contextName == null ? "" : contextName) + "> " + message, throwable);
	}

	protected class ResourceEnvRef {

		String description;
		String name;
		String type;

		public ResourceEnvRef(String description, String name, String type) {
			super();
			this.description = description;
			this.name = name;
			this.type = type;
		}
	}

	protected class ResourceRef {

		String description;
		String name;
		String type;
		String resAuth;
		String scope;

		public ResourceRef(String description, String name, String type, String resAuth,
				String scope) {
			super();
			this.description = description;
			this.name = name;
			this.type = type;
			this.resAuth = resAuth;
			this.scope = scope;
		}
	}

	protected class EnvEntry {

		String description;
		String name;
		String type;

		public EnvEntry(String description, String name, String type) {
			super();
			this.description = description;
			this.name = name;
			this.type = type;
		}

	}

	private static void clear() {
		root = null;
	}

	public int getSessionTimeout() {
		return sessionTimeout;
	}

	private void setSessionTimeout(int sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}

	public int getInitTimeout() {
		return initTimeout;
	}

	public void setInitTimeout(int initTimeout) {
		this.initTimeout = initTimeout;
	}

	public HashMap<String, String> getContextParameters() {
		return contextParameters;
	}

	public String getContextName() {
		return contextName;
	}

	public void setContextName(String contextName) {
		this.contextName = contextName;
	}

	public String getContextPath() {
		return contextPath;
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}

	public String getServletContextName() {
		return contextName;
	}

	public static boolean booleanArg(Map<String, Object> args, String name, boolean defaultTrue) {
		String value = (String) args.get(name);
		if (defaultTrue)
			return (value == null)
					|| (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
		else
			return (value != null)
					&& (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
	}

	public void destroy() {
		Thread.currentThread().setContextClassLoader(ucl);
		if (wrapper != null) {
			wrapper.removeAllFilter();
			wrapper.removeAllServlets();
		}
		if (listeners != null)
			for (int i = listeners.size() - 1; i > -1; i--) {
				EventListener listener = listeners.get(i);
				if (listener instanceof ServletContextListener)
					((ServletContextListener) listener).contextDestroyed(new ServletContextEvent(
							this));
			}
		Enumeration<String> e = getAttributeNames();
		while (e.hasMoreElements())
			removeAttribute((String) e.nextElement());
	}

	public static String validatePath(String path) {
		return Utils.canonicalizePath(path);
	}

	/**
	 * This function extract meaningful path or query
	 * 
	 * @param path
	 *            path to extract from
	 * @param query
	 *            true if extract query
	 * @return extraction or null
	 */
	public static String extractQueryAnchor(String path, boolean query) {
		int qp = path.indexOf('?');
		if (query) {
			if (qp >= 0)
				return path.substring(qp + 1);
			return null;
		}
		int hp = path.indexOf('#');
		if (qp >= 0) {
			if (hp >= 0 && hp < qp)
				return path.substring(0, hp);
			return path.substring(0, qp);
		} else if (hp >= 0)
			return path.substring(0, hp);
		return path;
	}

	public URLClassLoader getContextClassloader() {
		return ucl;
	}

	public ServletContext getFacade() {
		return contextFacade;
	}

	/**
	 * Return a context-relative path, beginning with a "/", that represents the
	 * canonical version of the specified path after ".." and "." elements are
	 * resolved out. If the specified path attempts to go outside the boundaries
	 * of the current context (i.e. too many ".." path elements are present),
	 * return <code>null</code> instead.
	 * 
	 * @param path
	 *            Path to be normalized
	 */
	private String normalize(String path) {

		if (path == null) {
			return null;
		}

		String normalized = path;

		// Normalize the slashes
		if (normalized.indexOf('\\') >= 0)
			normalized = normalized.replace('\\', '/');

		// Resolve occurrences of "/../" in the normalized path
		while (true) {
			int index = normalized.indexOf("/../");
			if (index < 0)
				break;
			if (index == 0)
				return (null); // Trying to go outside our context
			int index2 = normalized.lastIndexOf('/', index - 1);
			normalized = normalized.substring(0, index2) + normalized.substring(index + 3);
		}

		// Return the normalized path that we have completed
		return (normalized);

	}

	// ---------------------------------------------------------ServletContext
	// implement method
	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	public void setAttribute(String name, Object object) {
		if (object == null) {
			removeAttribute(name);
			return;
		}
		Object oldObj = attributes.put(name, object);
		if (listeners != null)
			for (EventListener listener : listeners) {
				if (listener instanceof ServletContextAttributeListener)
					if (oldObj == null)
						((ServletContextAttributeListener) listener)
								.attributeAdded(new ServletContextAttributeEvent(this, name, object));
					else
						((ServletContextAttributeListener) listener)
								.attributeReplaced(new ServletContextAttributeEvent(this, name,
										object));
			}
	}

	public Enumeration<String> getAttributeNames() {
		return new Enumerator<String>(attributes.keySet());
	}

	public ServletContext getContext(String uripath) {
		ServletContext app = server.getWebApp(uripath);
		// if (app == null)
		// server.log(String.format("%s servlet context dose not exist",
		// uripath));
		return app;
	}

	public String getInitParameter(String name) {
		return contextParameters.get(name);
	}

	public Enumeration<String> getInitParameterNames() {
		return Collections.enumeration(this.contextParameters.keySet());
	}

	public int getMajorVersion() {
		return 2;
	}

	public String getMimeType(String file) {
		if (mimes != null && file != null) {
			int p = file.lastIndexOf('.');
			if (p > 0) {
				String result = mimes.get(file.substring(p).toLowerCase());
				if (result != null)
					return result;
			}
		}
		return server.getMimeType(file);
	}

	public int getMinorVersion() {
		return 1;
	}

	public RequestDispatcher getNamedDispatcher(String name) {
		ServletDef serverDef = wrapper.findServletDef(name);
		if (serverDef != null) {
			if (serverDef.getInstance() == null
					&& Integer.toString(serverDef.getLoadOnStartup()) == null)
				try {
					newInstance(serverDef);
				} catch (ServletException se) {
				}
			if (serverDef.getInstance() != null) {
				RequestDispatcher rd = new FalconRequestDispatcher(wrapper, name, null, null, null,
						null, null);
				return rd;
			}
		}
		return null;
	}

	public String getRealPath(String path) {
		path = validatePath(path);
		if (path == null)
			return deployDir.toString();
		else
			return new File(deployDir, path).toString();
	}

	public String getFalconBaseHome() {

		String home = System.getProperty("Falcon.home");
		if (home == null) {
			home = System.getProperty("user.dir");
		}
		System.setProperty("Falcon.home", home);
		return home;
	}

	public RequestDispatcher getRequestDispatcher(String path) {

		// Get query string
		String queryString = null;
		int pos = path.indexOf('?');
		if (pos >= 0) {
			queryString = path.substring(pos + 1);
			path = path.substring(0, pos);
		}

		path = normalize(path);
		if (path == null)
			return (null);

		pos = path.length();

		DispatchData dd = dispatchData.get();
		if (dd == null) {
			dd = new DispatchData();
			dispatchData.set(dd);
		}

		MessageBytes uriMB = dd.uriMB;
		uriMB.recycle();

		// Use the thread local mapping data
		MappingData mappingData = dd.mappingData;

		// Map the URI
		CharChunk uriCC = uriMB.getCharChunk();
		try {
			uriCC.append(contextPath, 0, contextPath.length());

			int semicolon = path.indexOf(';');
			if (pos >= 0 && semicolon > pos) {
				semicolon = -1;
			}
			uriCC.append(path, 0, semicolon > 0 ? semicolon : pos);
			wrapper.getMapper().map(uriMB, mappingData);
			if (mappingData.wrapper == null) {
				return (null);
			}
			if (semicolon > 0) {
				uriCC.append(path, semicolon, pos - semicolon);
			}
		} catch (Exception e) {
			// Should never happen
			log("applicationContext.mapping.error");
			return (null);
		}

		ServletDef servletDef = (ServletDef) mappingData.wrapper;
		String wrapperPath = mappingData.wrapperPath.toString();
		String pathInfo = mappingData.pathInfo.toString();

		mappingData.recycle();

		// Construct a RequestDispatcher to process this request
		return new FalconRequestDispatcher(wrapper, servletDef.getServletName(), uriCC.toString(),
				contextPath, wrapperPath, pathInfo, queryString);

	}

	public URL getResource(String path) throws MalformedURLException {
		if (path.charAt(0) != '/')
			throw new MalformedURLException("Path: " + path + " has to start with '/'");
		path = extractQueryAnchor(path, false);
		try {
			File resFile = new File(getRealPath(path)).getCanonicalFile();
			if (resFile.exists())
				return resFile.toURL();
		} catch (IOException io) {
		}
		return null;
	}

	public InputStream getResourceAsStream(String path) {
		try {
			return getResource(path).openStream();
		} catch (NullPointerException npe) {
			System.err.println("URL can't be created for :" + path);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return null;
	}

	public Set<String> getResourcePaths(String path) {
		if (path.charAt(0) != '/')
			throw new IllegalArgumentException(
					"getResourcePaths: path parameters must begin with '/'");
		path = extractQueryAnchor(path, false);
		File dir = new File(getRealPath(path));
		if (dir.exists() == false || dir.isDirectory() == false)
			return null;
		Set<String> set = new TreeSet<String>();
		String[] els = dir.list();
		for (String el : els) {
			String fp = path + "/" + el;
			if (new File(getRealPath(fp)).isDirectory())
				fp += "/";
			set.add("/" + fp);
		}
		return set;
	}

	public String getServerInfo() {
		return "Falcon web container, Copyright &copy; 2009";
	}

	public Servlet getServlet(String name) throws ServletException {
		ServletDef servletDef = wrapper.findServletDef(name);
		if (servletDef != null)
			return servletDef.getInstance();
		throw new ServletException("No servlet " + name);
	}

	public Enumeration<String> getServletNames() {
		List<String> servletNames = new ArrayList<String>();
		for (ServletDef def : wrapper.getServletDefs()) {
			servletNames.add(def.getServletName());
		}
		return new Enumerator<String>(servletNames);
	}

	public Enumeration<Servlet> getServlets() {
		List<Servlet> result = new ArrayList<Servlet>();
		for (ServletDef def : wrapper.getServletDefs()) {
			result.add(def.getInstance());
		}
		return new Enumerator<Servlet>(result);
	}

	public void removeAttribute(java.lang.String name) {
		Object value = attributes.remove(name);
		if (listeners != null)
			for (EventListener listener : listeners)
				if (listener instanceof ServletContextAttributeListener)
					((ServletContextAttributeListener) listener)
							.attributeRemoved(new ServletContextAttributeEvent(this, name, value));
	}

	/**
	 * Internal class used as thread-local storage when doing path mapping
	 * during dispatch.
	 */
	private static final class DispatchData {

		public MessageBytes uriMB;
		public MappingData mappingData;

		public DispatchData() {
			uriMB = MessageBytes.newInstance();
			CharChunk uriCC = uriMB.getCharChunk();
			uriCC.setLimit(-1);
			mappingData = new MappingData();
		}
	}

	public Object[] getApplicationEventListeners() {
		if (listeners != null)
			return listeners.toArray(new Object[0]);
		else
			return null;
	}

	public Wrapper getWrapper() {

		return wrapper;
	}
}
