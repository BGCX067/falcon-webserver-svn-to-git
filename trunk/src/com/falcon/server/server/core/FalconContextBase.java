package com.falcon.server.server.core;

import com.falcon.server.server.Context;
import com.falcon.server.server.Manager;
import com.falcon.server.util.RequestUtil;

public abstract class FalconContextBase implements Context {

	Manager manager;

	/**
	 * Should we attempt to use cookies for session id communication?
	 */
	private boolean cookies = true;

	/**
	 * The background thread.
	 */
	private Thread thread = null;

	/**
	 * Encoded path.
	 */
	protected String contextName;

	protected String contextPath;

	public String getEncodedPath() {
		return RequestUtil.URLDecode(contextName);
	}

	protected int backgroundProcessorDelay = -1;

	/**
	 * The background thread completion semaphore.
	 */
	private boolean threadDone = false;

	public Manager getManager() {
		return manager;
	}

	public void setManager(Manager manager) {
		this.manager = manager;
	}

	/**
	 * Return the "use cookies for session ids" flag.
	 */
	public boolean getCookies() {

		return (this.cookies);

	}

	/**
	 * Set the "use cookies for session ids" flag.
	 * 
	 * @param cookies
	 *            The new flag
	 */
	public void setCookies(boolean cookies) {

		this.cookies = cookies;
	}

	/**
	 * Start the background thread that will periodically check for session
	 * timeouts.
	 */
	protected void threadStart() {

		if (thread != null)
			return;
		if (backgroundProcessorDelay <= 0)
			return;

		threadDone = false;
		String threadName = "FalconBackgroundProcessor[" + toString() + "]";
		thread = new Thread(new FalconBackgroundProcessor(), threadName);
		thread.setDaemon(true);
		thread.start();

	}

	/**
	 * Stop the background thread that is periodically checking for session
	 * timeouts.
	 */
	protected void threadStop() {

		if (thread == null)
			return;

		threadDone = true;
		thread.interrupt();
		try {
			thread.join();
		} catch (InterruptedException e) {
			;
		}
		thread = null;
	}

	protected class FalconBackgroundProcessor implements Runnable {

		public void run() {
			while (!threadDone) {
				try {
					Thread.sleep(backgroundProcessorDelay * 1000L);
				} catch (InterruptedException e) {
					;
				}
				if (!threadDone) {
					manager.backgroundProcess();
				}
			}
		}
	}

}
