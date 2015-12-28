package com.falcon.server.server.servlet;

import static com.falcon.server.start.ServerConstants.CONTENTTYPE;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import com.falcon.server.http.ServerCookie;
import com.falcon.server.http.util.CharChunk;
import com.falcon.server.http.util.DateTool;
import com.falcon.server.http.util.FastHttpDateFormat;
import com.falcon.server.http.util.MimeHeaders;
import com.falcon.server.http.util.URL;
import com.falcon.server.server.Context;
import com.falcon.server.server.Request;
import com.falcon.server.server.Response;
import com.falcon.server.server.Session;
import com.falcon.server.util.Globals;
import com.falcon.server.util.UEncoder;
import com.falcon.server.util.Utils;

public class FalconResponse implements Response {

	public int resCode = -1;

	protected long contentLength = -1;

	/**
	 * Has the charset been explicitly set.
	 */
	protected boolean charsetSet = false;

	/**
	 * Using output stream flag.
	 */
	protected boolean usingOutputStream = false;

	private PrintWriter pw;

	/**
	 * Using writer flag.
	 */
	protected boolean usingWriter = false;

	protected String contentType = null;
	protected String contentLanguage = null;
	protected String characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
	private Locale locale = Locale.getDefault();

	/**
	 * The set of Cookies associated with this Response.
	 */
	protected ArrayList<Cookie> cookies = new ArrayList<Cookie>();

	/**
	 * The facade associated with this response.
	 */
	protected ResponseFacade facade = null;

	/**
	 * Recyclable buffer to hold the redirect URL.
	 */
	protected CharChunk redirectURLCC = new CharChunk();

	/**
	 * URL encoder.
	 */
	protected UEncoder urlEncoder = new UEncoder();

	public static final String BGCOLOR = "BGCOLOR=\"#D1E9FE\"";

	FalconOutputStream outputStream;

	Request request;

	public void setResponse(Request request) {
		this.request = request;
	}

	Context context;

	public void setContext(Context context) {
		this.context = context;
	}

	public Context getContext() {
		return context;
	}

	/**
	 * Return the <code>ServletResponse</code> for which this object is the
	 * facade.
	 */
	public HttpServletResponse getResponse() {
		if (facade == null) {
			facade = new ResponseFacade(this);
		}
		return (facade);
	}

	public String resMessage;

	/**
	 * Response headers.
	 */
	protected MimeHeaders headers = new MimeHeaders();

	public MimeHeaders getHeaders() {
		return headers;
	}

	public void addCookie(Cookie cookie) {
		if (isCommitted())
			return;

		final StringBuffer sb = new StringBuffer();

		ServerCookie.appendCookieValue(sb, cookie.getVersion(), cookie.getName(),
				cookie.getValue(), cookie.getPath(), cookie.getDomain(), cookie.getComment(),
				cookie.getMaxAge(), cookie.getSecure());
		// if we reached here, no exception, cookie is valid
		// the header name is Set-Cookie for both "old" and v.1 ( RFC2109 )
		// RFC2965 is not supported by browsers and the Servlet spec
		// asks for 2109.
		addHeader("Set-Cookie", sb.toString());

		cookies.add(cookie);
	}

	public void setOutputStream(FalconOutputStream outputStream) {

		this.outputStream = outputStream;
	}

	public boolean containsHeader(String name) {
		return headers.getHeader(name) != null;
	}

	public String encodeURL(String url) {
		String absolute = toAbsolute(url);
		if (isEncodeable(absolute)) {
			// W3c spec clearly said
			if (url.equalsIgnoreCase("")) {
				url = absolute;
			}
			return (toEncoded(url, request.getSessionInternal(true).getId()));
		} else {
			return (url);
		}

	}

	public String encodeRedirectURL(String url) {
		return encodeURL(url);
	}

	public String encodeRedirectUrl(String url) {
		return encodeRedirectURL(url);
	}

	public String encodeUrl(String url) {
		return encodeURL(url);
	}

	public void sendError(int resCode, String resMessage) throws IOException {
		setStatus(resCode, resMessage);
		realSendError();
	}

	public void sendError(int resCode) throws IOException {
		setStatus(resCode);
		realSendError();
	}

	private void realSendError() throws IOException {
		if (isCommitted())
			throw new IllegalStateException(
					"Can not send an error, headers have been already written");
		// if (((ServeOutputStream) out).isInInclude()) // ignore
		// return;
		setContentType("text/html");
		StringBuffer sb = new StringBuffer(100);
		int lsp = resMessage.indexOf('\n');
		sb.append("<HTML><HEAD>").append(
				"<TITLE>" + resCode + " " + (lsp < 0 ? resMessage : resMessage.substring(0, lsp))
						+ "</TITLE>").append("</HEAD><BODY " + BGCOLOR).append(
				"><H2>" + resCode + " " + (lsp < 0 ? resMessage : resMessage.substring(0, lsp))
						+ "</H2>");
		if (lsp > 0)
			sb.append("<PRE>").append(Utils.htmlEncode(resMessage.substring(lsp), false)).append(
					"</PRE>");
		sb.append("<HR>");
		sb.append("</BODY></HTML>");
		setContentLength(sb.length());
		outputStream.print(sb.toString());
		closeStreams();
	}

	public void setContentType(String type) {

		if (isCommitted())
			return;
		// Ignore charset if getWriter() has already been called
		if (usingWriter) {
			if (type != null) {
				int index = type.indexOf(";");
				if (index != -1) {
					type = type.substring(0, index);
				}
			}
		}
		int semicolonIndex = -1;

		if (type == null) {
			this.contentType = null;
			return;
		}

		/*
		 * Remove the charset param (if any) from the Content-Type, and use it
		 * to set the response encoding.
		 * The most recent response encoding setting will be appended to the
		 * response's Content-Type (as its charset param) by getContentType();
		 */
		boolean hasCharset = false;
		int len = type.length();
		int index = type.indexOf(';');
		while (index != -1) {
			semicolonIndex = index;
			index++;
			while (index < len && Character.isSpaceChar(type.charAt(index))) {
				index++;
			}
			if (index + 8 < len && type.charAt(index) == 'c' && type.charAt(index + 1) == 'h'
					&& type.charAt(index + 2) == 'a' && type.charAt(index + 3) == 'r'
					&& type.charAt(index + 4) == 's' && type.charAt(index + 5) == 'e'
					&& type.charAt(index + 6) == 't' && type.charAt(index + 7) == '=') {
				hasCharset = true;
				break;
			}
			index = type.indexOf(';', index);
		}

		if (!hasCharset) {
			this.contentType = type;
			return;
		}

		this.contentType = type.substring(0, semicolonIndex);
		String tail = type.substring(index + 8);
		int nextParam = tail.indexOf(';');
		String charsetValue = null;
		if (nextParam != -1) {
			this.contentType += tail.substring(nextParam);
			charsetValue = tail.substring(0, nextParam);
		} else {
			charsetValue = tail;
		}

		// The charset value may be quoted, but must not contain any quotes.
		if (charsetValue != null && charsetValue.length() > 0) {
			charsetSet = true;
			charsetValue = charsetValue.replace('"', ' ');
			this.characterEncoding = charsetValue.trim();
		}

	}

	public void setContentLength(int length) {
		contentLength = length;
	}

	public void setContentLength(long length) {
		contentLength = length;
	}

	public long getContentLength() {
		return contentLength;
	}

	public void setIntHeader(String header, int value) {
		setHeader(header, Integer.toString(value));
	}

	public void closeStreams() throws IOException {
		IOException ioe = null;

		try {
			outputStream.close();
		} catch (IOException io1) {
			if (ioe != null)
				ioe = (IOException) ioe.initCause(io1);
			else
				ioe = io1;
		}
	}

	public void sendRedirect(String location) throws IOException {
		if (isCommitted())
			throw new IllegalStateException("Can not redirect, headers have been already written");

		resetBuffer();

		try {
			String absolute = toAbsolute(location);
			setStatus(SC_FOUND);
			setHeader("Location", absolute);
		} catch (IllegalArgumentException e) {
			setStatus(SC_NOT_FOUND);
		}

		setHeader("Location", location);
		setStatus(SC_MOVED_TEMPORARILY);
		setContentType("text/html");
		setContentLength(0);
		getWriter().flush();
	}

	public void setLongHeader(String header, long value) {
		setHeader(header, Long.toString(value));
	}

	public void setDateHeader(String header, long value) {

		if (isCommitted())
			return;

		SimpleDateFormat format = null;

		if (format == null) {
			format = new SimpleDateFormat(DateTool.HTTP_RESPONSE_DATE_HEADER, Locale.US);
			format.setTimeZone(TimeZone.getTimeZone("GMT"));
		}

		setHeader(header, FastHttpDateFormat.formatDate(value, format));

	}

	public void setHeader(String name, String value) {

		if (isCommitted())
			return;

		char cc = name.charAt(0);
		if (cc == 'C' || cc == 'c') {
			if (checkSpecialHeader(name, value))
				return;
		}
		headers.setValue(name).setString(value);
	}

	/**
	 * Set internal fields for special header names. Called from set/addHeader.
	 * Return true if the header is special, no need to set the header.
	 */
	private boolean checkSpecialHeader(String name, String value) {
		// XXX Eliminate redundant fields !!!
		// ( both header and in special fields )
		if (name.equalsIgnoreCase("Content-Type")) {
			setContentType(value);
			return true;
		}
		if (name.equalsIgnoreCase("Content-Length")) {
			try {
				long cL = Long.parseLong(value);
				setContentLength(cL);
				return true;
			} catch (NumberFormatException ex) {
				// Do nothing - the spec doesn't have any "throws"
				// and the user might know what he's doing
				return false;
			}
		}
		if (name.equalsIgnoreCase("Content-Language")) {
			// XXX XXX Need to construct Locale or something else
		}
		return false;
	}

	public void setStatus(int resCode, String resMessage) {
		this.resCode = resCode;
		this.resMessage = resMessage;
	}

	// / Sets the status code and a default message for this response.
	// @param resCode the status code
	public void setStatus(int resCode) {
		switch (resCode) {
		case SC_CONTINUE:
			setStatus(resCode, "Continue");
			break;
		case SC_SWITCHING_PROTOCOLS:
			setStatus(resCode, "Switching protocols");
			break;
		case SC_OK:
			setStatus(resCode, "Ok");
			break;
		case SC_CREATED:
			setStatus(resCode, "Created");
			break;
		case SC_ACCEPTED:
			setStatus(resCode, "Accepted");
			break;
		case SC_NON_AUTHORITATIVE_INFORMATION:
			setStatus(resCode, "Non-authoritative");
			break;
		case SC_NO_CONTENT:
			setStatus(resCode, "No content");
			break;
		case SC_RESET_CONTENT:
			setStatus(resCode, "Reset content");
			break;
		case SC_PARTIAL_CONTENT:
			setStatus(resCode, "Partial content");
			break;
		case SC_MULTIPLE_CHOICES:
			setStatus(resCode, "Multiple choices");
			break;
		case SC_MOVED_PERMANENTLY:
			setStatus(resCode, "Moved permanentently");
			break;
		case SC_MOVED_TEMPORARILY:
			setStatus(resCode, "Moved temporarily");
			break;
		case SC_SEE_OTHER:
			setStatus(resCode, "See other");
			break;
		case SC_NOT_MODIFIED:
			setStatus(resCode, "Not modified");
			break;
		case SC_USE_PROXY:
			setStatus(resCode, "Use proxy");
			break;
		case SC_BAD_REQUEST:
			setStatus(resCode, "Bad request");
			break;
		case SC_UNAUTHORIZED:
			setStatus(resCode, "Unauthorized");
			break;
		case SC_PAYMENT_REQUIRED:
			setStatus(resCode, "Payment required");
			break;
		case SC_FORBIDDEN:
			setStatus(resCode, "Forbidden");
			break;
		case SC_NOT_FOUND:
			setStatus(resCode, "Not found");
			break;
		case SC_METHOD_NOT_ALLOWED:
			setStatus(resCode, "Method not allowed");
			break;
		case SC_NOT_ACCEPTABLE:
			setStatus(resCode, "Not acceptable");
			break;
		case SC_PROXY_AUTHENTICATION_REQUIRED:
			setStatus(resCode, "Proxy auth required");
			break;
		case SC_REQUEST_TIMEOUT:
			setStatus(resCode, "Request timeout");
			break;
		case SC_CONFLICT:
			setStatus(resCode, "Conflict");
			break;
		case SC_GONE:
			setStatus(resCode, "Gone");
			break;
		case SC_LENGTH_REQUIRED:
			setStatus(resCode, "Length required");
			break;
		case SC_PRECONDITION_FAILED:
			setStatus(resCode, "Precondition failed");
			break;
		case SC_REQUEST_ENTITY_TOO_LARGE:
			setStatus(resCode, "Request entity too large");
			break;
		case SC_REQUEST_URI_TOO_LONG:
			setStatus(resCode, "Request URI too long");
			break;
		case SC_UNSUPPORTED_MEDIA_TYPE:
			setStatus(resCode, "Unsupported media type");
			break;
		case SC_INTERNAL_SERVER_ERROR:
			setStatus(resCode, "Internal server error");
			break;
		case SC_NOT_IMPLEMENTED:
			setStatus(resCode, "Not implemented");
			break;
		case SC_BAD_GATEWAY:
			setStatus(resCode, "Bad gateway");
			break;
		case SC_SERVICE_UNAVAILABLE:
			setStatus(resCode, "Service unavailable");
			break;
		case SC_GATEWAY_TIMEOUT:
			setStatus(resCode, "Gateway timeout");
			break;
		case SC_HTTP_VERSION_NOT_SUPPORTED:
			setStatus(resCode, "HTTP version not supported");
			break;
		case 207:
			setStatus(resCode, "Multi Status");
			break;
		default:
			setStatus(resCode, "");
			break;
		}
	}

	public void flushBuffer() throws java.io.IOException {
		outputStream.flush();
	}

	/**
	 * Clears the content of the underlying buffer in the response without
	 * clearing headers or status code. If the response has been committed, this
	 * method throws an IllegalStateException.
	 * 
	 * @since 2.3
	 */
	public void resetBuffer() {
		outputStream.reset();
	}

	public int getBufferSize() {
		return outputStream.getBufferSize();
	}

	public void setBufferSize(int size) {
		outputStream.setBufferSize(size);
	}

	public String getCharacterEncoding() {
		String ct = (String) headers.getHeader(CONTENTTYPE.toLowerCase());
		if (ct != null) {
			String enc = extractEncodingFromContentType(ct);
			if (enc != null)
				return enc;
		}
		return request.getCharacterEncoding();
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

	public String getContentType() {

		String ret = contentType;

		if (ret != null && characterEncoding != null && charsetSet) {
			ret = ret + ";charset=" + characterEncoding;
		}
		return ret;
	}

	public java.util.Locale getLocale() {
		return locale;
	}

	public ServletOutputStream getOutputStream() {

		if (usingWriter)
			throw new IllegalStateException("Already returned as a writer");
		usingOutputStream = true;
		return outputStream;
	}

	//针对字符输出使用getWriter(),针对二进制数据使用getOutputStream(),两个方法不可以同时使用。
	public PrintWriter getWriter() throws IOException {
		synchronized (outputStream) {
			if (usingOutputStream)
				throw new IllegalStateException("Already was returned as servlet output stream");
			String encoding = getCharacterEncoding();
			if (encoding == null)
				encoding = "iso-8859-1";
			pw = new PrintWriter(new OutputStreamWriter(outputStream, encoding));
			usingWriter = true;
		}
		return pw;
	}

	public PrintWriter getInternelWriter() {
		return pw;
	}

	public boolean isUsingWriter() {
		return usingWriter;
	}

	/**
	 * commited response has already had its status code and headers written.
	 * 
	 * @return a boolean indicating if the response has been committed
	 * @see setBufferSize(int), getBufferSize(), flushBuffer(), reset()
	 */
	public boolean isCommitted() {
		return outputStream.lengthWritten() > 0;
	}

	/**
	 * Clears any data that exists in the buffer as well as the status code and
	 * headers. If the response has been committed, this method throws an
	 * IllegalStateException.
	 * 
	 * @throws java.lang.IllegalStateException
	 *             - if the response has already been committed
	 * @see setBufferSize(int), getBufferSize(), flushBuffer(), isCommitted()
	 */
	public void reset() throws IllegalStateException {
		// new Exception("RESET").printStackTrace();
		if (!isCommitted()) {
			usingOutputStream = false;
			usingWriter = false;
			cookies = null;
			pw = null;
			outputStream.reset();
		} else
			throw new IllegalStateException("Header have already been committed.");
	}

	public void addDateHeader(String header, long date) {

		if (isCommitted())
			return;

		SimpleDateFormat format = null;
		if (format == null) {
			format = new SimpleDateFormat(DateTool.HTTP_RESPONSE_DATE_HEADER, Locale.US);
			format.setTimeZone(TimeZone.getTimeZone("GMT"));
		}

		addHeader(header, FastHttpDateFormat.formatDate(date, format));

	}

	public void addHeader(String name, String value) {
		char cc = name.charAt(0);
		if (cc == 'C' || cc == 'c') {
			if (checkSpecialHeader(name, value))
				return;
		}
		headers.addValue(name).setString(value);
	}

	public void addIntHeader(String header, int value) {
		addHeader(header, Integer.toString(value));
	}

	/**
	 * Overrides the name of the character encoding used in the body of this
	 * request. This method must be called prior to reading request parameters
	 * or reading input using getReader().
	 * 
	 * @param a
	 *            - String containing the name of the chararacter encoding.
	 * @throws java.io.UnsupportedEncodingException
	 *             - if this is not a valid encoding
	 * @since JSDK 2.3
	 */
	public void setCharacterEncoding(String _enc) {

		if (isCommitted())
			return;

		// Ignore any call made after the getWriter has been invoked
		// The default should be used
		if (usingWriter)
			return;
		characterEncoding = _enc;
		charsetSet = true;

	}

	public void setLocale(java.util.Locale locale) {
		if (locale == null) {
			return; // throw an exception?
		}

		// Save the locale for use by getLocale()
		this.locale = locale;

		// Set the contentLanguage for header output
		contentLanguage = locale.getLanguage();
		if ((contentLanguage != null) && (contentLanguage.length() > 0)) {
			String country = locale.getCountry();
			StringBuffer value = new StringBuffer(contentLanguage);
			if ((country != null) && (country.length() > 0)) {
				value.append('-');
				value.append(country);
			}
			contentLanguage = value.toString();
		}

	}

	/**
	 * Release all object references, and initialize instance variables, in
	 * preparation for reuse of this object.
	 */
	public void recycle() {

		usingOutputStream = false;
		usingWriter = false;

		cookies.clear();
	}

	/**
	 * Convert (if necessary) and return the absolute URL that represents the
	 * resource referenced by this possibly relative URL. If this URL is already
	 * absolute, return it unchanged.
	 * 
	 * @param location
	 *            URL to be (possibly) converted and then returned
	 * 
	 * @exception IllegalArgumentException
	 *                if a MalformedURLException is thrown when converting the
	 *                relative URL to an absolute one
	 */
	private String toAbsolute(String location) {

		if (location == null)
			return (location);

		boolean leadingSlash = location.startsWith("/");

		if (leadingSlash || !hasScheme(location)) {

			redirectURLCC.recycle();

			String scheme = request.getScheme();
			String name = request.getServerName();
			int port = request.getServerPort();

			try {
				redirectURLCC.append(scheme, 0, scheme.length());
				redirectURLCC.append("://", 0, 3);
				redirectURLCC.append(name, 0, name.length());
				if ((scheme.equals("http") && port != 80)
						|| (scheme.equals("https") && port != 443)) {
					redirectURLCC.append(':');
					String portS = port + "";
					redirectURLCC.append(portS, 0, portS.length());
				}
				if (!leadingSlash) {
					String relativePath = request.getRequestURI();
					int pos = relativePath.lastIndexOf('/');
					relativePath = relativePath.substring(0, pos);

					String encodedURI = null;

					encodedURI = urlEncoder.encodeURL(relativePath);

					redirectURLCC.append(encodedURI, 0, encodedURI.length());
					redirectURLCC.append('/');
				}
				redirectURLCC.append(location, 0, location.length());
			} catch (IOException e) {
				IllegalArgumentException iae = new IllegalArgumentException(location);
				iae.initCause(e);
				throw iae;
			}

			return redirectURLCC.toString();

		} else {

			return (location);

		}

	}

	/**
	 * Determine if a URI string has a <code>scheme</code> component.
	 */
	private boolean hasScheme(String uri) {
		int len = uri.length();
		for (int i = 0; i < len; i++) {
			char c = uri.charAt(i);
			if (c == ':') {
				return i > 0;
			} else if (!URL.isSchemeChar(c)) {
				return false;
			}
		}
		return false;
	}

	/**
	 * Return <code>true</code> if the specified URL should be encoded with a
	 * session identifier. This will be true if all of the following conditions
	 * are met:
	 * <ul>
	 * <li>The request we are responding to asked for a valid session
	 * <li>The requested session ID was not received via a cookie
	 * <li>The specified URL points back to somewhere within the web application
	 * that is responding to this request
	 * </ul>
	 * 
	 * @param location
	 *            Absolute URL to be validated
	 */
	protected boolean isEncodeable(final String location) {

		if (location == null)
			return (false);

		// Is this an intra-document reference?
		if (location.startsWith("#"))
			return (false);

		// Are we in a valid session that is not using cookies?
		final Request hreq = request;
		final Session session = hreq.getSessionInternal(false);
		if (session == null)
			return (false);
		if (hreq.isRequestedSessionIdFromCookie())
			return (false);

		return doIsEncodeable(hreq, session, location);
	}

	private boolean doIsEncodeable(Request hreq, Session session, String location) {
		// Is this a valid absolute URL?
		URL url = null;
		try {
			url = new URL(location);
		} catch (MalformedURLException e) {
			return (false);
		}

		// Does this URL match down to (and including) the context path?
		if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol()))
			return (false);
		if (!hreq.getServerName().equalsIgnoreCase(url.getHost()))
			return (false);
		int serverPort = hreq.getServerPort();
		if (serverPort == -1) {
			if ("https".equals(hreq.getScheme()))
				serverPort = 443;
			else
				serverPort = 80;
		}
		int urlPort = url.getPort();
		if (urlPort == -1) {
			if ("https".equals(url.getProtocol()))
				urlPort = 443;
			else
				urlPort = 80;
		}
		if (serverPort != urlPort)
			return (false);

		String contextPath = getContext().getContextPath();
		if (contextPath != null) {
			String file = url.getFile();
			if ((file == null) || !file.startsWith(contextPath))
				return (false);
			String tok = ";" + Globals.SESSION_PARAMETER_NAME + "=" + session.getId();
			if (file.indexOf(tok, contextPath.length()) >= 0)
				return (false);
		}

		// This URL belongs to our web application, so it is encodeable
		return (true);

	}

	/**
	 * Return the specified URL with the specified session identifier suitably
	 * encoded.
	 * 
	 * @param url
	 *            URL to be encoded with the session id
	 * @param sessionId
	 *            Session id to be included in the encoded URL
	 */
	protected String toEncoded(String url, String sessionId) {

		if ((url == null) || (sessionId == null))
			return (url);

		String path = url;
		String query = "";
		String anchor = "";
		int question = url.indexOf('?');
		if (question >= 0) {
			path = url.substring(0, question);
			query = url.substring(question);
		}
		int pound = path.indexOf('#');
		if (pound >= 0) {
			anchor = path.substring(pound);
			path = path.substring(0, pound);
		}
		StringBuffer sb = new StringBuffer(path);
		if (sb.length() > 0) { // jsessionid can't be first.
			sb.append(";");
			sb.append(Globals.SESSION_PARAMETER_NAME);
			sb.append("=");
			sb.append(sessionId);
		}
		sb.append(anchor);
		sb.append(query);
		return (sb.toString());

	}
}
