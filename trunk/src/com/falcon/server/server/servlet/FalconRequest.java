package com.falcon.server.server.servlet;

import static com.falcon.server.start.ServerConstants.CONTENTLENGTH;
import static com.falcon.server.start.ServerConstants.CONTENTTYPE;
import static com.falcon.server.start.ServerConstants.DEFAULT_CHARACTER_ENCODING;
import static com.falcon.server.start.ServerConstants.METHOD;
import static com.falcon.server.start.ServerConstants.PROTOCOL;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;

import com.falcon.server.http.Cookies;
import com.falcon.server.http.ServerCookie;
import com.falcon.server.http.util.FastHttpDateFormat;
import com.falcon.server.http.util.MessageBytes;
import com.falcon.server.http.util.MimeHeaders;
import com.falcon.server.http.util.Parameters;
import com.falcon.server.server.Context;
import com.falcon.server.server.Manager;
import com.falcon.server.server.Request;
import com.falcon.server.server.Response;
import com.falcon.server.server.Session;
import com.falcon.server.server.core.ApplicationFilterFactory;
import com.falcon.server.server.core.MappingData;
import com.falcon.server.server.core.ServletDef;
import com.falcon.server.util.Enumerator;
import com.falcon.server.util.Globals;
import com.falcon.server.util.ParameterMap;
import com.falcon.server.util.RequestUtil;
import com.falcon.server.util.StringParser;
import com.falcon.server.util.UDecoder;

public class FalconRequest implements Request {

	private String authType;

	private HashMap<String, Object> attributes = new HashMap<String, Object>();

	protected String requestURI = null;

	protected String requestedSessionId = null;

	Response response;

	ServletDef servlet;

	public void setResponse(Response response) {
		this.response = response;
	}

	FalconInputStream inputStream;

	Logger logger = Logger.getLogger(getClass());

	/**
	 * The currently active session for this request.
	 */
	protected Session session = null;

	/**
	 * Was the requested session ID received in a cookie?
	 */
	protected boolean requestedSessionCookie = false;

	/**
	 * Was the requested session ID received in a URL?
	 */
	protected boolean requestedSessionURL = false;

	/**
	 * The current dispatcher type.
	 */
	protected Object dispatcherType = null;

	/**
	 * The current request dispatcher path.
	 */
	protected Object requestDispatcherPath = null;

	/**
	 * Parse locales.
	 */
	protected boolean localesParsed = false;

	/**
	 * The preferred Locales assocaited with this Request.
	 */
	protected ArrayList<Locale> locales = new ArrayList<Locale>();
	/**
	 * The string parser we will use for parsing request lines.
	 */
	private StringParser parser = new StringParser();

	/**
	 * Hash map used in the getParametersMap method.
	 */
	protected ParameterMap parameterMap = new ParameterMap();
	/**
	 * Request parameters parsed flag.
	 */
	protected boolean parametersParsed = false;

	/**
	 * Using stream flag.
	 */
	protected boolean usingInputStream = false;

	/**
	 * Using writer flag.
	 */
	protected boolean usingReader = false;
	/**
	 * Maximum size of a POST which will be automatically parsed by the
	 * container. 2MB by default.
	 */
	protected int maxPostSize = 2 * 1024 * 1024;

	Parameters parameters;

	protected String localName;

	protected String localAddr;

	protected String queryString;

	protected int port;

	protected int remotePort;

	protected String serverName;

	protected String scheme;

	private MimeHeaders headers = new MimeHeaders();

	/**
	 * Post data buffer.
	 */
	protected static int CACHED_POST_LEN = 8192;
	protected byte[] postData = null;

	private Cookies falconookies = new Cookies(headers);

	public MimeHeaders getHeaders() {
		return headers;
	}

	Context context;

	private int resCode = -1;

	public int getResCode() {
		return resCode;
	}

	protected String charEncoding;

	/**
	 * The set of cookies associated with this Request.
	 */
	protected Cookie[] cookies = null;

	public String remoteAddr;

	public String remoteHost;

	/**
	 * The set of SimpleDateFormat formats to use in getDateHeader().
	 * 
	 * Notice that because SimpleDateFormat is not thread-safe, we can't declare
	 * formats[] as a static variable.
	 */
	protected SimpleDateFormat formats[] = {
			new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
			new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
			new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US) };

	/**
	 * Cookies parsed flag.
	 */
	protected boolean cookiesParsed = false;

	public void setContext(Context context) {
		this.context = context;
	}

	public Context getContext() {
		return context;
	}

	public String getAuthType() {
		return authType;
	}

	public String getContextPath() {
		return context.getContextPath();
	}

	/**
	 * The facade associated with this request.
	 */
	protected RequestFacade facade = null;

	/**
	 * Return the <code>ServletRequest</code> for which this object is the
	 * facade. This method must be implemented by a subclass.
	 */
	public HttpServletRequest getRequest() {
		if (facade == null) {
			facade = new RequestFacade(this);
		}
		return (facade);
	}

	protected MappingData mappingData = new MappingData();

	public MappingData getMappingData() {
		return (mappingData);
	}

	// Methods from HttpServletRequest.

	// / Gets the array of cookies found in this request.
	public Cookie[] getCookies() {

		if (!cookiesParsed)
			parseCookies();

		return cookies;
	}

	/**
	 * Parse cookies.
	 */
	protected void parseCookies() {

		cookiesParsed = true;

		Cookies serverCookies = falconookies;
		int count = serverCookies.getCookieCount();
		if (count <= 0)
			return;

		cookies = new Cookie[count];

		int idx = 0;
		for (int i = 0; i < count; i++) {
			ServerCookie scookie = serverCookies.getCookie(i);
			try {
				/*
				we must unescape the '\\' escape character
				*/
				Cookie cookie = new Cookie(scookie.getName().toString(), null);
				int version = scookie.getVersion();
				cookie.setVersion(version);
				cookie.setValue(unescape(scookie.getValue().toString()));
				cookie.setPath(unescape(scookie.getPath().toString()));
				String domain = scookie.getDomain().toString();
				if (domain != null)
					cookie.setDomain(unescape(domain));// avoid NPE
				String comment = scookie.getComment().toString();
				cookie.setComment(version == 1 ? unescape(comment) : null);
				cookies[idx++] = cookie;
			} catch (IllegalArgumentException e) {
				// Ignore bad cookie
			}
		}
		if (idx < count) {
			Cookie[] ncookies = new Cookie[idx];
			System.arraycopy(cookies, 0, ncookies, 0, idx);
			cookies = ncookies;
		}

	}

	public void addHeader(String key, String value) {
		headers.addValue(key).setString(value);
	}

	public void setHeader(String key, String value) {
		key = key.trim().toLowerCase();
		MessageBytes headerValue = headers.getValue(key);
		if (headerValue != null) {
			headers.setValue(value);
		} else {
			headers.addValue(key).setString(value);
		}
	}

	public String getContentEncoding() {
		return extractEncodingFromContentType(headers.getHeader(CONTENTTYPE));
	}

	protected String unescape(String s) {
		if (s == null)
			return null;
		if (s.indexOf('\\') == -1)
			return s;
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c != '\\')
				buf.append(c);
			else {
				if (++i >= s.length())
					throw new IllegalArgumentException();// invalid escape,
				// hence invalid
				// cookie
				c = s.charAt(i);
				buf.append(c);
			}
		}
		return buf.toString();
	}

	public int getContentLength() {
		return getIntHeader(CONTENTLENGTH);
	}

	// / Returns the MIME type of the request entity data, or null if
	// not known.
	// Same as the CGI variable CONTENT_TYPE.
	public String getContentType() {
		return getHeader(CONTENTTYPE);
	}

	// / Returns the protocol and version of the request as a string of
	// the form <protocol>/<major version>.<minor version>.
	// Same as the CGI variable SERVER_PROTOCOL.
	public String getProtocol() {
		return headers.getHeader(PROTOCOL);
	}

	public String getHeader(String name) {
		return headers.getHeader(name);
	}

	public int getIntHeader(String name) {
		String val = getHeader(name);
		if (val == null)
			return -1;
		return Integer.parseInt(val);
	}

	public long getDateHeader(String name) {

		String value = getHeader(name);
		if (value == null)
			return (-1L);

		// Attempt to convert the date header in a variety of formats
		long result = FastHttpDateFormat.parseDate(value, formats);
		if (result != (-1L)) {
			return result;
		}
		throw new IllegalArgumentException(value);
	}

	@SuppressWarnings("unchecked")
	public Enumeration getHeaderNames() {
		return headers.names();
	}

	@SuppressWarnings("unchecked")
	public Enumeration getHeaders(String header) {
		return headers.values(header);
	}

	public String getMethod() {
		return headers.getHeader(METHOD);
	}

	public String getPathInfo() {
		return (mappingData.pathInfo.toString());
	}

	public String getPathTranslated() {

		if (context == null)
			return (null);

		if (getPathInfo() == null) {
			return (null);
		} else {
			return (context.getRealPath(getPathInfo()));
		}
	}

	public String getQueryString() {
		if (queryString == null || queryString.equals("")) {
			return (null);
		} else {
			return queryString;
		}
	}

	public void setQueryString(String queryString) {

		this.queryString = queryString;
	}

	public String getRemoteUser() {
		throw new IllegalArgumentException("not implement");
	}

	public String getRequestURI() {
		return requestURI;
	}

	public void setRequestURI(String requestURI) {
		this.requestURI = requestURI;
	}

	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Reconstructs the URL the client used to make the request. The returned
	 * URL contains a protocol, server name, port number, and server path, but
	 * it does not include query string parameters. <br>
	 * Because this method returns a StringBuffer, not a string, you can modify
	 * the URL easily, for example, to append query parameters.
	 * <p>
	 * This method is useful for creating redirect messages and for reporting
	 * errors.
	 * 
	 * @return a StringBuffer object containing the reconstructed URL
	 * @since 2.3
	 */
	public java.lang.StringBuffer getRequestURL() {
		StringBuffer url = new StringBuffer();
		String scheme = getScheme();
		int port = getServerPort();
		if (port < 0)
			port = 80; // Work around java.net.URL bug

		url.append(scheme);
		url.append("://");
		url.append(getServerName());
		if ((scheme.equals("http") && (port != 80)) || (scheme.equals("https") && (port != 443))) {
			url.append(':');
			url.append(port);
		}
		url.append(getRequestURI());

		return (url);
	}

	public String getRequestedSessionId() {
		return requestedSessionId;
	}

	/**
	 * Set the requested session ID for this request. This is normally called by
	 * the HTTP Connector, when it parses the request headers.
	 * 
	 * @param id
	 *            The new session id
	 */
	public void setRequestedSessionId(String id) {

		this.requestedSessionId = id;

	}

	// http://localhost:8080/news/main/list.jsp (news为工程名)
	// System.out.println(request.getServletPath());
	// 结果：/main/list.jsp

	public String getServletPath() {
		return (mappingData.wrapperPath.toString());
	}

	public void setServletPath(String path) {
		if (path != null)
			mappingData.wrapperPath.setString(path);
	}

	public synchronized HttpSession getSession(boolean create) {
		Session session = doGetSession(create);
		if (session != null) {
			return session.getSession();
		} else {
			return null;
		}
	}

	/**
	 * Return the session associated with this Request, creating one if
	 * necessary and requested.
	 * 
	 * @param create
	 *            Create a new session if one does not exist
	 */
	public Session getSessionInternal(boolean create) {
		return doGetSession(create);
	}

	public Principal getUserPrincipal() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isRequestedSessionIdFromURL() {
		return isRequestedSessionIdFromUrl();
	}

	/**
	 * Checks whether the session id specified by this request came in as a
	 * cookie. (The requested session may not be one returned by the getSession
	 * method.)
	 */
	public boolean isRequestedSessionIdFromCookie() {
		if (requestedSessionId != null)
			return (requestedSessionCookie);
		else
			return (false);

	}

	// 该方法由请求的pipeline设置
	public void setRequestedSessionCookie(boolean flag) {

		this.requestedSessionCookie = flag;

	}

	public boolean isRequestedSessionIdFromUrl() {

		if (requestedSessionId != null)
			return (requestedSessionURL);
		else
			return (false);
	}

	/**
	 * Set a flag indicating whether or not the requested session ID for this
	 * request came in through a URL. 该方法由请求的pipeline设置
	 * 
	 * @param flag
	 *            The new flag
	 */
	public void setRequestedSessionURL(boolean flag) {

		this.requestedSessionURL = flag;

	}

	public boolean isRequestedSessionIdValid() {
		if (requestedSessionId == null)
			return (false);
		if (context == null)
			return (false);
		Manager manager = context.getManager();
		if (manager == null)
			return (false);
		Session session = null;
		try {
			session = manager.findSession(requestedSessionId);
		} catch (IOException e) {
			session = null;
		}
		if ((session != null) && session.isValid())
			return (true);
		else
			return (false);

	}

	public boolean isUserInRole(String arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	public Object getAttribute(String name) {

		if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
			return (dispatcherType == null) ? ApplicationFilterFactory.REQUEST_INTEGER
					: dispatcherType;
		} else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
			return (requestDispatcherPath == null) ? mappingData.requestPath.toString()
					: requestDispatcherPath.toString();
		}

		Object attr = attributes.get(name);

		// TODO 处理SSL属性
		return attr;
	}

	@SuppressWarnings("unchecked")
	public Enumeration getAttributeNames() {
		return new Enumerator(attributes.keySet(), true);
	}

	public String getCharacterEncoding() {
		String ct = headers.getHeader(CONTENTTYPE.toLowerCase());
		if (ct != null) {
			String enc = extractEncodingFromContentType(ct);
			if (enc != null)
				return enc;
		}
		return charEncoding;
	}

	private String extractEncodingFromContentType(String ct) {
		if (ct == null)
			return null;
		int scp = ct.indexOf(';');
		if (scp > 0) {
			scp = ct.toLowerCase().indexOf("charset=", scp);
			if (scp >= 0) {
				ct = ct.substring(scp + "charset=".length()).trim();
				scp = ct.indexOf(';');
				if (scp > 0)
					ct = ct.substring(0, scp);
				int l = ct.length();
				if (l > 2 && ct.charAt(0) == '"')
					return ct.substring(1, l - 1);
				return ct;
			}
		}
		return null;
	}

	public ServletInputStream getInputStream() throws IOException {

		if (inputStream == null) {
			throw new NullPointerException("InputStream is null");
		}
		return inputStream;
	}

	public void setInputStream(FalconInputStream inputStream) {

		usingInputStream = true;
		this.inputStream = inputStream;
	}

	/**
	 * Returns the host name of the Internet Protocol (IP) interface on which
	 * the request was received.
	 * 
	 * @return a <code>String</code> containing the host name of the IP on which
	 *         the request was received.
	 * 
	 * @since 2.4
	 */
	public String getLocalName() {
		return localName;
	}

	public void setLocalName(String localName) {
		this.localName = localName;
	}

	/**
	 * Returns the Internet Protocol (IP) address of the interface on which the
	 * request was received.
	 * 
	 * @return a <code>String</code> containing the IP address on which the
	 *         request was received.
	 * 
	 * @since 2.4
	 * 
	 */
	public String getLocalAddr() {
		return localAddr;
	}

	public void setLocalAddr(String localAddr) {

		this.localAddr = localAddr;
	}

	/**
	 * Returns the Internet Protocol (IP) port number of the interface on which
	 * the request was received.
	 * 
	 * @return an integer specifying the port number
	 * 
	 * @since 2.4
	 */
	public int getLocalPort() {
		return getServerPort();
	}

	/**
	 * For request: Returns the preferred Locale that the client will accept
	 * content in, based on the Accept-Language header. If the client request
	 * doesn't provide an Accept-Language header, this method returns the
	 * default locale for the server.
	 * 
	 * For response: Returns the locale specified for this response using the
	 * setLocale(java.util.Locale) method. Calls made to setLocale after the
	 * response is committed have no effect. If no locale has been specified,
	 * the container's default locale is returned.
	 */
	public Locale getLocale() {

		if (!localesParsed)
			parseLocales();

		if (locales.size() > 0) {
			return ((Locale) locales.get(0));
		} else {
			return (Locale.getDefault());
		}

	}

	@SuppressWarnings("unchecked")
	protected void parseLocales() {

		localesParsed = true;

		Enumeration values = getHeaders("accept-language");

		while (values.hasMoreElements()) {
			String value = values.nextElement().toString();
			parseLocalesHeader(value);
		}

	}

	/**
	 * 处理accept-language头的格式
	 */
	@SuppressWarnings("unchecked")
	protected void parseLocalesHeader(String value) {

		// Store the accumulated languages that have been requested in
		// a local collection, sorted by the quality value (so we can
		// add Locales in descending order). The values will be ArrayLists
		// containing the corresponding Locales to be added
		TreeMap locales = new TreeMap();

		// Preprocess the value to remove all whitespace
		int white = value.indexOf(' ');
		if (white < 0)
			white = value.indexOf('\t');
		if (white >= 0) {
			StringBuffer sb = new StringBuffer();
			int len = value.length();
			for (int i = 0; i < len; i++) {
				char ch = value.charAt(i);
				if ((ch != ' ') && (ch != '\t'))
					sb.append(ch);
			}
			value = sb.toString();
		}

		// Process each comma-delimited language specification
		parser.setString(value); // ASSERT: parser is available to us
		int length = parser.getLength();
		while (true) {

			// Extract the next comma-delimited entry
			int start = parser.getIndex();
			if (start >= length)
				break;
			int end = parser.findChar(',');
			String entry = parser.extract(start, end).trim();
			parser.advance(); // For the following entry

			// Extract the quality factor for this entry
			double quality = 1.0;
			int semi = entry.indexOf(";q=");
			if (semi >= 0) {
				try {
					quality = Double.parseDouble(entry.substring(semi + 3));
				} catch (NumberFormatException e) {
					quality = 0.0;
				}
				entry = entry.substring(0, semi);
			}

			// Skip entries we are not going to keep track of
			if (quality < 0.00005)
				continue; // Zero (or effectively zero) quality factors
			if ("*".equals(entry))
				continue; // FIXME - "*" entries are not handled

			// Extract the language and country for this entry
			String language = null;
			String country = null;
			String variant = null;
			int dash = entry.indexOf('-');
			if (dash < 0) {
				language = entry;
				country = "";
				variant = "";
			} else {
				language = entry.substring(0, dash);
				country = entry.substring(dash + 1);
				int vDash = country.indexOf('-');
				if (vDash > 0) {
					String cTemp = country.substring(0, vDash);
					variant = country.substring(vDash + 1);
					country = cTemp;
				} else {
					variant = "";
				}
			}
			if (!isAlpha(language) || !isAlpha(country) || !isAlpha(variant)) {
				continue;
			}

			// Add a new Locale to the list of Locales for this quality level
			Locale locale = new Locale(language, country, variant);
			Double key = new Double(-quality); // Reverse the order
			ArrayList<Locale> values = (ArrayList) locales.get(key);
			if (values == null) {
				values = new ArrayList();
				locales.put(key, values);
			}
			values.add(locale);

		}

		// Process the quality values in highest->lowest order (due to
		// negating the Double value when creating the key)
		Iterator keys = locales.keySet().iterator();
		while (keys.hasNext()) {
			Double key = (Double) keys.next();
			ArrayList list = (ArrayList) locales.get(key);
			Iterator values = list.iterator();
			while (values.hasNext()) {
				Locale locale = (Locale) values.next();
				addLocale(locale);
			}
		}

	}

	protected static final boolean isAlpha(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Add a Locale to the set of preferred Locales for this Request. The first
	 * added Locale will be the first one returned by getLocales().
	 * 
	 * @param locale
	 *            The new preferred Locale
	 */
	public void addLocale(Locale locale) {
		locales.add(locale);
	}

	@SuppressWarnings("unchecked")
	public Enumeration getLocales() {

		if (!localesParsed)
			parseLocales();

		if (locales.size() > 0)
			return (new Enumerator(locales));
		ArrayList results = new ArrayList();
		results.add(Locale.getDefault());
		return (new Enumerator(results));
	}

	/**
	 * Return the value of the specified request parameter, if any; otherwise,
	 * return <code>null</code>. If there is more than one value defined, return
	 * only the first one.
	 * 
	 * @param name
	 *            Name of the desired request parameter
	 */
	public String getParameter(String name) {

		if (!parametersParsed)
			parseParameters();

		return parameters.getParameter(name);

	}

	/**
	 * Returns a <code>Map</code> of the parameters of this request. Request
	 * parameters are extra information sent with the request. For HTTP
	 * servlets, parameters are contained in the query string or posted form
	 * data.
	 * 
	 * @return A <code>Map</code> containing parameter names as keys and
	 *         parameter values as map values.
	 */
	@SuppressWarnings("unchecked")
	public Map getParameterMap() {

		if (parameterMap.isLocked())
			return parameterMap;

		Enumeration enumeration = getParameterNames();
		while (enumeration.hasMoreElements()) {
			String name = enumeration.nextElement().toString();
			String[] values = getParameterValues(name);
			parameterMap.put(name, values);
		}

		parameterMap.setLocked(true);

		return parameterMap;

	}

	/**
	 * Return the names of all defined request parameters for this request.
	 */
	@SuppressWarnings("unchecked")
	public Enumeration getParameterNames() {

		if (!parametersParsed)
			parseParameters();

		return parameters.getParameterNames();

	}

	/**
	 * Return the defined values for the specified request parameter, if any;
	 * otherwise, return <code>null</code>.
	 * 
	 * @param name
	 *            Name of the desired request parameter
	 */
	public String[] getParameterValues(String name) {

		if (!parametersParsed)
			parseParameters();

		return parameters.getParameterValues(name);

	}

	/**
	 * Parse request parameters.
	 */
	protected void parseParameters() {

		parametersParsed = true;

		parameters = new Parameters();
		MessageBytes queryMB = MessageBytes.newInstance();

		if (queryString != null) {
			queryMB.setBytes(queryString.getBytes(), 0, queryString.length());
		}
		parameters.setQuery(queryMB);
		parameters.setURLDecoder(new UDecoder());
		parameters.setHeaders(headers);

		// getCharacterEncoding() may have been overridden to search for
		// hidden form field containing request encoding
		String enc = getCharacterEncoding();

		boolean useBodyEncodingForURI = getUseBodyEncodingForURI();
		if (enc != null) {
			parameters.setEncoding(enc);
			if (useBodyEncodingForURI) {
				parameters.setQueryStringEncoding(enc);
			}
		} else {
			parameters.setEncoding(DEFAULT_CHARACTER_ENCODING);
			if (useBodyEncodingForURI) {
				parameters.setQueryStringEncoding(DEFAULT_CHARACTER_ENCODING);
			}
		}

		parameters.handleQueryParameters();

		if (usingInputStream || usingReader)
			return;

		if (!getMethod().equalsIgnoreCase("POST"))
			return;

		String contentType = getContentType();
		if (contentType == null)
			contentType = "";
		int semicolon = contentType.indexOf(';');
		if (semicolon >= 0) {
			contentType = contentType.substring(0, semicolon).trim();
		} else {
			contentType = contentType.trim();
		}
		if (!("application/x-www-form-urlencoded".equals(contentType)))
			return;

		int len = getContentLength();

		if (len > 0) {
			int maxPostSize = getMaxPostSize();
			if ((maxPostSize > 0) && (len > maxPostSize)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Post too large");
				}
				return;
			}
			byte[] formData = null;
			if (len < CACHED_POST_LEN) {
				if (postData == null)
					postData = new byte[CACHED_POST_LEN];
				formData = postData;
			} else {
				formData = new byte[len];
			}
			try {
				if (readPostBody(formData, len) != len) {
					return;
				}
			} catch (IOException e) {

				e.printStackTrace();
				return;
			}
			parameters.processParameters(formData, 0, len);
		}

	}

	/**
	 * Read post body in an array.
	 */
	protected int readPostBody(byte body[], int len) throws IOException {

		int offset = 0;
		do {
			int inputLen = getInputStream().read(body, offset, len - offset);
			if (inputLen <= 0) {
				return offset;
			}
			offset += inputLen;
		} while ((len - offset) > 0);
		return len;
	}

	public boolean getUseBodyEncodingForURI() {
		return false;
	}

	public int getMaxPostSize() {
		return maxPostSize;
	}

	public void setMaxPostSize(int size) {
		this.maxPostSize = size;
	}

	public BufferedReader getReader() {
		synchronized (inputStream) {
			if (inputStream.isReturnedAsStream())
				throw new IllegalStateException("Already returned as a stream.");
			inputStream.setReturnedAsReader(true);
		}
		if (charEncoding != null)
			try {
				usingReader = true;
				return new BufferedReader(new InputStreamReader(inputStream, charEncoding));
			} catch (UnsupportedEncodingException uee) {
			}
		return new BufferedReader(new InputStreamReader(inputStream));
	}

	public int getRemotePort() {
		return remotePort;
	}

	public void setRemotePort(int port) {
		this.remotePort = port;
	}

	/**
	 * Return a RequestDispatcher that wraps the resource at the specified path,
	 * which may be interpreted as relative to the current request path.
	 * 该方法与context类对应的方法区别是context需要以/开头的绝对路径，而本方法可以是 相对路径
	 * 
	 * @param path
	 *            Path of the resource to be wrapped
	 */
	public RequestDispatcher getRequestDispatcher(String path) {

		if (context == null)
			return (null);

		// If the path is already context-relative, just pass it through
		if (path == null)
			return (null);
		else if (path.startsWith("/"))
			return (context.getRequestDispatcher(path));

		// Convert a request-relative path to a context-relative one
		String servletPath = (String) getAttribute(Globals.INCLUDE_SERVLET_PATH_ATTR);
		if (servletPath == null)
			servletPath = getServletPath();

		// Add the path info, if there is any
		String pathInfo = getPathInfo();
		String requestPath = null;

		if (pathInfo == null) {
			requestPath = servletPath;
		} else {
			requestPath = servletPath + pathInfo;
		}

		int pos = requestPath.lastIndexOf('/');
		String relative = null;
		if (pos >= 0) {
			relative = RequestUtil.normalize(requestPath.substring(0, pos + 1) + path);
		} else {
			relative = RequestUtil.normalize(requestPath + path);
		}

		return (context.getRequestDispatcher(relative));

	}

	public String getScheme() {
		return scheme;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public String getServerName() {

		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public int getServerPort() {
		return port;
	}

	public String getRemoteAddr() {
		return remoteAddr;
	}

	public void setRemoteAddr(String remoteAddr) {
		this.remoteAddr = remoteAddr;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public void setRemoteHost(String remoteHost) {
		this.remoteHost = remoteHost;
	}

	public String getRealPath(String path) {

		if (context == null)
			return (null);
		try {
			return (context.getRealPath(path));
		} catch (IllegalArgumentException e) {
			return (null);
		}

	}

	public boolean isSecure() {
		return "https".equals(getScheme());
	}

	/**
	 * 是否需要判断属性的可访问性，eg.是否只读
	 */
	public void removeAttribute(String name) {

		Object value = attributes.remove(name);

		Object listeners[] = context.getApplicationEventListeners();
		if ((listeners == null) || (listeners.length == 0))
			return;
		ServletRequestAttributeEvent event = new ServletRequestAttributeEvent(context,
				getRequest(), name, value);
		for (int i = 0; i < listeners.length; i++) {
			if (!(listeners[i] instanceof ServletRequestAttributeListener))
				continue;
			ServletRequestAttributeListener listener = (ServletRequestAttributeListener) listeners[i];
			try {
				listener.attributeRemoved(event);
			} catch (Throwable t) {
				attributes.put(Globals.EXCEPTION_ATTR, t);
			}
		}
	}

	public void setAttribute(String key, Object value) {

		// Name cannot be null
		if (key == null)
			throw new IllegalArgumentException("Attribute name can not be null");

		// Null value is the same as removeAttribute()
		if (value == null) {
			removeAttribute(key);
			return;
		}

		if (key.equals(Globals.DISPATCHER_TYPE_ATTR)) {
			dispatcherType = value;
			return;
		} else if (key.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
			requestDispatcherPath = value;
			return;
		}

		Object oldValue = null;
		boolean replaced = false;

		oldValue = attributes.put(key, value);
		if (oldValue != null) {
			replaced = true;
		}

		// Notify interested application event listeners
		Object listeners[] = context.getApplicationEventListeners();
		if ((listeners == null) || (listeners.length == 0))
			return;
		ServletRequestAttributeEvent event = null;
		if (replaced)
			event = new ServletRequestAttributeEvent(context, getRequest(), key, oldValue);
		else
			event = new ServletRequestAttributeEvent(context, getRequest(), key, value);

		for (int i = 0; i < listeners.length; i++) {
			if (!(listeners[i] instanceof ServletRequestAttributeListener))
				continue;
			ServletRequestAttributeListener listener = (ServletRequestAttributeListener) listeners[i];
			try {
				if (replaced) {
					listener.attributeReplaced(event);
				} else {
					listener.attributeAdded(event);
				}
			} catch (Throwable t) {
				attributes.put(Globals.EXCEPTION_ATTR, t);
			}
		}
	}

	public void setCharacterEncoding(String _enc) {
		charEncoding = _enc;
	}

	public HttpSession getSession() {
		return getSession(true);
	}

	public void addIncludeAttributes(String requestURI, String contextPath, String servletPath,
			String pathInfo, String queryString) {
		Map<String, String> includeAttributes = new HashMap<String, String>();
		if (requestURI != null) {
			includeAttributes.put(FalconRequestDispatcher.INCLUDE_REQUEST_URI, requestURI);
		}
		if (contextPath != null) {
			includeAttributes.put(FalconRequestDispatcher.INCLUDE_CONTEXT_PATH, contextPath);
		}
		if (servletPath != null) {
			includeAttributes.put(FalconRequestDispatcher.INCLUDE_SERVLET_PATH, servletPath);
		}
		if (pathInfo != null) {
			includeAttributes.put(FalconRequestDispatcher.INCLUDE_PATH_INFO, pathInfo);
		}
		if (queryString != null) {
			includeAttributes.put(FalconRequestDispatcher.INCLUDE_QUERY_STRING, queryString);
		}
		this.attributes.putAll(includeAttributes);
	}

	public void recycle() {

		context = null;

		dispatcherType = null;
		requestDispatcherPath = null;

		usingInputStream = false;
		usingReader = false;

		authType = null;
		cookiesParsed = false;
		localAddr = null;
		localName = null;
		port = -1;
		remotePort = -1;
		localesParsed = false;
		locales.clear();

		attributes.clear();
		cookies = null;

		if (session != null) {
			session = null;
		}
		session = null;
		requestedSessionCookie = false;
		requestedSessionId = null;
		requestedSessionURL = false;

		parameterMap = new ParameterMap();

		parameters = null;
		remoteAddr = null;
		remoteHost = null;

		mappingData.recycle();

	}

	protected Session doGetSession(boolean create) {

		// There cannot be a session if no context has been assigned yet
		if (context == null)
			return (null);

		// Return the current session if it exists and is valid
		if ((session != null) && !session.isValid())
			session = null;
		if (session != null)
			return (session);

		// Return the requested session if it exists and is valid
		Manager manager = null;
		if (context != null)
			manager = context.getManager();
		if (manager == null)
			return (null); // Sessions are not supported
		if (requestedSessionId != null) {
			try {
				session = manager.findSession(requestedSessionId);
			} catch (IOException e) {
				session = null;
			}
			if ((session != null) && !session.isValid())
				session = null;
			if (session != null) {
				session.access();
				return (session);
			}
		}

		// Create a new session if requested and the response is not committed
		if (!create)
			return (null);
		if ((context != null) && (response != null) && context.getCookies()
				&& response.getResponse().isCommitted()) {
			throw new IllegalStateException("response has bean Committed");
		}

		// Attempt to reuse session id if one was submitted in a cookie
		// Do not reuse the session id if it is from a URL, to prevent possible
		// phishing attacks
		if (isRequestedSessionIdFromCookie()) {
			session = manager.createSession(getRequestedSessionId());
		} else {
			session = manager.createSession(null);
		}

		// Creating a new session cookie based on that session
		if ((session != null) && (getContext() != null) && getContext().getCookies()) {
			Cookie cookie = new Cookie(Globals.SESSION_COOKIE_NAME, session.getId());
			configureSessionCookie(cookie);
			response.addCookie(cookie);
		}

		if (session != null) {
			session.access();
			return (session);
		} else {
			return (null);
		}
	}

	/**
	 * Configures the given JSESSIONID cookie.
	 * 
	 * @param cookie
	 *            The JSESSIONID cookie to be configured
	 */
	protected void configureSessionCookie(Cookie cookie) {
		cookie.setMaxAge(-1);
		String contextPath = null;
		if (getContext() != null) {
			contextPath = getContext().getEncodedPath();
		}
		if ((contextPath != null) && (contextPath.length() > 0)) {
			cookie.setPath(contextPath);
		} else {
			cookie.setPath("/");
		}
		if (isSecure()) {
			cookie.setSecure(true);
		}
	}

	public void setServletDef(ServletDef servlet) {
		this.servlet = servlet;
	}

	public ServletDef getServletDef() {
		return servlet;
	}

}
