package com.falcon.server.server.core;

import java.io.File;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.apache.log4j.Logger;

import com.falcon.server.http.util.CharChunk;
import com.falcon.server.http.util.MessageBytes;
import com.falcon.server.util.Utils;

/**
 * Mapper, which implements the servlet API mapping rules (which are derived
 * from the HTTP rules).
 * 
 */
public final class Mapper {

	public Logger logger = Logger.getLogger(getClass());
	// ----------------------------------------------------- Instance Variables

	protected Context context = new Context();

	protected ContextList contextList = new ContextList();

	public Mapper() {
	}

	/**
	 * Set context, used for wrapper mapping (request dispatcher).
	 * 
	 * @param welcomeResources
	 *            Welcome files defined for this context
	 * @param resources
	 *            Static resources of the context
	 */
	public void setContext(String path) {
		context.name = path;
	}

	/**
	 * Add a new Context to an existing Host.
	 * 
	 * @param path
	 *            Context path
	 * @param context
	 *            Context object
	 */
	public void addContext(String path, Object context, String[] welcomeResources) {

		Context[] contexts = contextList.contexts;
		int slashCount = slashCount(path);
		// Update nesting
		if (slashCount > contextList.nesting) {
			contextList.nesting = slashCount;
		}
		Context[] newContexts = new Context[contexts.length + 1];
		Context newContext = new Context();
		newContext.name = path;
		newContext.object = context;
		newContext.welcomeResources = welcomeResources;
		if (insertMap(contexts, newContexts, newContext)) {
			contextList.contexts = newContexts;
		}
	}

	/**
	 * Add a new Wrapper to an existing Context.
	 * 
	 * @param contextPath
	 *            Context path this wrapper belongs to
	 * @param path
	 *            Wrapper mapping
	 * @param wrapper
	 *            Wrapper object
	 */
	public void addServlet(String contextPath, String path, Object servlet) {
		addServlet(contextPath, path, servlet, false);
	}

	public void addServlet(String contextPath, String path, Object servlet, boolean jspWildCard) {

		Context[] contexts = contextList.contexts;
		int pos2 = find(contexts, contextPath);
		if (pos2 < 0) {
			logger.error("No context found: " + contextPath);
			return;
		}
		Context context = contexts[pos2];
		if (context.name.equals(contextPath)) {
			addServlet(context, path, servlet, jspWildCard);
		}
	}

	protected void addServlet(String path, Object servlet) {
		addServlet(context, path, servlet, false);
	}

	/**
	 * Adds a wrapper to the given context.
	 * 
	 * @param context
	 *            The context to which to add the wrapper
	 * @param path
	 *            Wrapper mapping
	 * @param wrapper
	 *            The Wrapper object
	 * @param jspWildCard
	 *            true if the servlet corresponds to the JspServlet and the
	 *            mapping path contains a wildcard; false otherwise
	 */
	protected void addServlet(Context context, String path, Object servlet, boolean jspWildCard) {

		synchronized (context) {
			ServletWrapper newWrapper = new ServletWrapper();
			newWrapper.object = servlet;
			newWrapper.jspWildCard = jspWildCard;
			if (path.endsWith("/*")) {
				// Wildcard wrapper
				newWrapper.name = path.substring(0, path.length() - 2);
				ServletWrapper[] oldWrappers = context.wildcardWrappers;
				ServletWrapper[] newWrappers = new ServletWrapper[oldWrappers.length + 1];
				if (insertMap(oldWrappers, newWrappers, newWrapper)) {
					context.wildcardWrappers = newWrappers;
					int slashCount = slashCount(newWrapper.name);
					if (slashCount > context.nesting) {
						context.nesting = slashCount;
					}
				}
			} else if (path.startsWith("*.")) {
				// Extension wrapper
				newWrapper.name = path.substring(2);
				ServletWrapper[] oldWrappers = context.extensionWrappers;
				ServletWrapper[] newWrappers = new ServletWrapper[oldWrappers.length + 1];
				if (insertMap(oldWrappers, newWrappers, newWrapper)) {
					context.extensionWrappers = newWrappers;
				}
			} else if (path.equals("/")) {
				// Default wrapper
				newWrapper.name = "";
				context.defaultWrapper = newWrapper;
			} else {
				// Exact wrapper
				newWrapper.name = path;
				ServletWrapper[] oldWrappers = context.exactWrappers;
				ServletWrapper[] newWrappers = new ServletWrapper[oldWrappers.length + 1];
				if (insertMap(oldWrappers, newWrappers, newWrapper)) {
					context.exactWrappers = newWrappers;
				}
			}
		}
	}

	/**
	 * Map the specified URI relative to the context, mutating the given mapping
	 * data.
	 * 
	 * @param uri
	 *            URI
	 * @param mappingData
	 *            This structure will contain the result of the mapping
	 *            operation
	 */
	public void map(MessageBytes uri, MappingData mappingData) throws Exception {

		uri.toChars();
		CharChunk uricc = uri.getCharChunk();
		uricc.setLimit(-1);
		internalMapWrapper(context, uricc, mappingData);

	}

	/**
	 * Map the specified host name and URI, mutating the given mapping data.
	 * 
	 * @param uri
	 *            URI
	 * @param mappingData
	 *            This structure will contain the result of the mapping
	 *            operation
	 */
	public void mapContext(MessageBytes uri, MappingData mappingData) throws Exception {

		uri.toChars();
		internalMap(uri.getCharChunk(), mappingData);
	}

	/**
	 * Map the specified URI.
	 */
	private final void internalMap(CharChunk uri, MappingData mappingData) throws Exception {

		uri.setLimit(-1);

		Context[] contexts = contextList.contexts;
		Context context = null;
		int nesting = 0;

		// Context mapping
		if (mappingData.context == null) {
			int pos = find(contexts, uri);
			if (pos == -1) {
				return;
			}

			int lastSlash = -1;
			int uriEnd = uri.getEnd();
			int length = -1;
			boolean found = false;
			while (pos >= 0) {
				if (uri.startsWith(contexts[pos].name)) {
					length = contexts[pos].name.length();
					if (uri.getLength() == length) {
						found = true;
						break;
					} else if (uri.startsWithIgnoreCase("/", length)) {
						found = true;
						break;
					}
				}
				if (lastSlash == -1) {
					lastSlash = nthSlash(uri, nesting + 1);
				} else {
					lastSlash = lastSlash(uri);
				}
				uri.setEnd(lastSlash);
				pos = find(contexts, uri);
			}
			uri.setEnd(uriEnd);

			if (!found) {
				if (contexts[0].name.equals("")) {
					context = contexts[0];
				}
			} else {
				context = contexts[pos];
			}
			if (context != null) {
				mappingData.context = context.object;
				mappingData.contextPath.setString(context.name);
			}
		}

		// Wrapper mapping
		if ((context != null) && (mappingData.wrapper == null)) {
			internalMapWrapper(context, uri, mappingData);
		}
	}

	/**
	 * Wrapper mapping.
	 */
	private final void internalMapWrapper(Context context, CharChunk path, MappingData mappingData)
			throws Exception {

		int pathOffset = path.getOffset();
		int pathEnd = path.getEnd();
		int servletPath = pathOffset;
		boolean noServletPath = false;

		int length = context.name.length();
		if (length == (pathEnd - pathOffset)) {
			path.append('/');
			pathEnd++;
		}
		if (length != (pathEnd - pathOffset)) {
			servletPath = pathOffset + length;
		} else {
			noServletPath = true;
			path.append('/');
			pathOffset = path.getOffset();
			pathEnd = path.getEnd();
			servletPath = pathOffset + length;
		}

		path.setOffset(servletPath);

		// Rule 1 -- Exact Match
		ServletWrapper[] exactWrappers = context.exactWrappers;
		internalMapExactWrapper(exactWrappers, path, mappingData);

		// Rule 2 -- Prefix Match
		boolean checkJspWelcomeFiles = false;
		ServletWrapper[] wildcardWrappers = context.wildcardWrappers;
		if (mappingData.wrapper == null) {
			internalMapWildcardWrapper(wildcardWrappers, context.nesting, path, mappingData);
			if (mappingData.wrapper != null && mappingData.jspWildCard) {
				char[] buf = path.getBuffer();
				if (buf[pathEnd - 1] == '/') {
					/*
					 * Path ending in '/' was mapped to JSP servlet based on
					 * wildcard match (e.g., as specified in url-pattern of a
					 * jsp-property-group.
					 * Force the context's welcome files, which are interpreted
					 * as JSP files (since they match the url-pattern), to be
					 * considered. See Bugzilla 27664.
					 */
					mappingData.wrapper = null;
					checkJspWelcomeFiles = true;
				} else {
					// See Bugzilla 27704
					mappingData.wrapperPath.setChars(buf, path.getStart(), path.getLength());
					mappingData.pathInfo.recycle();
				}
			}
		}

		if (mappingData.wrapper == null && noServletPath) {
			// The path is empty, redirect to "/"
			mappingData.redirectPath.setChars(path.getBuffer(), pathOffset, pathEnd);
			path.setEnd(pathEnd - 1);
			return;
		}

		// Rule 3 -- Extension Match
		ServletWrapper[] extensionWrappers = context.extensionWrappers;
		if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
			internalMapExtensionWrapper(extensionWrappers, path, mappingData);
		}

		// Rule 4 -- Welcome resources processing for servlets
		if (mappingData.wrapper == null) {
			boolean checkWelcomeFiles = checkJspWelcomeFiles;
			if (!checkWelcomeFiles) {
				char[] buf = path.getBuffer();
				checkWelcomeFiles = (buf[pathEnd - 1] == '/');
			}
			if (checkWelcomeFiles) {
				for (int i = 0; (i < context.welcomeResources.length)
						&& (mappingData.wrapper == null); i++) {
					path.setOffset(pathOffset);
					path.setEnd(pathEnd);
					path.append(context.welcomeResources[i], 0, context.welcomeResources[i]
							.length());
					path.setOffset(servletPath);

					// Rule 4a -- Welcome resources processing for exact macth
					internalMapExactWrapper(exactWrappers, path, mappingData);

					// Rule 4b -- Welcome resources processing for prefix match
					if (mappingData.wrapper == null) {
						internalMapWildcardWrapper(wildcardWrappers, context.nesting, path,
								mappingData);
					}

					// Rule 4c -- Welcome resources processing
					// for physical folder
					if (mappingData.wrapper == null) {
						Object file = null;
						String pathStr = path.toString();
						file = Utils.lookup(context.name + File.separator + pathStr);
						if (file != null) {
							internalMapExtensionWrapper(extensionWrappers, path, mappingData);
							if (mappingData.wrapper == null && context.defaultWrapper != null) {
								mappingData.wrapper = context.defaultWrapper.object;
								mappingData.requestPath.setChars(path.getBuffer(), path.getStart(),
										path.getLength());
								mappingData.wrapperPath.setChars(path.getBuffer(), path.getStart(),
										path.getLength());
								mappingData.requestPath.setString(pathStr);
								mappingData.wrapperPath.setString(pathStr);
							}
						}
					}

				}

				path.setOffset(servletPath);
				path.setEnd(pathEnd);
			}

		}

		// Rule 7 -- Default servlet
		if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
			if (context.defaultWrapper != null) {
				mappingData.wrapper = context.defaultWrapper.object;
				mappingData.requestPath.setChars(path.getBuffer(), path.getStart(), path
						.getLength());
				mappingData.wrapperPath.setChars(path.getBuffer(), path.getStart(), path
						.getLength());
			}
			// Redirection to a folder
			char[] buf = path.getBuffer();
			if (buf[pathEnd - 1] != '/') {
				Object file = null;
				String pathStr = path.toString();
				file = Utils.lookup(pathStr);
				if (file != null) {
					// Note: this mutates the path: do not do any processing
					// after this (since we set the redirectPath, there
					// shouldn't be any)
					path.setOffset(pathOffset);
					path.append('/');
					mappingData.redirectPath.setChars(path.getBuffer(), path.getStart(), path
							.getLength());
				} else {
					mappingData.requestPath.setString(pathStr);
					mappingData.wrapperPath.setString(pathStr);
				}
			}
		}

		path.setOffset(pathOffset);
		path.setEnd(pathEnd);

	}

	/**
	 * Exact mapping.
	 */
	private final void internalMapExactWrapper(ServletWrapper[] wrappers, CharChunk path,
			MappingData mappingData) {
		int pos = find(wrappers, path);
		if ((pos != -1) && (path.equals(wrappers[pos].name))) {
			mappingData.requestPath.setString(wrappers[pos].name);
			mappingData.wrapperPath.setString(wrappers[pos].name);
			mappingData.wrapper = wrappers[pos].object;
		}
	}

	/**
	 * Wildcard mapping.
	 */
	private final void internalMapWildcardWrapper(ServletWrapper[] wrappers, int nesting,
			CharChunk path, MappingData mappingData) {

		int pathEnd = path.getEnd();

		int lastSlash = -1;
		int length = -1;
		int pos = find(wrappers, path);
		if (pos != -1) {
			boolean found = false;
			while (pos >= 0) {
				if (path.startsWith(wrappers[pos].name)) {
					length = wrappers[pos].name.length();
					if (path.getLength() == length) {
						found = true;
						break;
					} else if (path.startsWithIgnoreCase("/", length)) {
						found = true;
						break;
					}
				}
				if (lastSlash == -1) {
					lastSlash = nthSlash(path, nesting + 1);
				} else {
					lastSlash = lastSlash(path);
				}
				path.setEnd(lastSlash);
				pos = find(wrappers, path);
			}
			path.setEnd(pathEnd);
			if (found) {
				mappingData.wrapperPath.setString(wrappers[pos].name);
				if (path.getLength() > length) {
					mappingData.pathInfo.setChars(path.getBuffer(), path.getOffset() + length, path
							.getLength()
							- length);
				}
				mappingData.requestPath.setChars(path.getBuffer(), path.getOffset(), path
						.getLength());
				mappingData.wrapper = wrappers[pos].object;
				mappingData.jspWildCard = wrappers[pos].jspWildCard;
			}
		}
	}

	/**
	 * Extension mappings.
	 */
	private final void internalMapExtensionWrapper(ServletWrapper[] wrappers, CharChunk path,
			MappingData mappingData) {
		char[] buf = path.getBuffer();
		int pathEnd = path.getEnd();
		int servletPath = path.getOffset();
		int slash = -1;
		for (int i = pathEnd - 1; i >= servletPath; i--) {
			if (buf[i] == '/') {
				slash = i;
				break;
			}
		}
		if (slash >= 0) {
			int period = -1;
			for (int i = pathEnd - 1; i > slash; i--) {
				if (buf[i] == '.') {
					period = i;
					break;
				}
			}
			if (period >= 0) {
				path.setOffset(period + 1);
				path.setEnd(pathEnd);
				int pos = find(wrappers, path);
				if ((pos != -1) && (path.equals(wrappers[pos].name))) {
					mappingData.wrapperPath.setChars(buf, servletPath, pathEnd - servletPath);
					mappingData.requestPath.setChars(buf, servletPath, pathEnd - servletPath);
					mappingData.wrapper = wrappers[pos].object;
				}
				path.setOffset(servletPath);
				path.setEnd(pathEnd);
			}
		}
	}

	/**
	 * Find a map elemnt given its name in a sorted array of map elements. This
	 * will return the index for the closest inferior or equal item in the given
	 * array.
	 */
	private static final int find(MapElement[] map, CharChunk name) {
		return find(map, name, name.getStart(), name.getEnd());
	}

	/**
	 * Find a map elemnt given its name in a sorted array of map elements. This
	 * will return the index for the closest inferior or equal item in the given
	 * array.
	 */
	private static final int find(MapElement[] map, CharChunk name, int start, int end) {

		int a = 0;
		int b = map.length - 1;

		// Special cases: -1 and 0
		if (b == -1) {
			return -1;
		}

		if (compare(name, start, end, map[0].name) < 0) {
			return -1;
		}
		if (b == 0) {
			return 0;
		}

		int i = 0;
		while (true) {
			i = (b + a) / 2;
			int result = compare(name, start, end, map[i].name);
			if (result == 1) {
				a = i;
			} else if (result == 0) {
				return i;
			} else {
				b = i;
			}
			if ((b - a) == 1) {
				int result2 = compare(name, start, end, map[b].name);
				if (result2 < 0) {
					return a;
				} else {
					return b;
				}
			}
		}

	}

	/**
	 * Find a map elemnt given its name in a sorted array of map elements. This
	 * will return the index for the closest inferior or equal item in the given
	 * array.
	 */
	private static final int find(MapElement[] map, String name) {

		int a = 0;
		int b = map.length - 1;

		// Special cases: -1 and 0
		if (b == -1) {
			return -1;
		}

		if (name.compareTo(map[0].name) < 0) {
			return -1;
		}
		if (b == 0) {
			return 0;
		}

		int i = 0;
		while (true) {
			i = (b + a) / 2;
			int result = name.compareTo(map[i].name);
			if (result > 0) {
				a = i;
			} else if (result == 0) {
				return i;
			} else {
				b = i;
			}
			if ((b - a) == 1) {
				int result2 = name.compareTo(map[b].name);
				if (result2 < 0) {
					return a;
				} else {
					return b;
				}
			}
		}

	}

	/**
	 * Compare given char chunk with String. Return -1, 0 or +1 if inferior,
	 * equal, or superior to the String.
	 */
	private static final int compare(CharChunk name, int start, int end, String compareTo) {
		int result = 0;
		char[] c = name.getBuffer();
		int len = compareTo.length();
		if ((end - start) < len) {
			len = end - start;
		}
		for (int i = 0; (i < len) && (result == 0); i++) {
			if (c[i + start] > compareTo.charAt(i)) {
				result = 1;
			} else if (c[i + start] < compareTo.charAt(i)) {
				result = -1;
			}
		}
		if (result == 0) {
			if (compareTo.length() > (end - start)) {
				result = -1;
			} else if (compareTo.length() < (end - start)) {
				result = 1;
			}
		}
		return result;
	}

	/**
	 * Find the position of the last slash in the given char chunk.
	 */
	private static final int lastSlash(CharChunk name) {

		char[] c = name.getBuffer();
		int end = name.getEnd();
		int start = name.getStart();
		int pos = end;

		while (pos > start) {
			if (c[--pos] == '/') {
				break;
			}
		}

		return (pos);

	}

	/**
	 * Find the position of the nth slash, in the given char chunk.
	 */
	private static final int nthSlash(CharChunk name, int n) {

		char[] c = name.getBuffer();
		int end = name.getEnd();
		int start = name.getStart();
		int pos = start;
		int count = 0;

		while (pos < end) {
			if ((c[pos++] == '/') && ((++count) == n)) {
				pos--;
				break;
			}
		}

		return (pos);

	}

	/**
	 * Return the slash count in a given string.
	 */
	private static final int slashCount(String name) {
		int pos = -1;
		int count = 0;
		while ((pos = name.indexOf('/', pos + 1)) != -1) {
			count++;
		}
		return count;
	}

	/**
	 * Insert into the right place in a sorted MapElement array, and prevent
	 * duplicates.
	 */
	private static final boolean insertMap(MapElement[] oldMap, MapElement[] newMap,
			MapElement newElement) {
		int pos = find(oldMap, newElement.name);
		if ((pos != -1) && (newElement.name.equals(oldMap[pos].name))) {
			return false;
		}
		System.arraycopy(oldMap, 0, newMap, 0, pos + 1);
		newMap[pos + 1] = newElement;
		System.arraycopy(oldMap, pos + 1, newMap, pos + 2, oldMap.length - pos - 1);
		return true;
	}

	/**
	 * Insert into the right place in a sorted MapElement array.
	 */
	private static final boolean removeMap(MapElement[] oldMap, MapElement[] newMap, String name) {
		int pos = find(oldMap, name);
		if ((pos != -1) && (name.equals(oldMap[pos].name))) {
			System.arraycopy(oldMap, 0, newMap, 0, pos);
			System.arraycopy(oldMap, pos + 1, newMap, pos, oldMap.length - pos - 1);
			return true;
		}
		return false;
	}

	// ------------------------------------------------- MapElement Inner Class

	protected static abstract class MapElement {

		public String name = null;
		public Object object = null;

	}

	// ------------------------------------------------ ContextList Inner Class

	protected static final class ContextList {

		public Context[] contexts = new Context[0];
		public int nesting = 0;

	}

	// ---------------------------------------------------- Context Inner Class

	protected static final class Context extends MapElement {

		public String path = null;
		public String[] welcomeResources = new String[0];
		public ServletWrapper defaultWrapper = null;
		public ServletWrapper[] exactWrappers = new ServletWrapper[0];
		public ServletWrapper[] wildcardWrappers = new ServletWrapper[0];
		public ServletWrapper[] extensionWrappers = new ServletWrapper[0];
		public int nesting = 0;

	}

	// ---------------------------------------------------- Wrapper Inner Class

	protected static class ServletWrapper extends MapElement {

		public String path = null;
		public boolean jspWildCard = false;
	}

	// -------------------------------------------------------- Testing Methods

	// FIXME: Externalize this

	/*public static void main(String args[]) {

	    try {

	    Mapper mapper = new Mapper(null);
	    System.out.println("Start");

	    mapper.addHost("sjbjdvwsbvhrb", new String[0], "blah1");
	    mapper.addHost("sjbjdvwsbvhr/", new String[0], "blah1");
	    mapper.addHost("wekhfewuifweuibf", new String[0], "blah2");
	    mapper.addHost("ylwrehirkuewh", new String[0], "blah3");
	    mapper.addHost("iohgeoihro", new String[0], "blah4");
	    mapper.addHost("fwehoihoihwfeo", new String[0], "blah5");
	    mapper.addHost("owefojiwefoi", new String[0], "blah6");
	    mapper.addHost("iowejoiejfoiew", new String[0], "blah7");
	    mapper.addHost("iowejoiejfoiew", new String[0], "blah17");
	    mapper.addHost("ohewoihfewoih", new String[0], "blah8");
	    mapper.addHost("fewohfoweoih", new String[0], "blah9");
	    mapper.addHost("ttthtiuhwoih", new String[0], "blah10");
	    mapper.addHost("lkwefjwojweffewoih", new String[0], "blah11");
	    mapper.addHost("zzzuyopjvewpovewjhfewoih", new String[0], "blah12");
	    mapper.addHost("xxxxgqwiwoih", new String[0], "blah13");
	    mapper.addHost("qwigqwiwoih", new String[0], "blah14");

	    System.out.println("Map:");
	    for (int i = 0; i < mapper.hosts.length; i++) {
	        System.out.println(mapper.hosts[i].name);
	    }

	    mapper.setDefaultHostName("ylwrehirkuewh");

	    String[] welcomes = new String[2];
	    welcomes[0] = "boo/baba";
	    welcomes[1] = "bobou";

	    mapper.addContext("iowejoiejfoiew", "", "context0", new String[0], null);
	    mapper.addContext("iowejoiejfoiew", "/foo", "context1", new String[0], null);
	    mapper.addContext("iowejoiejfoiew", "/foo/bar", "context2", welcomes, null);
	    mapper.addContext("iowejoiejfoiew", "/foo/bar/bla", "context3", new String[0], null);

	    mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/fo/*", "wrapper0");
	    mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/", "wrapper1");
	    mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/blh", "wrapper2");
	    mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "*.jsp", "wrapper3");
	    mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/blah/bou/*", "wrapper4");
	    mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "/blah/bobou/*", "wrapper5");
	    mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "*.htm", "wrapper6");

	    MappingData mappingData = new MappingData();
	    MessageBytes host = MessageBytes.newInstance();
	    host.setString("iowejoiejfoiew");
	    MessageBytes uri = MessageBytes.newInstance();
	    uri.setString("/foo/bar/blah/bobou/foo");
	    uri.toChars();
	    uri.getCharChunk().setLimit(-1);

	    mapper.map(host, uri, mappingData);
	    System.out.println("MD Host:" + mappingData.host);
	    System.out.println("MD Context:" + mappingData.context);
	    System.out.println("MD Wrapper:" + mappingData.wrapper);

	    System.out.println("contextPath:" + mappingData.contextPath);
	    System.out.println("wrapperPath:" + mappingData.wrapperPath);
	    System.out.println("pathInfo:" + mappingData.pathInfo);
	    System.out.println("redirectPath:" + mappingData.redirectPath);

	    mappingData.recycle();
	    mapper.map(host, uri, mappingData);
	    System.out.println("MD Host:" + mappingData.host);
	    System.out.println("MD Context:" + mappingData.context);
	    System.out.println("MD Wrapper:" + mappingData.wrapper);

	    System.out.println("contextPath:" + mappingData.contextPath);
	    System.out.println("wrapperPath:" + mappingData.wrapperPath);
	    System.out.println("pathInfo:" + mappingData.pathInfo);
	    System.out.println("redirectPath:" + mappingData.redirectPath);

	    for (int i = 0; i < 1000000; i++) {
	        mappingData.recycle();
	        mapper.map(host, uri, mappingData);
	    }

	    long time = System.currentTimeMillis();
	    for (int i = 0; i < 1000000; i++) {
	        mappingData.recycle();
	        mapper.map(host, uri, mappingData);
	    }
	    System.out.println("Elapsed:" + (System.currentTimeMillis() - time));

	    System.out.println("MD Host:" + mappingData.host);
	    System.out.println("MD Context:" + mappingData.context);
	    System.out.println("MD Wrapper:" + mappingData.wrapper);

	    System.out.println("contextPath:" + mappingData.contextPath);
	    System.out.println("wrapperPath:" + mappingData.wrapperPath);
	    System.out.println("requestPath:" + mappingData.requestPath);
	    System.out.println("pathInfo:" + mappingData.pathInfo);
	    System.out.println("redirectPath:" + mappingData.redirectPath);

	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	}
	*/
}
