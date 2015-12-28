package com.falcon.server.server;

import java.util.HashMap;

@SuppressWarnings("unchecked")
public class BasicAuthRealm extends HashMap {

	private static final long serialVersionUID = 1L;
	String name;

	public BasicAuthRealm(String name) {
		this.name = name;
	}

	String name() {
		return name;
	}
}
