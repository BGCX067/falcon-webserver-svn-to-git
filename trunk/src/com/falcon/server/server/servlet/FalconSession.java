package com.falcon.server.server.servlet;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import com.falcon.server.server.Context;
import com.falcon.server.server.Manager;
import com.falcon.server.server.Session;
import com.falcon.server.util.Enumerator;

/**
 * Http session support
 * 
 * TODO: provide lazy session restoring, it should allow to load classes from
 * wars 1st step it read serialization data and store under session attribute
 * 2nd when the session requested, it tries to deserialize all session
 * attributes considered that all classes available
 */
@SuppressWarnings( { "deprecation", "serial" })
public class FalconSession implements HttpSession, Session, Serializable {

	/**
	 * The collection of user data attributes associated with this Session.
	 */
	protected Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

	/**
	 * The time this session was created, in milliseconds since midnight,
	 * January 1, 1970 GMT.
	 */
	protected long creationTime = 0L;

	private String id;

	/**
	 * 以秒为单位的session活动间隔
	 */
	private int maxInactiveInterval = 30 * 60; // in seconds

	/**
	 * 当前访问session的时间
	 */
	protected long thisAccessedTime = 0L;

	private boolean expired;

	protected boolean isValid = false;

	private transient ServletContext servletContext;

	protected static HttpSessionContext sessionContext = null;

	protected Manager manager;

	protected transient FalconSessionFacade facade = null;

	/**
	 * Flag indicating whether this session is new or not.
	 */
	protected boolean isNew = false;
	/**
	 * 最近访问时间
	 */
	private long lastAccessedTime;

	/**
	 * Return the last time the client sent a request associated with this
	 * session, as the number of milliseconds since midnight, January 1, 1970
	 * GMT. Actions that your application takes, such as getting or setting a
	 * value associated with the session, do not affect the access time.
	 */
	public long getLastAccessedTime() {

		if (!isValidInternal()) {
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".getLastAccessedTime"));
		}

		return (this.lastAccessedTime);
	}

	public Manager getManager() {
		return manager;
	}

	public void setManager(Manager manager) {
		this.manager = manager;
	}

	public FalconSession(Manager manager) {

		this.manager = manager;
	}

	public FalconSession(String id, int inactiveInterval) {

		creationTime = System.currentTimeMillis();
		this.id = id;
		this.maxInactiveInterval = inactiveInterval;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		if ((this.id != null) && (manager != null))
			manager.remove(this);

		this.id = id;

		if (manager != null)
			manager.add(this);
		tellNew();
	}

	/**
	 * Inform the listeners about the new session.
	 * 
	 */
	public void tellNew() {

		// Notify interested application event listeners
		Context context = manager.getContext();
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners != null) {
			HttpSessionEvent event = new HttpSessionEvent(getSession());
			for (int i = 0; i < listeners.length; i++) {
				if (!(listeners[i] instanceof HttpSessionListener))
					continue;
				HttpSessionListener listener = (HttpSessionListener) listeners[i];
				try {
					listener.sessionCreated(event);
				} catch (Throwable t) {
				}
			}
		}

	}

	public int getMaxInactiveInterval() {
		return maxInactiveInterval;
	}

	public void setMaxInactiveInterval(int interval) {
		this.maxInactiveInterval = interval;
		if (isValid && interval == 0) {
			expire(true);
		}
	}

	public HttpSessionContext getSessionContext() {

		if (sessionContext == null)
			sessionContext = new StandardSessionContext();
		return (sessionContext);

	}

	public ServletContext getServletContext() {
		return servletContext;
	}

	public Object getAttribute(String name) throws IllegalStateException {

		if (!isValidInternal()) {
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".getAttribute()"));
		}
		if (name == null)
			return null;

		return (attributes.get(name));
	}

	/**
	 * @deprecated As of Version 2.2, this method is replaced by
	 *             <code>getAttribute()</code>
	 */
	public Object getValue(String name) throws IllegalStateException {
		return getAttribute(name);
	}

	@SuppressWarnings("unchecked")
	public Enumeration getAttributeNames() throws IllegalStateException {
		if (!isValidInternal()) {
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".getAttributeNames()"));
		}
		return (new Enumerator<String>(attributes.keySet(), true));
	}

	/**
	 * 
	 * @deprecated As of Version 2.2, this method is replaced by
	 *             <code>getAttributeNames()</code>
	 */
	public String[] getValueNames() throws IllegalStateException {

		if (!isValidInternal())
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".getValueNames()"));

		return (keys());
	}

	public void setAttribute(String name, Object value) throws IllegalStateException {
		if (name == null)
			throw new IllegalArgumentException("attribute name is null");

		if (value == null) {
			removeAttribute(name);
			return;
		}
		if (!isValidInternal())
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".setAttribute()"));

		HttpSessionBindingEvent event = null;

		// Call the valueBound() method if necessary
		if (value instanceof HttpSessionBindingListener) {
			// Don't call any notification if replacing with the same value
			Object oldValue = attributes.get(name);
			if (value != oldValue) {
				event = new HttpSessionBindingEvent(getSession(), name, value);
				try {
					((HttpSessionBindingListener) value).valueBound(event);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}

		Object unbound = attributes.put(name, value);

		if ((unbound != null) && (unbound != value)
				&& (unbound instanceof HttpSessionBindingListener)) {
			try {
				((HttpSessionBindingListener) unbound).valueUnbound(new HttpSessionBindingEvent(
						getSession(), name));
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		// Notify interested application event listeners
		Context context = manager.getContext();
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners == null)
			return;
		for (int i = 0; i < listeners.length; i++) {
			if (!(listeners[i] instanceof HttpSessionAttributeListener))
				continue;
			HttpSessionAttributeListener listener = (HttpSessionAttributeListener) listeners[i];
			try {
				if (unbound != null) {
					if (event == null) {
						event = new HttpSessionBindingEvent(getSession(), name, unbound);
					}
					listener.attributeReplaced(event);
				} else {
					if (event == null) {
						event = new HttpSessionBindingEvent(getSession(), name, value);
					}
					listener.attributeAdded(event);
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

	}

	/**
	 * @deprecated As of Version 2.2, this method is replaced by
	 *             <code>setAttribute()</code>
	 */
	public void putValue(String name, Object value) throws IllegalStateException {
		setAttribute(name, value);
	}

	public void removeAttribute(java.lang.String name) throws IllegalStateException {
		if (!isValidInternal())
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".removeAttribute()"));

		if (name == null)
			return;

		// Remove this attribute from our collection
		Object value = attributes.remove(name);

		// Do we need to do valueUnbound() and attributeRemoved() notification?
		if (value == null) {
			return;
		}

		// Call the valueUnbound() method if necessary
		HttpSessionBindingEvent event = null;
		if (value instanceof HttpSessionBindingListener) {
			event = new HttpSessionBindingEvent(getSession(), name, value);
			((HttpSessionBindingListener) value).valueUnbound(event);
		}

		// Notify interested application event listeners
		Context context = manager.getContext();
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners == null)
			return;
		for (int i = 0; i < listeners.length; i++) {
			if (!(listeners[i] instanceof HttpSessionAttributeListener))
				continue;
			HttpSessionAttributeListener listener = (HttpSessionAttributeListener) listeners[i];
			try {
				if (event == null) {
					event = new HttpSessionBindingEvent(getSession(), name, value);
				}
				listener.attributeRemoved(event);
			} catch (Throwable t) {
				t.printStackTrace();
			}

		}
	}

	/**
	 * @deprecated As of Version 2.2, this method is replaced by
	 *             <code>removeAttribute()</code>
	 */
	public void removeValue(java.lang.String name) throws IllegalStateException {
		removeAttribute(name);
	}

	/**
	 * Invalidates this session and unbinds any objects bound to it.
	 * 
	 * @exception IllegalStateException
	 *                if this method is called on an invalidated session
	 */
	public void invalidate() {

		if (!isValidInternal())
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".invalidate()"));

		expire(true);

	}

	public boolean isNew() {

		if (!isValidInternal())
			throw new IllegalStateException(String.format("%s session is invalid", getClass()
					.getName()
					+ ".isNew()"));

		return (this.isNew);

	}

	/**
	 * Set the <code>isNew</code> flag for this session.
	 * 
	 * @param isNew
	 *            The new value for the <code>isNew</code> flag
	 */
	public void setNew(boolean isNew) {

		this.isNew = isNew;

	}

	/**
	 * something hack, to update servlet context since session created out of
	 * scope
	 * 
	 * @param sc
	 */
	public synchronized void setServletContext(ServletContext sc) {

		servletContext = sc;
	}

	public boolean isValid() {

		if (!expired) {
			return true;
		}

		if (!this.isValid) {
			return false;
		}
		if (maxInactiveInterval >= 0) {
			long timeNow = System.currentTimeMillis();
			int timeIdle = (int) ((timeNow - thisAccessedTime) / 1000L);
			if (timeIdle >= maxInactiveInterval) {
				expire(true);
			}
		}
		return isValid;
	}

	public void setValid(boolean isValid) {
		this.isValid = isValid;
	}

	/**
	 * Set the creation time for this session. This method is called by the
	 * Manager when an existing Session instance is reused.
	 * 
	 * @param time
	 *            The new creation time
	 */
	public void setCreationTime(long time) {

		this.creationTime = time;
		this.lastAccessedTime = time;
		this.thisAccessedTime = time;

	}

	/**
	 * Perform the internal processing required to invalidate this session,
	 * without triggering an exception if the session has already expired.
	 * 
	 * @param notify
	 *            Should we notify listeners about the demise of this session?
	 */
	public void expire(boolean notify) {

		// Mark this session as "being expired" if needed
		if (expired)
			return;

		synchronized (this) {

			if (manager == null)
				return;

			expired = true;

			// Notify interested application event listeners
			// FIXME - Assumes we call listeners in reverse order
			Context context = manager.getContext();
			Object listeners[] = context.getApplicationEventListeners();
			if (notify && (listeners != null)) {
				HttpSessionEvent event = new HttpSessionEvent(getSession());
				for (int i = 0; i < listeners.length; i++) {
					int j = (listeners.length - 1) - i;
					if (!(listeners[j] instanceof HttpSessionListener))
						continue;
					HttpSessionListener listener = (HttpSessionListener) listeners[j];
					try {
						listener.sessionDestroyed(event);
					} catch (Throwable t) {
						try {
						} catch (Exception e) {
							;
						}
					}
				}
			}
			setValid(false);

			// Remove this session from our manager's active sessions
			manager.remove(this);

			// We have completed expire of this session
			expired = false;

			// Unbind any objects associated with this session
			String keys[] = keys();
			for (int i = 0; i < keys.length; i++)
				removeAttributeInternal(keys[i], notify);

		}
	}

	protected String[] keys() {

		return ((String[]) attributes.keySet().toArray(new String[0]));

	}

	public HttpSession getSession() {

		if (facade == null) {
			facade = new FalconSessionFacade(this);
		}
		return (facade);

	}

	/**
	 * Remove the object bound with the specified name from this session. If the
	 * session does not have an object bound with this name, this method does
	 * nothing.
	 * <p>
	 * After this method executes, and if the object implements
	 * <code>HttpSessionBindingListener</code>, the container calls
	 * <code>valueUnbound()</code> on the object.
	 * 
	 * @param name
	 *            Name of the object to remove from this session.
	 * @param notify
	 *            Should we notify interested listeners that this attribute is
	 *            being removed?
	 */
	protected void removeAttributeInternal(String name, boolean notify) {

		// Avoid NPE
		if (name == null)
			return;

		// Remove this attribute from our collection
		Object value = attributes.remove(name);

		// Do we need to do valueUnbound() and attributeRemoved() notification?
		if (!notify || (value == null)) {
			return;
		}

		// Call the valueUnbound() method if necessary
		HttpSessionBindingEvent event = null;
		if (value instanceof HttpSessionBindingListener) {
			event = new HttpSessionBindingEvent(getSession(), name, value);
			((HttpSessionBindingListener) value).valueUnbound(event);
		}

		// Notify interested application event listeners
		Context context = manager.getContext();
		Object listeners[] = context.getApplicationEventListeners();
		if (listeners == null)
			return;
		for (int i = 0; i < listeners.length; i++) {
			if (!(listeners[i] instanceof HttpSessionAttributeListener))
				continue;
			HttpSessionAttributeListener listener = (HttpSessionAttributeListener) listeners[i];
			try {
				if (event == null) {
					event = new HttpSessionBindingEvent(getSession(), name, value);
				}
				listener.attributeRemoved(event);
			} catch (Throwable t) {

			}
		}

	}

/*	// storing session in format
	// id:latency:contextname:tttt
	// entry:base64 ser data
	// entry:base64 ser data
	// $$
	void save(Writer w) throws IOException {
		if (expired)
			return;
		// can't use append because old JDK
		w.write(id);
		w.write(':');
		w.write(Integer.toString(maxInactiveInterval));
		w.write(':');
		w.write(servletContext == null || servletContext.getServletContextName() == null ? ""
				: servletContext.getServletContextName());
		w.write(':');
		w.write(Long.toString(lastAccessTime));
		w.write("\r\n");
		Enumeration e = getAttributeNames();
		ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 16);
		while (e.hasMoreElements()) {
			String aname = (String) e.nextElement();
			Object so = get(aname);
			if (so instanceof Serializable) {
				os.reset();
				ObjectOutputStream oos = new ObjectOutputStream(os);
				try {
					oos.writeObject(so);
					w.write(aname);
					w.write(":");
					w.write(Utils.base64Encode(os.toByteArray()));
					w.write("\r\n");
				} catch (IOException ioe) {
					servletContext.log("Problem storing a session value of " + aname, ioe);
				}
			} else
				servletContext.log("Non serializable object " + so.getClass().getName()
						+ " skiped in storing of " + aname, null);
			if (so instanceof HttpSessionActivationListener)
				((HttpSessionActivationListener) so)
						.sessionWillPassivate(new HttpSessionEvent(this));
		}
		w.write("$$\r\n");
	}

	static CopyOfFalconSession restore(BufferedReader r, int inactiveInterval,
			ServletContext servletContext) throws IOException {
		String s = r.readLine();
		if (s == null) // eos
			return null;
		int cp = s.indexOf(':');
		if (cp < 0)
			throw new IOException("Invalid format for a session header, no session id: " + s);
		String id = s.substring(0, cp);
		int cp2 = s.indexOf(':', cp + 1);
		if (cp2 < 0)
			throw new IOException("Invalid format for a session header, no latency: " + s);
		try {
			inactiveInterval = Integer.parseInt(s.substring(cp + 1, cp2));
		} catch (NumberFormatException nfe) {
			servletContext
					.log("Session latency is invalid:" + s.substring(cp + 1, cp2) + " " + nfe);
		}
		cp = s.indexOf(':', cp2 + 1);
		if (cp < 0)
			throw new IOException("Invalid format for a session header, context name: " + s);
		String contextName = s.substring(cp2 + 1, cp);
		// consider servletContext.getContext("/"+contextName)
		CopyOfFalconSession result = new CopyOfFalconSession(id, inactiveInterval);
		try {
			result.lastAccessTime = Long.parseLong(s.substring(cp + 1));
		} catch (NumberFormatException nfe) {
			servletContext.log("Last access time is invalid:" + s.substring(cp + 1) + " " + nfe);
		}
		do {
			s = r.readLine();
			if (s == null)
				throw new IOException("Unexpected end of a stream.");
			if ("$$".equals(s))
				return result;
			cp = s.indexOf(':');
			if (cp < 0)
				throw new IOException("Invalid format for a session entry: " + s);
			String aname = s.substring(0, cp);
			// if (lazyRestore)
			// result.put(aname, s.substring(cp+1));
			ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(Utils.decode64(s
					.substring(cp + 1))));
			Throwable restoreError;
			try {
				Object so;
				result.put(aname, so = ois.readObject());
				restoreError = null;
				if (so instanceof HttpSessionActivationListener)
					((HttpSessionActivationListener) so).sessionDidActivate(new HttpSessionEvent(
							result));

			} catch (ClassNotFoundException cnfe) {
				restoreError = cnfe;

			} catch (NoClassDefFoundError ncdfe) {
				restoreError = ncdfe;
			} catch (IOException ioe) {
				restoreError = ioe;
			}
			if (restoreError != null)
				servletContext.log("Can't restore :" + aname + ", " + restoreError);
		} while (true);
	}
*/
	public void access() {

		this.lastAccessedTime = this.thisAccessedTime;
		this.thisAccessedTime = System.currentTimeMillis();
	}

	/**
	 * Return the <code>isValid</code> flag for this session without any
	 * expiration check.
	 */
	protected boolean isValidInternal() {
		return (this.isValid || !this.expired);
	}
}

/**
 * @deprecated
 * @author panye
 * 
 */
@SuppressWarnings("unchecked")
final class StandardSessionContext implements HttpSessionContext {

	protected HashMap dummy = new HashMap();

	/**
	 * Return the session identifiers of all sessions defined within this
	 * context.
	 * 
	 * @deprecated As of Java Servlet API 2.1 with no replacement. This method
	 *             must return an empty <code>Enumeration</code> and will be
	 *             removed in a future version of the API.
	 */
	public Enumeration getIds() {

		return (new Enumerator(dummy));

	}

	/**
	 * Return the <code>HttpSession</code> associated with the specified session
	 * identifier.
	 * 
	 * @param id
	 *            Session identifier for which to look up a session
	 * 
	 * @deprecated As of Java Servlet API 2.1 with no replacement. This method
	 *             must return null and will be removed in a future version of
	 *             the API.
	 */
	public HttpSession getSession(String id) {

		return (null);

	}
}