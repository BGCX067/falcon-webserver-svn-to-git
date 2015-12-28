package com.falcon.server.server.servlet;

import static com.falcon.server.start.ServerConstants.ARG_ACCESS_LOG_FMT;
import static com.falcon.server.start.ServerConstants.CHUNKED;
import static com.falcon.server.start.ServerConstants.CONNECTION;
import static com.falcon.server.start.ServerConstants.CONTENTLENGTH;
import static com.falcon.server.start.ServerConstants.CONTENT_ENCODING;
import static com.falcon.server.start.ServerConstants.HOST;
import static com.falcon.server.start.ServerConstants.HTTP_MAX_HDR_LEN;
import static com.falcon.server.start.ServerConstants.KEEPALIVE;
import static com.falcon.server.start.ServerConstants.METHOD;
import static com.falcon.server.start.ServerConstants.PROTOCOL;
import static com.falcon.server.start.ServerConstants.SERVERINFO;
import static com.falcon.server.start.ServerConstants.SESSION_URL_NAME;
import static com.falcon.server.start.ServerConstants.TRANSFERENCODING;
import static com.falcon.server.start.ServerConstants.UTF8;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import com.falcon.server.http.util.ByteChunk;
import com.falcon.server.http.util.MessageBytes;
import com.falcon.server.http.util.MimeHeaders;
import com.falcon.server.server.Context;
import com.falcon.server.server.Pipeline;
import com.falcon.server.server.Valve;
import com.falcon.server.server.core.FalconContextValve;
import com.falcon.server.server.core.FalconPipeline;
import com.falcon.server.server.core.FalconServer;
import com.falcon.server.server.core.FalconWrapper;
import com.falcon.server.server.core.FalconWrapperValve;
import com.falcon.server.server.core.ServletDef;
import com.falcon.server.util.Utils;

public class FalconConnection implements Runnable {

	private Socket socket;

	FalconRequest request;

	public FalconRequest getRequest() {
		return request;
	}

	public FalconResponse getResponse() {
		return response;
	}

	FalconResponse response;

	private FalconServer server;

	public FalconServer getServer() {
		return server;
	}

	private FalconInputStream in;

	private FalconOutputStream out;

	private String reqMethod; // == null by default

	private String reqUriPath, reqUriPathUn;

	private String reqProtocol;

	private String remoteUser;

	private boolean oneOne; // HTTP/1.1 or better

	protected boolean keepAlive = true;

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	protected int timesRequested;

	protected long lastRun, lastWait;

	/**
	 * 获得最近一次运行的时间，该值应该大于LastRun
	 * 
	 * @return long
	 */
	public long getLastRun() {
		return lastRun;
	}

	public void setLastRun(long lastRun) {
		this.lastRun = lastRun;
	}

	/**
	 * 获得最近一次请求的时间
	 * 
	 * @return long
	 */
	public long getLastWait() {
		return lastWait;
	}

	public void setLastWait(long lastWait) {
		this.lastWait = lastWait;
	}

	private String sessionUrlValue, sessionValue;

	private boolean headersWritten;

	protected String reqQuery;

	private PrintWriter pw;

	private MessageFormat accessFmt;

	private Object[] logPlaceholders;

	/**
	 * The "enable DNS lookups" flag for this Connector.
	 */
	protected boolean enableLookups = false;

	// TODO consider creation an instance per thread in a pool, thread
	// memory can be used

	public boolean isEnableLookups() {
		return enableLookups;
	}

	public void setEnableLookups(boolean enableLookups) {
		this.enableLookups = enableLookups;
	}

	private final SimpleDateFormat expdatefmt = new SimpleDateFormat(
			"EEE, dd-MMM-yyyy HH:mm:ss 'GMT'", Locale.US); // used for
	// cookie

	private final SimpleDateFormat rfc850DateFmt = new SimpleDateFormat(
			"EEEEEE, dd-MMM-yy HH:mm:ss 'GMT'", Locale.US); // rfc850-date

	private final SimpleDateFormat headerdateformat = new SimpleDateFormat(
			"EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US); // rfc1123-date

	private final SimpleDateFormat asciiDateFmt = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy",
			Locale.US); // ASCII date, used in
	// headers

	private static final TimeZone tz = TimeZone.getTimeZone("GMT");

	private static List<Valve> valves = new ArrayList<Valve>();

	private Pipeline pipeline = new FalconPipeline();

	static {
		tz.setID("GMT");
		Valve v1 = new FalconContextValve();
		Valve v2 = new FalconWrapperValve();
		v1.setNext(v2);
		v2.setNext(null);
		valves.add(v1);
		valves.add(v2);
	}

	public FalconConnection(Socket socket, FalconServer server) {
		this.socket = socket;
		this.server = server;

		request = new FalconRequest();
		response = new FalconResponse();

		request.setResponse(response);
		response.setResponse(request);

		expdatefmt.setTimeZone(tz);
		headerdateformat.setTimeZone(tz);
		rfc850DateFmt.setTimeZone(tz);
		asciiDateFmt.setTimeZone(tz);
		if (server.isAccessLogged()) {
			// not format string must be not tull
			accessFmt = new MessageFormat((String) server.arguments.get(ARG_ACCESS_LOG_FMT));
			logPlaceholders = new Object[12];
		}
		pipeline.addValves(valves.toArray(new Valve[0]));
		server.getPool().execute(this);
	}

	/*private void initSSLAttrs() {
		if (socket.getClass().getName().indexOf("SSLSocket") > 0) {
			try {
				sslAttributes = new Hashtable();
				Object sslSession = socket.getClass().getMethod("getSession", Utils.EMPTY_CLASSES)
						.invoke(socket, Utils.EMPTY_OBJECTS);
				if (sslSession != null) {
					sslAttributes.put("javax.net.ssl.session", sslSession);
					Method m = sslSession.getClass().getMethod("getCipherSuite",
							Utils.EMPTY_CLASSES);
					m.setAccessible(true);
					sslAttributes.put("javax.net.ssl.cipher_suite", m.invoke(sslSession,
							Utils.EMPTY_OBJECTS));
					m = sslSession.getClass().getMethod("getPeerCertificates", Utils.EMPTY_CLASSES);
					m.setAccessible(true);
					sslAttributes.put("javax.net.ssl.peer_certificates", m.invoke(sslSession,
							Utils.EMPTY_OBJECTS));
				}
			} catch (IllegalAccessException iae) {
				sslAttributes = null;
				// iae.printStackTrace();
			} catch (NoSuchMethodException nsme) {
				sslAttributes = null;
				// nsme.printStackTrace();
			} catch (InvocationTargetException ite) {
				// note we do not clear attributes, because
				// SSLPeerUnverifiedException
				// happens in the last call, when no client sertificate
				// sslAttributes = null;
				// ite.printStackTrace();
			} catch (IllegalArgumentException iae) {
				// sslAttributes = null;
				// iae.printStackTrace();
			}
			// System.err.println("Socket SSL attrs: "+sslAttributes);
		}
	}*/

	/**
	 * it closes stream awaring of keep -alive
	 * 
	 * @throws IOException
	 */
	public void closeStreams() throws IOException {
		IOException ioe = null;
		try {
			if (response.isUsingWriter()) {
				pw = response.getInternelWriter();
				pw.flush();
			} else
				out.flush();
		} catch (IOException io1) {
			ioe = io1;
		}
		try {
			out.close();
		} catch (IOException io1) {
			if (ioe != null)
				ioe = (IOException) ioe.initCause(io1);
			else
				ioe = io1;
		}
		try {
			in.close();
		} catch (IOException io1) {
			if (ioe != null)
				ioe = (IOException) ioe.initCause(io1);
			else
				ioe = io1;
		}
		if (ioe != null)
			throw ioe;
	}

	private void restart() {
		reqMethod = null;
		reqUriPath = reqUriPathUn = null;
		reqProtocol = null;
		remoteUser = null;
		oneOne = false;
		sessionUrlValue = null;
		sessionValue = null;
		reqQuery = null;
		pw = null;
		headersWritten = false;
		((FalconInputStream) in).refresh();
		((FalconOutputStream) out).refresh();
		request.recycle();
	}

	// Methods from Runnable.
	public void run() {
		try {
			// initSSLAttrs();
			in = new FalconInputStream(socket.getInputStream(), this);
			out = new FalconOutputStream(socket.getOutputStream(), this);
			request.setInputStream(in);
			response.setOutputStream(out);

			out.setRequest(request);

			do {
				restart();
				parseRequest();
				if (reqMethod != null && server.isAccessLogged()) {
					// consider caching socket stuff for faster logging
					// {0} {1} {2} [{3,date,dd/MMM/yyyy:HH:mm:ss Z}] \"{4}
					// {5} {6}\" {7,number,#} {8,number} {9} {10}
					// ARG_ACCESS_LOG_FMT
					logPlaceholders[0] = socket.getInetAddress(); // IP
					logPlaceholders[1] = "-"; // the RFC 1413 identity of
					// the client
					logPlaceholders[2] = remoteUser == null ? "-" : remoteUser; // remote
					// user
					logPlaceholders[3] = new Date(lastRun); // time stamp
					// {3,date,dd/MMM/yyyy:HH:mm:ss
					// Z} {3,time,}
					logPlaceholders[4] = reqMethod; // method
					logPlaceholders[5] = reqUriPathUn; // resource
					logPlaceholders[6] = reqProtocol; // protocol
					logPlaceholders[7] = new Integer(request.getResCode());
					logPlaceholders[8] = new Long(((FalconOutputStream) out).lengthWritten());
					logPlaceholders[9] = new Integer(socket.getLocalPort());
					logPlaceholders[10] = server.isShowReferer() ? request.getHeader("Referer")
							: "-";
					logPlaceholders[11] = server.isShowUserAgent() ? request
							.getHeader("User-Agent") : "-";
					server.getLogStream().println(accessFmt.format(logPlaceholders));
				}
				lastRun = 0;
				timesRequested++;
			} while (keepAlive && server.isKeepAlive()
					&& timesRequested < server.getMaxTimesConnectionUse());
		} catch (IOException ioe) {
			String errMsg = ioe.getMessage();
			if ((errMsg == null || errMsg.indexOf("ocket closed") < 0)
					&& ioe instanceof java.nio.channels.AsynchronousCloseException == false)
				server.log("IO error: " + ioe + " in processing a request from "
						+ socket.getInetAddress() + ":" + socket.getLocalPort() + " / "
						+ socket.getClass().getName(), ioe);
			else
				synchronized (this) {
					server.log("Exception considered as socket closed:" + ioe, ioe);
					socket = null;
				}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			synchronized (this) {
				if (socket != null)
					try {
						socket.close();
					} catch (IOException e) { /* ignore */
					}
				socket = null;
			}
		}
	}

	private void parseRequest() throws Exception {

		byte[] lineBytes = new byte[4096];
		int len;
		String line;
		// / TODO put time mark here for start waiting for receiving
		// requests
		lastWait = System.currentTimeMillis();
		// Read the first line of the request.
		len = in.readLine(lineBytes, 0, lineBytes.length);
		if (len == -1 || len == 0) {
			if (keepAlive) {
				keepAlive = false;
				// connection seems be closed

			} else {
				problem("Status-Code 400: Bad Request(empty)", HttpServletResponse.SC_BAD_REQUEST);
			}
			return;
		}
		if (len >= lineBytes.length) {
			problem("Status-Code 414: Request-URI Too Long",
					HttpServletResponse.SC_REQUEST_URI_TOO_LONG);
			return;
		}
		// //lastRun = 0; // to avoid closing socket in long process
		line = new String(lineBytes, 0, len, UTF8);
		StringTokenizer ust = new StringTokenizer(line);
		if (ust.hasMoreTokens()) {
			reqMethod = ust.nextToken();
			if (ust.hasMoreTokens()) {
				reqUriPathUn = ust.nextToken();
				// TODO make it only when URL overwrite enambled
				int uop = reqUriPathUn.indexOf(SESSION_URL_NAME);
				if (uop > 0) {
					sessionUrlValue = reqUriPathUn.substring(uop + SESSION_URL_NAME.length());
					reqUriPathUn = reqUriPathUn.substring(0, uop);
					try {
						request.setRequestedSessionId(sessionUrlValue);
					} catch (NullPointerException npe) {
						sessionUrlValue = null;
					} catch (IllegalStateException ise) {
						sessionUrlValue = null;
					}
				}
				if (ust.hasMoreTokens()) {
					reqProtocol = ust.nextToken();
					oneOne = !reqProtocol.toUpperCase().equals("HTTP/1.0");
					// Read the rest of the lines.
					String s;
					while ((s = ((FalconInputStream) in).readLine(HTTP_MAX_HDR_LEN)) != null) {
						if (s.length() == 0)
							break;
						int c = s.indexOf(':', 0);
						if (c > 0) {
							String key = s.substring(0, c).trim().toLowerCase();
							String value = s.substring(c + 1).trim();
							request.addHeader(key, value);
							if (CONNECTION.equalsIgnoreCase(key))
								if (oneOne)
									keepAlive = "close".equalsIgnoreCase(value) == false;
								else
									keepAlive = KEEPALIVE.equalsIgnoreCase(value);
							else if (KEEPALIVE.equalsIgnoreCase(key)) {

							}
						} else
							server.log("header field '" + s + "' without ':'");
					}
				} else {
					reqProtocol = "HTTP/0.9";
					oneOne = false;
				}
			}
		}

		if (reqProtocol == null) {
			problem("Status-Code 400: Malformed request line:" + line,
					HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		request.addHeader(PROTOCOL, reqProtocol);
		request.addHeader(METHOD, reqMethod);

		// Check Host: header in HTTP/1.1 requests.
		if (oneOne) {
			String host = request.getHeader(HOST);
			if (host == null) {
				problem("'Host' header missing in HTTP/1.1 request",
						HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
		}

		// Split off query string, if any.
		int qmark = reqUriPathUn.indexOf('?');
		if (qmark > -1) {
			if (qmark < reqUriPathUn.length() - 1)
				reqQuery = reqUriPathUn.substring(qmark + 1);
			reqUriPathUn = reqUriPathUn.substring(0, qmark);
		}
		request.setQueryString(reqQuery);
		reqUriPath = Utils.decode(reqUriPathUn, UTF8);

		// TDOD check if reqUriPathUn starts with http://host:port
		int port = socket.getLocalPort();
		request.setPort(port);

		int remotePort = socket.getPort();
		request.setRemotePort(remotePort);

		InetAddress localAddress = socket.getLocalAddress();
		request.setServerName(localAddress.getHostName());

		String scheme = socket.getClass().getName().indexOf("SSLSocket") > 0 ? "https" : "http";
		request.setScheme(scheme);

		MessageBytes tt = MessageBytes.newInstance();
		tt.setBytes(reqUriPath.getBytes(), 0, reqUriPath.length());
		request.setRequestURI(reqUriPath);

		ByteChunk uriBC = tt.getByteChunk();
		if (uriBC.startsWithIgnoreCase("http", 0)) {

			int pos = uriBC.indexOf("://", 0, 3, 4);
			int uriBCStart = uriBC.getStart();
			int slashPos = -1;
			if (pos != -1) {
				byte[] uriB = uriBC.getBytes();
				slashPos = uriBC.indexOf('/', pos + 3);
				if (slashPos == -1) {
					slashPos = uriBC.getLength();
					// Set URI as "/"
					tt.setBytes(uriB, uriBCStart + pos + 1, 1);
				} else {
					tt.setBytes(uriB, uriBCStart + slashPos, uriBC.getLength() - slashPos);
				}
				request.setRequestURI(tt.toString());
			}
		}
		if (CHUNKED.equals(request.getHeader(TRANSFERENCODING))) {
			request.setHeader(CONTENTLENGTH, null);
			((FalconInputStream) in).chunking(true);
		}
		String contentEncoding = request.getContentEncoding();
		// TODO: encoding in request can be invalid, then do default
		request.setCharacterEncoding(contentEncoding != null ? contentEncoding : UTF8);
		String contentLength = request.getHeader(CONTENTLENGTH);
		if (contentLength != null)
			try {
				((FalconInputStream) in).setContentLength(Long.parseLong(contentLength));
			} catch (NumberFormatException nfe) {
				server.log("Invalid value of input content-length: " + contentLength);
			}
		String encoding = request.getHeader(CONTENT_ENCODING);
		if (encoding != null) {
			if ((encoding.equalsIgnoreCase("gzip") || encoding.equalsIgnoreCase("compressed"))
					&& null != server.getGzipInStreamConstr()
					&& ((FalconInputStream) in).compressed(true)) {
			} else {
				problem("Status-Code 415: Unsupported media type:" + encoding,
						HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				return;
			}
		}
		if (keepAlive && server.isKeepAlive() && socket.getKeepAlive() == false)
			socket.setKeepAlive(true);

		InetAddress inetAddr = socket.getLocalAddress();
		if (inetAddr != null) {
			request.setLocalName(inetAddr.getHostName());
		}

		String localAddr = socket.getLocalAddress().getHostAddress();
		request.setLocalAddr(localAddr);

		inetAddr = socket.getInetAddress();
		String remoteAddr = null;
		if (inetAddr != null) {
			remoteAddr = inetAddr.getHostAddress();
			request.setRemoteAddr(remoteAddr);
		}

		inetAddr = socket.getInetAddress();
		// 下面注释之间的代码出现性能问题:原因是getHostName会查询DNS解析主机名，通过增加enableLookups
		// 变量修复该问题
		String remoteHost = null;
		if (isEnableLookups() && inetAddr != null) {
			remoteHost = inetAddr.getHostName();
		} else {
			if (remoteAddr != null) {
				remoteHost = remoteAddr;
			}
		}
		request.setRemoteHost(remoteHost);
		// 上面的代码出现性能问题
		lastRun = System.currentTimeMillis();
		MessageBytes pathBytes = MessageBytes.newInstance();
		pathBytes.setString(reqUriPath);

		FalconWrapper.mapper.mapContext(pathBytes, request.getMappingData());

		request.setContext((Context) request.getMappingData().context);
		request.setServletDef((ServletDef) request.getMappingData().wrapper);
		runService(request, response);
	}

	private void runService(FalconRequest request, FalconResponse response) throws IOException {

		response.setStatus(HttpServletResponse.SC_OK);
		try {
			request.parseCookies();
			pipeline.getFirst().invoke(request, response);
		} catch (UnavailableException e) { 
			problem(e.getMessage(), HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		} catch (ServletException e) {
			server.log("Servlet exception", e);
			Throwable rootCause = e.getRootCause();
			while (rootCause != null) {
				server.log("Caused by", rootCause);
				if (rootCause instanceof ServletException)
					rootCause = ((ServletException) rootCause).getRootCause();
				else
					rootCause = rootCause.getCause(); /* 1.4 */
			}
			problem(e.toString(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} catch (IOException ioe) {
			throw ioe;
		} catch (Exception e) {
			server.log("Unexpected problem running servlet", e);
			problem("Unexpected problem running servlet: " + e.toString(),
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} finally {
			closeStreams();
			// socket will be closed by a caller if no keep-alive
		}
	}

	private void problem(String logMessage, int resCode) {
		server.log(logMessage);
		try {
			response.sendError(resCode, logMessage);
		} catch (IllegalStateException e) {
		} catch (IOException e) {
		}
	}

	public Socket getSocket() {
		return socket;
	}

	void writeHeaders() throws IOException {
		synchronized (this) {
			if (headersWritten)
				return;

			headersWritten = true;
		}
		boolean chunked_out = false;
		boolean wasContentLen = false;
		String firstLine = null;
		if (response.resMessage.length() < 256)
			firstLine = reqProtocol + " " + response.resCode + " " + response.resMessage;
		else
			firstLine = reqProtocol + " " + response.resCode + " "
					+ response.resMessage.substring(0, 255);
		out.println(firstLine);
		out.println("Server:" + SERVERINFO);
		out.println("Data:" + headerdateformat.format(new Date()));
		out.println("Content-Type:" + response.getContentType());
		out.println("Content-Length:" + response.getContentLength());

		MimeHeaders headers = response.getHeaders();
		int size = headers.size();
		for (int i = 0; i < size; i++) {
			String headerName = headers.getName(i).getString();
			String headerValue = headers.getValue(i).getString();
			if (chunked_out == false)
				if (TRANSFERENCODING.equals(headerName) && CHUNKED.equals(headerValue))
					chunked_out = true;
		}

		if (wasContentLen == false && chunked_out == false && isKeepAlive()) {
			out.println(TRANSFERENCODING + ": " + CHUNKED);
			chunked_out = true;
		}
		out.println();
		out.flush();
		((FalconOutputStream) out).setChunked(chunked_out);
	}
}