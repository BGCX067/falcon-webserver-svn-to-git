package com.falcon.server.server;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;

public interface Connector {
	
	public void init(Map<String, Object> inProperties, Map<String, String> outProperties)
			throws IOException;

	public Socket accept() throws IOException;

	public void destroy() throws IOException;
}