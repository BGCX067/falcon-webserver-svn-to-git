package com.falcon.server.server.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import com.falcon.server.server.Connector;
import com.falcon.server.start.ServerConstants;

public class HttpIoConnector implements Connector {

	public Socket accept() throws IOException {
		return socket.accept();
	}

	public void destroy() throws IOException {
		socket.close();
	}

	public void init(Map<String, Object> inProperties, Map<String, String> outProperties)
			throws IOException {

		Integer tempPort = (Integer) inProperties.get(ServerConstants.ARG_PORT);
		int port = tempPort != null ? tempPort : ServerConstants.DEF_PORT;
		String bindAddrStr = (String) inProperties.get(ServerConstants.ARG_BINDADDRESS);
		InetSocketAddress bindAddr = bindAddrStr != null ? new InetSocketAddress(InetAddress
				.getByName(bindAddrStr), port) : null;

		// 设置接收用户请求的队列数
		String backlogStr = (String) inProperties.get(ServerConstants.ARG_BACKLOG);
		int backlog = backlogStr != null ? Integer.parseInt(backlogStr) : -1;
		if (bindAddr != null) {
			socket = new ServerSocket();
			if (backlog < 0)
				socket.bind(bindAddr);
			else
				socket.bind(bindAddr, backlog);
		} else {
			if (backlog < 0)
				socket = new ServerSocket(port);
			else
				socket = new ServerSocket(port, backlog);
		}
		if (outProperties != null)
			if (socket.isBound())
				outProperties.put(ServerConstants.ARG_BINDADDRESS, socket.getInetAddress()
						.getHostName());
			else
				outProperties.put(ServerConstants.ARG_BINDADDRESS, InetAddress.getLocalHost()
						.getHostName());
	}

	public String toString() {
		return "HttpIoConnector " + socket;
	}

	private ServerSocket socket;
}