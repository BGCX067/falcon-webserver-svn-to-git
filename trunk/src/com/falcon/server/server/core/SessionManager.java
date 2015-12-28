package com.falcon.server.server.core;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.falcon.server.server.Context;
import com.falcon.server.server.Manager;
import com.falcon.server.server.Session;
import com.falcon.server.server.servlet.FalconSession;

public class SessionManager implements Manager {

	private int count = 0;

	private Logger log = Logger.getLogger(getClass());

	protected Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

	Context context;
	/**
	 * A random number generator to use when generating session identifiers.
	 */
	protected Random random = null;

	/**
	 * The maximum number of active Sessions allowed, or -1 for no limit.
	 */
	protected int maxActiveSessions = -1;

	/**
	 * Number of session creations that failed due to maxActiveSessions.
	 */
	protected int rejectedSessions = 0;

	/**
	 * Return the MessageDigest implementation to be used when creating session
	 * identifiers.
	 */
	protected MessageDigest digest = null;
	/**
	 * The default message digest algorithm to use if we cannot use the
	 * requested one.
	 */
	protected static final String DEFAULT_ALGORITHM = "MD5";

	/**
	 * The message digest algorithm to be used when generating session
	 * identifiers. This must be an algorithm supported by the
	 * <code>java.security.MessageDigest</code> class on your platform.
	 */
	protected String algorithm = DEFAULT_ALGORITHM;
	/**
	 * The session id length of Sessions created by this Manager.
	 */
	protected int sessionIdLength = 16;
	/**
	 * The default maximum inactive interval for Sessions created by this
	 * Manager.
	 */
	protected int maxInactiveInterval = 60;

	/**
	 * Return the default maximum inactive interval (in seconds) for Sessions
	 * created by this Manager.
	 */
	public int getMaxInactiveInterval() {

		return (this.maxInactiveInterval);

	}

	/**
	 * Set the default maximum inactive interval (in seconds) for Sessions
	 * created by this Manager.
	 * 
	 * @param interval
	 *            The new default value
	 */
	public void setMaxInactiveInterval(int interval) {

		this.maxInactiveInterval = interval;
	}

	/**
	 * Number of session creations that failed due to maxActiveSessions
	 * 
	 * @return The count
	 */
	public int getRejectedSessions() {
		return rejectedSessions;
	}

	public void setRejectedSessions(int rejectedSessions) {
		this.rejectedSessions = rejectedSessions;
	}

	/**
	 * Gets the session id length (in bytes) of Sessions created by this
	 * Manager.
	 * 
	 * @return The session id length
	 */
	public int getSessionIdLength() {

		return (this.sessionIdLength);
	}

	/**
	 * Sets the session id length (in bytes) for Sessions created by this
	 * Manager.
	 * 
	 * @param idLength
	 *            The session id length
	 */
	public void setSessionIdLength(int idLength) {

		this.sessionIdLength = idLength;
	}

	protected int sessionCounter = 0;

	public void setSessionCounter(int sessionCounter) {
		this.sessionCounter = sessionCounter;
	}

	/**
	 * Total sessions created by this manager.
	 * 
	 * @return sessions created
	 */
	public int getSessionCounter() {
		return sessionCounter;
	}

	public SessionManager(Context context) {
		super();
		this.context = context;
	}

	/**
	 * 处理session过期的频率
	 */
	protected int processExpiresFrequency = 6;

	protected long processingTime = 0;

	public int getProcessExpiresFrequency() {
		return processExpiresFrequency;
	}

	public void setProcessExpiresFrequency(int processExpiresFrequency) {
		this.processExpiresFrequency = processExpiresFrequency;
	}

	public void backgroundProcess() {
		count = (count + 1) % processExpiresFrequency;
		if (count == 0)
			processExpires();
	}

	public void processExpires() {

		long timeNow = System.currentTimeMillis();
		Session sessions[] = findSessions();
		int expireHere = 0;

		if (log.isDebugEnabled())
			log.debug("Start expire sessions SessionManager at " + timeNow + " sessioncount "
					+ sessions.length);
		for (int i = 0; i < sessions.length; i++) {
			if (sessions[i] != null && !sessions[i].isValid()) {
				expireHere++;
			}
		}
		long timeEnd = System.currentTimeMillis();
		if (log.isDebugEnabled())
			log.debug("End expire sessions SessionManager processingTime " + (timeEnd - timeNow)
					+ " expired sessions: " + expireHere);
		processingTime += (timeEnd - timeNow);

	}

	public Session[] findSessions() {

		return sessions.values().toArray(new Session[0]);
	}

	/**
	 * Set the maximum number of actives Sessions allowed, or -1 for no limit.
	 * 
	 * @param max
	 *            The new maximum number of sessions
	 */
	public void setMaxActiveSessions(int max) {

		this.maxActiveSessions = max;
	}

	/**
	 * Return the maximum number of active Sessions allowed, or -1 for no limit.
	 */
	public int getMaxActiveSessions() {

		return (this.maxActiveSessions);

	}

	protected FalconSession getNewSession() {
		return new FalconSession(this);
	}

	public Session createSession(String sessionId) {
		if ((maxActiveSessions >= 0) && (sessions.size() >= maxActiveSessions)) {
			rejectedSessions++;
			throw new IllegalStateException(
					"create session error,it is beyond the max active session num");
		}

		Session session = getNewSession();

		// Initialize the properties of the new session and return it
		session.setValid(true);
		session.setCreationTime(System.currentTimeMillis());
		session.setMaxInactiveInterval(this.maxInactiveInterval);
		session.setNew(true);
		if (sessionId == null) {
			sessionId = generateSessionId();
		}
		session.setId(sessionId);
		sessionCounter++;

		return (session);
	}

	/**
	 * Generate and return a new session identifier.
	 */
	protected synchronized String generateSessionId() {

		byte random[] = new byte[16];
		String result = null;

		// Render the result as a String of hexadecimal digits
		StringBuffer buffer = new StringBuffer();
		do {
			int resultLenBytes = 0;

			while (resultLenBytes < this.sessionIdLength) {
				getRandomBytes(random);
				random = getDigest().digest(random);
				for (int j = 0; j < random.length && resultLenBytes < this.sessionIdLength; j++) {
					byte b1 = (byte) ((random[j] & 0xf0) >> 4);
					byte b2 = (byte) (random[j] & 0x0f);
					if (b1 < 10)
						buffer.append((char) ('0' + b1));
					else
						buffer.append((char) ('A' + (b1 - 10)));
					if (b2 < 10)
						buffer.append((char) ('0' + b2));
					else
						buffer.append((char) ('A' + (b2 - 10)));
					resultLenBytes++;
				}
			}
			result = buffer.toString();
		} while (sessions.containsKey(result));
		return (result);
	}

	protected void getRandomBytes(byte bytes[]) {

		getRandom().nextBytes(bytes);
	}

	public Random getRandom() {
		if (this.random == null) {
			// Calculate the new random number generator seed
			long seed = System.currentTimeMillis();
			this.random = new java.util.Random();
			this.random.setSeed(seed);
		}
		return (this.random);

	}

	/**
	 * Return the MessageDigest object to be used for calculating session
	 * identifiers. If none has been created yet, initialize one the first time
	 * this method is called.
	 */
	public synchronized MessageDigest getDigest() {

		if (this.digest == null) {
			long t1 = System.currentTimeMillis();
			if (log.isDebugEnabled())
				log.debug("start using algorithm " + algorithm);
			try {
				this.digest = MessageDigest.getInstance(algorithm);
			} catch (NoSuchAlgorithmException e) {
				log.error("algorithm error" + e);
				try {
					this.digest = MessageDigest.getInstance(DEFAULT_ALGORITHM);
				} catch (NoSuchAlgorithmException f) {
					log.error("algorithm dose not exist " + DEFAULT_ALGORITHM);
					this.digest = null;
				}
			}
			if (log.isDebugEnabled())
				log.debug("algorithm end");
			long t2 = System.currentTimeMillis();
			if (log.isDebugEnabled())
				log.debug("getDigest() " + (t2 - t1));
		}

		return (this.digest);

	}

	/**
	 * Return the message digest algorithm for this Manager.
	 */
	public String getAlgorithm() {

		return (this.algorithm);

	}

	/**
	 * Set the message digest algorithm for this Manager.
	 * 
	 * @param algorithm
	 *            The new message digest algorithm
	 */
	public void setAlgorithm(String algorithm) {

		this.algorithm = algorithm;
	}

	public Context getContext() {
		return context;
	}

	public void remove(Session session) {
		sessions.remove(session.getId());
	}

	/**
	 * Add this Session to the set of active Sessions for this Manager.
	 * 
	 * @param session
	 *            Session to be added
	 */
	public void add(Session session) {

		sessions.put(session.getId(), session);
	}

	public Session findSession(String id) throws IOException {
		if (id == null)
			return (null);
		return (Session) sessions.get(id);
	}
}
