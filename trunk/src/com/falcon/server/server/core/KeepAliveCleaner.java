package com.falcon.server.server.core;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.falcon.server.server.servlet.FalconConnection;

class KeepAliveCleaner extends Thread {

	protected List<FalconConnection> connections;

	protected List<FalconConnection> ingoings;

	protected boolean stopped;

	private boolean noCheckClose;

	private FalconServer server;

	/**
	 * max number of a connection use in keep-alive default is 100
	 */
	private int maxUse;

	KeepAliveCleaner(FalconServer server) {
		super("KeepAlive cleaner");
		connections = new ArrayList<FalconConnection>();
		ingoings = new ArrayList<FalconConnection>();
		this.server = server;
		maxUse = server.getMaxTimesConnectionUse();
		setDaemon(true);
	}

	synchronized void addConnection(FalconConnection conn) {
		synchronized (ingoings) {
			if (stopped == false && ingoings.size() <= maxUse)
				ingoings.add(conn);
		}
	}

	public void run() {
		long d = server.getKeepAliveDuration();

		while (true) {
			synchronized (ingoings) {
				Iterator<FalconConnection> i = ingoings.iterator();
				while (i.hasNext()) {
					connections.add(i.next());
					i.remove();
				}
			}
			Iterator<FalconConnection> i = connections.iterator();
			long ct = System.currentTimeMillis();
			d = server.getKeepAliveDuration();
			while (i.hasNext()) {
				FalconConnection conn = (FalconConnection) i.next();
				Socket socket = conn.getSocket();
				boolean closed = socket == null;
				if (!noCheckClose)
					synchronized (conn) {
						if (socket != null)
							try {
								closed = !socket.isConnected() && socket.isClosed();
							} catch (IllegalArgumentException e) {
								noCheckClose = true;
							} catch (SecurityException e) {
								noCheckClose = true;
							}
					}

				if (closed
						|| (conn.isKeepAlive() && (ct - conn.getLastWait() > d && conn.getLastRun() < conn
								.getLastWait())) || stopped) {
					i.remove();
					synchronized (conn) {
						if (socket != null)
							try {
								socket.getInputStream().close();
								socket.close();
							} catch (IOException ioe) {
								ioe.printStackTrace();
							}
					}
				}

				if (stopped && connections.size() == 0)
					break;
				try {
					sleep(d);
				} catch (InterruptedException ie) {
					stopped = true;
				}
			}
		}
	}
}