package com.falcon.server.server.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.falcon.server.server.Connector;
import com.falcon.server.server.FalconBase;
import com.falcon.server.server.WarDeployer;
import com.falcon.server.server.servlet.FalconConnection;
import com.falcon.server.start.Logger;
import com.falcon.server.start.ServerConstants;
import com.falcon.server.util.Utils;

public class FalconServer extends FalconBase implements Serializable, ServerConstants {

	private static final long serialVersionUID = 1L;

	protected static final int DEF_SESSION_TIMEOUT = 30; // in minutes

	protected static final int DEF_MIN_ACT_SESS = 10;

	protected static final int DESTROY_TIME_SEC = 15;

	protected static final int HTTP_MAX_HDR_LEN = 1024 * 1024 * 10;

	public static final int DEF_PORT = 8080;

	public static final String BGCOLOR = "BGCOLOR=\"#D1E9FE\"";

	private String defaultDeployerFactory = "com.falcon.server.server.core.FalconWarDeploy";

	/**
	 * max number of alive connections default value
	 */
	protected static final int DEF_MAX_CONN_USE = 100;

	public static final String UTF8 = "UTF-8"; // default encoding

	protected String hostName;

	private transient PrintStream logStream;

	private boolean useAccLog;

	/**
	 * whether keep alive (server uses keep-alive by default)
	 */
	private boolean keepAlive;

	/**
	 * keep alive timeout interval in seconds, default is 30
	 */
	private int timeoutKeepAlive;

	/**
	 * max number of a connection use in keep-alive default is 100
	 */
	private int maxAliveConnUse;

	private boolean showUserAgent;

	private boolean showReferer;

	/**
	 * keep alive correlative parameter
	 */
	protected String keepAliveHdrParams;

	protected transient KeepAliveCleaner keepAliveCleaner;

	protected transient Constructor<?> gzipInStreamConstr;

	private Map<String, WebAppConfiguration> webapps;

	// for sessions
	private byte[] uniqer = new byte[20]; // TODO consider configurable strength

	/**
	 * 生成session id的加密随机数
	 */
	private SecureRandom srandom;

	/**
	 * provides session timeout in minutes and can be negative. Negative value
	 * won't start session cleaning thread and will use a persistent session
	 * cookie if not sent default is 30 minutes
	 */
	protected int expiredIn;

	public Map<String, Object> arguments;

	private ExecutorService pool = null;

	private Logger log;

	public Logger getLog() {
		return log;
	}

	// / Constructor.
	public FalconServer(Map<String, Object> arguments, PrintStream logStream) {
		this.arguments = arguments;
		this.logStream = logStream;
		log = new Logger(logStream);

		pool = Executors.newFixedThreadPool(Integer.valueOf((String) arguments
				.get(ARG_THREAD_POOL_SIZE)));

		setAccessLogged();
		keepAlive = arguments.get(ARG_KEEPALIVE) == null
				|| ((Boolean) arguments.get(ARG_KEEPALIVE)).booleanValue();

		int timeoutKeepAliveSec;
		try {
			timeoutKeepAliveSec = Integer.parseInt((String) arguments.get(ARG_KEEPALIVE_TIMEOUT));
		} catch (Exception ex) {
			timeoutKeepAliveSec = 30;
		}
		timeoutKeepAlive = timeoutKeepAliveSec * 1000;
		try {
			maxAliveConnUse = Integer.parseInt((String) arguments.get(ARG_MAX_CONN_USE));
		} catch (Exception ex) {
			maxAliveConnUse = DEF_MAX_CONN_USE;
		}
		keepAliveHdrParams = "timeout=" + timeoutKeepAliveSec + ", max=" + maxAliveConnUse;

		expiredIn = arguments.get(ARG_SESSION_TIMEOUT) != null ? ((Integer) arguments
				.get(ARG_SESSION_TIMEOUT)).intValue() : DEF_SESSION_TIMEOUT;
		srandom = new SecureRandom((arguments.get(ARG_SESSION_SEED) == null ? "FALCON" + new Date()
				: (String) arguments.get(ARG_SESSION_SEED)).getBytes());
		try {
			gzipInStreamConstr = Class.forName("java.util.zip.GZIPInputStream").getConstructor(
					new Class[] { InputStream.class });
		} catch (ClassNotFoundException cne) {

		} catch (NoSuchMethodException nsm) {

		}
	}

	public Map<String, Object> getArguments() {
		return arguments;
	}

	public Constructor<?> getGzipInStreamConstr() {
		return gzipInStreamConstr;
	}

	public ExecutorService getPool() {
		return pool;
	}

	public FalconServer() {
		this(new HashMap<String, Object>(), System.err);
	}

	protected void setAccessLogged() {
		String logflags = (String) arguments.get(ARG_LOG_OPTIONS);
		if (logflags != null) {
			useAccLog = true;
			showUserAgent = logflags.indexOf('A') >= 0;
			showReferer = logflags.indexOf('R') >= 0;
		}
	}

	public boolean isAccessLogged() {
		return useAccLog;
	}

	public boolean isShowReferer() {
		return showReferer;
	}

	public boolean isShowUserAgent() {
		return showUserAgent;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public int getKeepAliveDuration() {
		return timeoutKeepAlive;
	}

	public String getKeepAliveParamStr() {
		return keepAliveHdrParams;
	}

	public int getMaxTimesConnectionUse() {
		return maxAliveConnUse;
	}

	public void addWebApp(String path, WebAppConfiguration webapp) {
		if (webapps == null)
			webapps = new HashMap<String, WebAppConfiguration>();
		webapps.put(path, webapp);
	}

	public WebAppConfiguration unloadWebApp(WebAppConfiguration webapp) {
		WebAppConfiguration result = null;
		synchronized (webapps) {
			result = (WebAppConfiguration) webapps.remove(webapp.getContextPath());
		}
		return result;
	}

	/**
	 * 删除一个servlet，此处是不是需要删除servlet对应的session
	 * 
	 * @param urlPat
	 */
	public synchronized void unloadWebApp(String urlPat) {
		webapps.remove(urlPat);
	}

	public void addWarDeployer(String deployerFactory) {
		if (deployerFactory != null)
			defaultDeployerFactory = deployerFactory;
		try {
			WarDeployer wd = (WarDeployer) Class.forName(defaultDeployerFactory).newInstance();
			wd.deploy(this);
		} catch (ClassNotFoundException cnf) {
			log("Problem initializing war deployer: " + cnf);
		} catch (Exception e) {
			log("Problem war(s) deployment", e);
		}
	}

	/**
	 * session持久化
	 * 
	 * @return
	 */
	protected File getPersistentFile() {
		if (arguments.get(ARG_SESSION_PERSIST) == null
				|| (Boolean) arguments.get(ARG_SESSION_PERSIST) == Boolean.FALSE)
			return null;
		String workPath = (String) arguments.get(ARG_WORK_DIRECTORY);
		if (workPath == null)
			workPath = ".";
		return new File(workPath, hostName
				+ '-'
				+ (arguments.get(ARG_PORT) == null ? String.valueOf(DEF_PORT) : arguments
						.get(ARG_PORT)) + "-session.obj");
	}

	// Run the server. Returns only on errors.
	transient boolean running = true;

	protected transient Connector connector;

	protected transient Thread ssclThread;

	/**
	 * Launches the server It doesn't exist until server runs, so start it in a
	 * dedicated thread.
	 * 
	 * @return 0 if the server successfully terminated, 1 if it can't be started
	 *         and -1 if it was terminated during some errors
	 */
	public int serve() {
		try {
			connector = createConnector();
		} catch (IOException e) {
			log("connector: " + e);
			return 1;
		}

		if (isKeepAlive()) {
//			keepAliveCleaner = new KeepAliveCleaner(this);
//			keepAliveCleaner.start();
		}
		File fsessions = getPersistentFile();
		if (fsessions != null && fsessions.exists()) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(fsessions));
				// sessions = FalconHttpSessionContextImpl.restore(br,
				// Math.abs(expiredIn) * 60, this);
			} catch (IOException ioe) {
				log("Problem in restoring sessions.", ioe);
			} catch (Exception e) {
				log("Unexpected problem in restoring sessions.", e);
			} finally {
				if (br != null)
					try {
						br.close();
					} catch (IOException ioe) {
					}
			}
		}
		System.out.println("[" + new Date() + "] falcon httpd " + hostName + " - " + connector
				+ " is listening.");
		try {
			while (running) {
				try {
					Socket socket = connector.accept();
					if (keepAliveCleaner != null) // we need to add regardless
						// of keep alive
						keepAliveCleaner.addConnection(new FalconConnection(socket, this));
					else
						new FalconConnection(socket, this); // TODO consider
				} catch (IOException e) {
					log("Accept: " + e);
				} catch (SecurityException se) {
					log("Illegal access: " + se);
				} catch (IllegalStateException is) {
					log("Illegal state: " + is);
				}
			}
		} catch (Throwable t) {
			log("Unhandled exception: " + t + ", server is terminating.", t);
			if (t instanceof ThreadDeath)
				throw (Error) t;
			return -1;
		} finally {
			try {
				if (connector != null)
					connector.destroy();
			} catch (IOException e) {
			}
		}
		return 0;
	}

	/**
	 * Tells the server to stop
	 * 
	 * @throws IOException
	 */
	public void notifyStop() throws IOException {
		running = false;
		connector.destroy();
		connector = null;
		if (ssclThread != null)
			ssclThread.interrupt();
	}

	protected Connector createConnector() throws IOException {
		String acceptorClass = (String) arguments.get(ARG_ACCEPTOR_CLASS);
		if (acceptorClass == null)
			acceptorClass = "com.falcon.server.server.core.HttpIoConnector";
		// assured defaulting here
		try {
			connector = (Connector) Class.forName(acceptorClass).newInstance();
		} catch (InstantiationException e) {
			log("Couldn't instantiate connector, the Server is inoperable", e);
		} catch (IllegalAccessException e) {
			Constructor<?> c;
			try {
				c = Class.forName(acceptorClass).getDeclaredConstructor(Utils.EMPTY_CLASSES);
				c.setAccessible(true);
				connector = (Connector) c.newInstance(Utils.EMPTY_OBJECTS);
			} catch (Exception e1) {
				log(
						"connector is not accessable or can't be instantiated, the Server is inoperable",
						e);
			}
		} catch (ClassNotFoundException e) {
			log("connector class not found, the Server is inoperable", e);
		}
		Map<String, String> acceptorProperties = new HashMap<String, String>();
		connector.init(arguments, acceptorProperties);
		hostName = (String) acceptorProperties.get(ARG_BINDADDRESS);
		return connector;
	}

	public WebAppConfiguration getWebApp(String name) {
		try {
			return (WebAppConfiguration) webapps.get(name);
		} catch (NullPointerException npe) {
			return null;
		}
	}

	public void destroyAllWebapp() {
		for (WebAppConfiguration app : webapps.values()) {
			app.destroy();
		}
	}

	public Collection<WebAppConfiguration> getWebApps() {
		return webapps.values();
	}

	public Collection<String> getWebAppsName() {
		return webapps.keySet();
	}

	/**
	 * 添加日志信息
	 */
	public void log(String message) {
		log.log(message);
	}

	public void log(String message, Throwable throwable) {
		log.log(message, throwable);
	}

	public void log(Exception exception, String message) {
		log.log(message, exception);
	}

	public String getMimeType(String file) {
		// TODO make MIME table expendable from an external file
		file = file.toUpperCase();
		// it could be faster to extract extension and then linear search in a
		// string of extension
		// and use found index as a key to type
		if (file.endsWith(".HTML") || file.endsWith(".HTM"))
			return "text/html";
		if (file.endsWith(".TXT"))
			return "text/plain";
		if (file.endsWith(".XML"))
			return "text/xml";
		if (file.endsWith(".CSS"))
			return "text/css";
		if (file.endsWith(".SGML") || file.endsWith(".SGM"))
			return "text/x-sgml";
		// Image
		if (file.endsWith(".GIF"))
			return "image/gif";
		if (file.endsWith(".JPG") || file.endsWith(".JPEG") || file.endsWith(".JPE"))
			return "image/jpeg";
		if (file.endsWith(".PNG"))
			return "image/png";
		if (file.endsWith(".BMP"))
			return "image/bmp";
		if (file.endsWith(".TIF") || file.endsWith(".TIFF"))
			return "image/tiff";
		if (file.endsWith(".RGB"))
			return "image/x-rgb";
		if (file.endsWith(".XPM"))
			return "image/x-xpixmap";
		if (file.endsWith(".XBM"))
			return "image/x-xbitmap";
		if (file.endsWith(".SVG"))
			return "image/svg-xml ";
		if (file.endsWith(".SVGZ"))
			return "image/svg-xml ";
		// Audio
		if (file.endsWith(".AU") || file.endsWith(".SND"))
			return "audio/basic";
		if (file.endsWith(".MID") || file.endsWith(".MIDI") || file.endsWith(".RMI")
				|| file.endsWith(".KAR"))
			return "audio/mid";
		if (file.endsWith(".MPGA") || file.endsWith(".MP2") || file.endsWith(".MP3"))
			return "audio/mpeg";
		if (file.endsWith(".WAV"))
			return "audio/wav";
		if (file.endsWith(".AIFF") || file.endsWith(".AIFC"))
			return "audio/aiff";
		if (file.endsWith(".AIF"))
			return "audio/x-aiff";
		if (file.endsWith(".RA"))
			return "audio/x-realaudio";
		if (file.endsWith(".RPM"))
			return "audio/x-pn-realaudio-plugin";
		if (file.endsWith(".RAM"))
			return "audio/x-pn-realaudio";
		if (file.endsWith(".SD2"))
			return "audio/x-sd2";
		// Applications
		if (file.endsWith(".BIN") || file.endsWith(".DMS") || file.endsWith(".LHA")
				|| file.endsWith(".LZH") || file.endsWith(".EXE") || file.endsWith(".DLL")
				|| file.endsWith(".CLASS"))
			return "application/octet-stream";
		if (file.endsWith(".HQX"))
			return "application/mac-binhex40";
		if (file.endsWith(".PS") || file.endsWith(".AI") || file.endsWith(".EPS"))
			return "application/postscript";
		if (file.endsWith(".PDF"))
			return "application/pdf";
		if (file.endsWith(".RTF"))
			return "application/rtf";
		if (file.endsWith(".DOC"))
			return "application/msword";
		if (file.endsWith(".PPT"))
			return "application/powerpoint";
		if (file.endsWith(".FIF"))
			return "application/fractals";
		if (file.endsWith(".P7C"))
			return "application/pkcs7-mime";
		// Application/x
		if (file.endsWith(".JS"))
			return "application/x-javascript";
		if (file.endsWith(".Z"))
			return "application/x-compress";
		if (file.endsWith(".GZ"))
			return "application/x-gzip";
		if (file.endsWith(".TAR"))
			return "application/x-tar";
		if (file.endsWith(".TGZ"))
			return "application/x-compressed";
		if (file.endsWith(".ZIP"))
			return "application/x-zip-compressed";
		if (file.endsWith(".DIR") || file.endsWith(".DCR") || file.endsWith(".DXR"))
			return "application/x-director";
		if (file.endsWith(".DVI"))
			return "application/x-dvi";
		if (file.endsWith(".TEX"))
			return "application/x-tex";
		if (file.endsWith(".LATEX"))
			return "application/x-latex";
		if (file.endsWith(".TCL"))
			return "application/x-tcl";
		if (file.endsWith(".CER") || file.endsWith(".CRT") || file.endsWith(".DER"))
			return "application/x-x509-ca-cert";
		if (file.endsWith(".ISO"))
			return "application/x-iso9660-image";
		// Video
		if (file.endsWith(".MPG") || file.endsWith(".MPE") || file.endsWith(".MPEG"))
			return "video/mpeg";
		if (file.endsWith(".QT") || file.endsWith(".MOV"))
			return "video/quicktime";
		if (file.endsWith(".AVI"))
			return "video/x-msvideo";
		if (file.endsWith(".MOVIE"))
			return "video/x-sgi-movie";
		// Chemical
		if (file.endsWith(".PDB") || file.endsWith(".XYZ"))
			return "chemical/x-pdb";
		// X-
		if (file.endsWith(".ICE"))
			return "x-conference/x-cooltalk";
		if (file.endsWith(".JNLP"))
			return "application/x-java-jnlp-file";
		if (file.endsWith(".WRL") || file.endsWith(".VRML"))
			return "x-world/x-vrml";
		if (file.endsWith(".WML"))
			return "text/vnd.wap.wml";
		if (file.endsWith(".WMLC"))
			return "application/vnd.wap.wmlc";
		if (file.endsWith(".WMLS"))
			return "text/vnd.wap.wmlscript";
		if (file.endsWith(".WMLSC"))
			return "application/vnd.wap.wmlscriptc";
		if (file.endsWith(".WBMP"))
			return "image/vnd.wap.wbmp";

		return null;
	}

	public String getServerInfo() {
		return SERVER_NAME + " " + SERVER_NAME;
	}

	public PrintStream getLogStream() {
		return logStream;
	}

	/**
	 * Returns the name of this web application correponding to this
	 * ServletContext as specified in the deployment descriptor for this web
	 * application by the display-name element.
	 * 
	 * @return The name of the web application or null if no name has been
	 *         declared in the deployment descriptor.
	 * 
	 * @since Servlet 2.3
	 */
	public java.lang.String getServletContextName() {
		return null;
	}

	synchronized String generateSessionId() {
		srandom.nextBytes(uniqer);
		// TODO swap randomly bytes
		return Utils.base64Encode(uniqer);
	}


}