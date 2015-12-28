package com.falcon.server.start;

public interface ServerConstants {

	public static final String ARG_PORT = "port";

	public static final int DEF_PORT = 8080;

	public static final String ARG_SERVLETS = "servlets";

	public static final String ARG_REALMS = "realms";

	public static final String ARG_ALIASES = "aliases";

	public static final String ARG_BINDADDRESS = "bind-address";

	public static final String ARG_BACKLOG = "backlog";

	public static final String ARG_ERR = "error-stream";

	public static final String ARG_OUT = "out-stream";

	public static final String ARG_SESSION_TIMEOUT = "session-timeout";

	public static final String ARG_LOG_DIR = "log-dir";

	public static final String ARG_LOG_OPTIONS = "log-options";

	public static final String ARG_NOHUP = "nohup";

	public static final String ARG_COMPILE_OUT = "compile-out";

	public static final String ARG_WAR = "war-deployer";

	public static final String ARG_KEEPALIVE = "keep-alive";

	public static final String DEF_LOGENCODING = "falcon.serve.log.encoding";

	public static final String ARG_KEEPALIVE_TIMEOUT = "timeout-keep-alive";

	public static final String ARG_MAX_CONN_USE = "max-alive-conn-use";

	public static final String ARG_SESSION_PERSIST = "sssn-persistance";

	public static final String ARG_MAX_ACTIVE_SESSIONS = "max-active-sessions";

	public static final String ARG_ACCESS_LOG_FMT = "access-log-format";

	public static final String ARG_ACCEPTOR_CLASS = "acceptorImpl";

	public static final String ARG_WORK_DIRECTORY = "workdirectory";

	public static final String ARG_SESSION_SEED = "SessionSeed";

	public static final String ARG_THREAD_POOL_SIZE = "pool_max_size";

	public static final String SERVER_NAME = "Falcon-web-server";

	public static final String SERVER_VERSION = "Version 0.1";

	public static final int DEF_MIN_ACT_SESS = 10;

	public static final String UTF8 = "UTF-8"; // default encoding

	public static final int HTTP_MAX_HDR_LEN = 1024 * 1024 * 10;

	public static final String ELEM_DISPLAY_NAME = "display-name";
	public static final String ELEM_SERVLET = "servlet";
	public static final String ELEM_SERVLET_MAPPING = "servlet-mapping";
	public static final String ELEM_SERVLET_NAME = "servlet-name";
	public static final String ELEM_FILTER = "filter";
	public static final String ELEM_FILTER_MAPPING = "filter-mapping";
	public static final String ELEM_FILTER_NAME = "filter-name";
	public static final String ELEM_DISPATCHER = "dispatcher";
	public static final String ELEM_URL_PATTERN = "url-pattern";
	public static final String ELEM_WELCOME_FILES = "welcome-file-list";
	public static final String ELEM_WELCOME_FILE = "welcome-file";
	public static final String ELEM_SESSION_CONFIG = "session-config";
	public static final String ELEM_SESSION_TIMEOUT = "session-timeout";
	public static final String ELEM_MIME_MAPPING = "mime-mapping";
	public static final String ELEM_MIME_EXTENSION = "extension";
	public static final String ELEM_MIME_TYPE = "mime-type";
	public static final String ELEM_CONTEXT_PARAM = "context-param";
	public static final String ELEM_PARAM_NAME = "param-name";
	public static final String ELEM_PARAM_VALUE = "param-value";
	public static final String ELEM_LISTENER = "listener";
	public static final String ELEM_LISTENER_CLASS = "listener-class";
	public static final String ELEM_DISTRIBUTABLE = "distributable";
	public static final String ELEM_ERROR_PAGE = "error-page";
	public static final String ELEM_EXCEPTION_TYPE = "exception-type";
	public static final String ELEM_ERROR_CODE = "error-code";
	public static final String ELEM_ERROR_LOCATION = "location";
	public static final String ELEM_SECURITY_CONSTRAINT = "security-constraint";
	public static final String ELEM_LOGIN_CONFIG = "login-config";
	public static final String ELEM_SECURITY_ROLE = "security-role";
	public static final String ELEM_ROLE_NAME = "role-name";
	public static final String ELEM_ENV_ENTRY = "env-entry";
	public static final String ELEM_LOCALE_ENC_MAP_LIST = "locale-encoding-mapping-list";
	public static final String ELEM_LOCALE_ENC_MAPPING = "locale-encoding-mapping";
	public static final String ELEM_LOCALE = "locale";
	public static final String ELEM_ENCODING = "encoding";
	public static final String ELEM_JSP_CONFIG = "jsp-config";
	public static final String ELEM_JSP_PROPERTY_GROUP = "jsp-property-group";

	public final static String WWWFORMURLENCODE = "application/x-www-form-urlencoded";

	public final static String TRANSFERENCODING = "transfer-encoding".toLowerCase();

	public final static String KEEPALIVE = "Keep-Alive".toLowerCase();

	public final static String CONTENT_ENCODING = "Content-Encoding".toLowerCase();

	public final static String CONNECTION = "Connection".toLowerCase();

	public final static String PROTOCOL = "Protocol";

	public final static String METHOD = "Method";

	public final static String CHUNKED = "chunked";

	public final static String SETCOOKIE = "Set-Cookie".toLowerCase();

	public final static String HOST = "Host".toLowerCase();

	public final static String COOKIE = "Cookie".toLowerCase();

	public final static String ACCEPT_LANGUAGE = "Accept-Language".toLowerCase();

	public final static String SESSION_COOKIE_NAME = "JSESSIONID";

	public final static String SESSION_URL_NAME = ";jsessionid";

	public final static String CONTENTTYPE = "Content-Type".toLowerCase();

	public final static String CONTENTLENGTH = "Content-Length".toLowerCase();

	public static final String WEBAPP_DESCTIPTOR = "WEB-INF/web.xml";

	public static final String WAR_DEPLOY_IN_ROOT = "falcon.wardeploy.asroot";

	public static final String WEBAPPINITTIMEOUT = "falcon.webapp.%s.init.timeout";

	public static final String WEBAPPCLASSLOADER = "falcon.webapp.AppClassLoader";

	public static final String JSP_SERVLET_LOG_LEVEL = "WARNING";

	public static final String JSP_SERVLET_NAME = "JspServlet";
	public static final String JSP_SERVLET_MAPPING = "*.jsp";
	public static final String JSPX_SERVLET_MAPPING = "*.jspx";
	public static final String JSP_SERVLET_CLASS = "org.apache.jasper.servlet.JspServlet";

	public static final String DEFAULT_SERVLET_NAME = "default";
	public static final String DEFAULT_SERVLET_MAPPING = "/";
	public static final String DEFAULT_SERVLET_CLASS = "com.falcon.server.server.core.DefaultServlet";
	public static final String DEFAULT_CHARACTER_ENCODING = "ISO-8859-1";
	public static final String SERVERINFO = "FALCON WEB SERVER 0.1";
	
	public static final String WEBAPPDIR = "Falcon.webappdir";
	public static final String DEPLOYDIR = "Falcon.deploydir";

}
