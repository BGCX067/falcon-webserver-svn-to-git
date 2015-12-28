package com.falcon.server.server;

public class ErrorPage {
	String errorPage;

	Class<?> exception;

	int errorCode;

	public ErrorPage(String page, String exClass, String code) {
		if (page == null || page.length() == 0 || page.charAt(0) != '/')
			throw new IllegalArgumentException("Error page path '" + page + "' must start with '/'");
		if (page.charAt(0) == '/')
			errorPage = page;
		else
			errorPage = "/" + page;
		try {
			exception = Class.forName(exClass);
		} catch (Exception e) {

		}
		try {
			errorCode = Integer.parseInt(code);
		} catch (Exception e) {

		}
	}
}