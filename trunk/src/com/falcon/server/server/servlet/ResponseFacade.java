/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.falcon.server.server.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import com.falcon.server.util.StringManager;

/**
 * Facade class that wraps a Coyote response object. All methods are delegated
 * to the wrapped response.
 * 
 */
public class ResponseFacade implements HttpServletResponse {

	// ----------------------------------------------------------- Constructors

	/**
	 * Construct a wrapper for the specified response.
	 * 
	 * @param response
	 *            The response to be wrapped
	 */
	public ResponseFacade(FalconResponse response) {

		this.response = response;
	}

	// ----------------------------------------------- Class/Instance Variables

	/**
	 * The string manager for this package.
	 */
	protected static StringManager sm = StringManager.getManager(Constants.Package);

	/**
	 * The wrapped response.
	 */
	protected FalconResponse response = null;

	// --------------------------------------------------------- Public Methods

	/**
	 * Clear facade.
	 */
	public void clear() {
		response = null;
	}

	/**
	 * Prevent cloning the facade.
	 */
	protected Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	// ------------------------------------------------ ServletResponse Methods

	public String getCharacterEncoding() {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.getCharacterEncoding();
	}

	public ServletOutputStream getOutputStream() throws IOException {

		// if (isFinished())
		// throw new IllegalStateException
		// (/*sm.getString("responseFacade.finished")*/);

		ServletOutputStream sos = response.getOutputStream();
		return (sos);

	}

	public PrintWriter getWriter() throws IOException {

		// if (isFinished())
		// throw new IllegalStateException
		// (/*sm.getString("responseFacade.finished")*/);

		PrintWriter writer = response.getWriter();
		return (writer);
	}

	public void setContentLength(int len) {

		if (isCommitted())
			return;

		response.setContentLength(len);

	}

	public void setContentType(String type) {

		if (isCommitted())
			return;
		response.setContentType(type);
	}

	public void setBufferSize(int size) {

		if (isCommitted())
			throw new IllegalStateException(/*sm.getString("responseBase.reset.ise")*/);

		response.setBufferSize(size);

	}

	public int getBufferSize() {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.getBufferSize();
	}

	public void flushBuffer() throws IOException {

		response.flushBuffer();

	}

	public void resetBuffer() {

		if (isCommitted())
			throw new IllegalStateException(/*sm.getString("responseBase.reset.ise")*/);

		response.resetBuffer();

	}

	public boolean isCommitted() {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return (response.isCommitted());
	}

	public void reset() {

		if (isCommitted())
			throw new IllegalStateException(/*sm.getString("responseBase.reset.ise")*/);

		response.reset();

	}

	public void setLocale(Locale loc) {

		if (isCommitted())
			return;

		response.setLocale(loc);
	}

	public Locale getLocale() {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.getLocale();
	}

	public void addCookie(Cookie cookie) {

		if (isCommitted())
			return;

		response.addCookie(cookie);

	}

	public boolean containsHeader(String name) {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.containsHeader(name);
	}

	public String encodeURL(String url) {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.encodeURL(url);
	}

	public String encodeRedirectURL(String url) {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.encodeRedirectURL(url);
	}

	public String encodeUrl(String url) {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.encodeURL(url);
	}

	public String encodeRedirectUrl(String url) {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.encodeRedirectURL(url);
	}

	public void sendError(int sc, String msg) throws IOException {

		if (isCommitted())
			throw new IllegalStateException(/*sm.getString("responseBase.reset.ise")*/);

		response.sendError(sc, msg);

	}

	public void sendError(int sc) throws IOException {

		if (isCommitted())
			throw new IllegalStateException(/*sm.getString("responseBase.reset.ise")*/);

		response.sendError(sc);

	}

	public void sendRedirect(String location) throws IOException {

		if (isCommitted())
			throw new IllegalStateException(/*sm.getString("responseBase.reset.ise")*/);

		response.sendRedirect(location);

	}

	public void setDateHeader(String name, long date) {

		if (isCommitted())
			return;

		response.setDateHeader(name, date);

	}

	public void addDateHeader(String name, long date) {

		if (isCommitted())
			return;

		response.addDateHeader(name, date);

	}

	public void setHeader(String name, String value) {

		if (isCommitted())
			return;

		response.setHeader(name, value);

	}

	public void addHeader(String name, String value) {

		if (isCommitted())
			return;

		response.addHeader(name, value);

	}

	public void setIntHeader(String name, int value) {

		if (isCommitted())
			return;

		response.setIntHeader(name, value);

	}

	public void addIntHeader(String name, int value) {

		if (isCommitted())
			return;

		response.addIntHeader(name, value);

	}

	public void setStatus(int sc) {

		if (isCommitted())
			return;

		response.setStatus(sc);

	}

	public void setStatus(int sc, String sm) {

		if (isCommitted())
			return;

		response.setStatus(sc, sm);
	}

	public String getContentType() {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		return response.getContentType();
	}

	public void setCharacterEncoding(String arg0) {

		if (response == null) {
			throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
		}

		response.setCharacterEncoding(arg0);
	}

}
