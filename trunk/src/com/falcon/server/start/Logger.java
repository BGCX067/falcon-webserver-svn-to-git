package com.falcon.server.start;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class Logger {

	PrintStream logStream;

	public Logger(PrintStream logStream) {
		super();
		this.logStream = logStream;
	}

	/**
	 * 添加日志信息
	 */
	public void log(String message) {
		Date date = new Date(System.currentTimeMillis());
		logStream.println("[" + date.toString() + "] " + message);
	}

	public void log(String message, Throwable throwable) {
		if (throwable != null) {
			StringWriter sw;
			PrintWriter pw = new PrintWriter(sw = new StringWriter());
			throwable.printStackTrace(pw);
			message = message + '\n' + sw;
		}
		log(message);
	}

	public void log(Exception exception, String message) {
		log(message, exception);
	}
}
