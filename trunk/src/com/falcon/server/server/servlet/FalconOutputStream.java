package com.falcon.server.server.servlet;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletOutputStream;

import com.falcon.server.server.Request;
import com.falcon.server.util.Utils;

public class FalconOutputStream extends ServletOutputStream {

	private static final boolean STREAM_DEBUG = false;

	private boolean chunked;

	private boolean closed;

	// TODO: predefine as static byte[] used by chunked
	// underneath stream
	private OutputStream out;

	// private BufferedWriter writer; // for top speed
	private FalconConnection conn;

	private int inInclude;

	private String encoding;

	private/*volatile*/long lbytes;

	private Utils.SimpleBuffer buffer;

	private Request request;

	public void setRequest(Request request) {
		this.request = request;
		encoding = request.getCharacterEncoding();
		if (encoding == null)
			encoding = Utils.ISO_8859_1;
	}

	public FalconOutputStream(OutputStream out, FalconConnection conn) {
		this.out = out;
		this.conn = conn;
		buffer = new Utils.SimpleBuffer();
	}

	void refresh() {
		chunked = false;
		closed = false;
		inInclude = 0;
		lbytes = 0;
		buffer.reset();
		encoding = request.getCharacterEncoding();
		if (encoding == null)
			encoding = Utils.ISO_8859_1;
	}

	protected void reset() {
		if (lbytes == 0)
			buffer.reset();
		else
			throw new IllegalStateException("Result was already committed");
	}

	protected int getBufferSize() {
		return buffer.getSize();
	}

	protected void setBufferSize(int size) {
		if (lbytes > 0)
			throw new IllegalStateException("Bytes already written in response");
		buffer.setSize(size);
	}

	protected void setChunked(boolean set) {
		chunked = set;
	}

	public void print(String s) throws IOException {
		write(s.getBytes(encoding));
	}

	public void write(int b) throws IOException {
		write(new byte[] { (byte) b }, 0, 1);
	}

	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	public void write(byte[] b, int off, int len) throws IOException {
		if (closed) {
			if (STREAM_DEBUG)
				System.err.println((b == null ? "null" : new String(b, off, len))
						+ "\n won't be written, stream closed.");
			throw new IOException("An attempt of writing " + len + " bytes to a closed out.");
		}
		conn.writeHeaders();
		if (len == 0)
			return;
		b = buffer.put(b, off, len);
		len = b.length;
		if (len == 0)
			return;
		off = 0;
		if (chunked) {
			String hexl = Integer.toHexString(len);
			out.write((hexl + "\r\n").getBytes()); // no encoding Ok
			lbytes += 2 + hexl.length();
			out.write(b, off, len);
			lbytes += len;
			out.write("\r\n".getBytes());
			lbytes += 2;
		} else {
			out.write(b, off, len);
			lbytes += len;
		}

		if (STREAM_DEBUG) {
			if (chunked)
				System.err.println(Integer.toHexString(len));
			System.err.print(new String(b, off, len));
			if (chunked)
				System.err.println();
		}
	}

	public void flush() throws IOException {
		if (closed)
			return;
		// throw new IOException("An attempt of flushig closed out.");
		conn.writeHeaders();
		byte[] b = buffer.get();
		if (b.length > 0) {
			if (chunked) {
				String hexl = Integer.toHexString(b.length);
				out.write((hexl + "\r\n").getBytes()); // no encoding Ok
				lbytes += 2 + hexl.length();
				out.write(b);
				lbytes += b.length;
				out.write("\r\n".getBytes());
				lbytes += 2;
				if (STREAM_DEBUG) {
					System.err.println(hexl);
					System.err.print(new String(b));
					System.err.println();
				}
			} else {
				out.write(b);
				lbytes += b.length;
				if (STREAM_DEBUG) {
					System.err.print(new String(b));
				}
			}
		}
		out.flush();
	}

	public void close() throws IOException {
		if (closed)
			return;
		// throw new IOException("Stream is already closed.");
		// new IOException("Stream closing").printStackTrace();
		try {
			flush();
			if (inInclude == 0) {
				if (chunked) {
					out.write("0\r\n\r\n".getBytes());
					lbytes += 5;
					if (STREAM_DEBUG)
						System.err.print("0\r\n\r\n");
					// TODO: here is possible to write trailer headers
					out.flush();
				}
				if (conn.keepAlive == false)
					out.close();
			}
		} finally {
			closed = true;
		}
	}

	public long lengthWritten() {
		return lbytes;
	}

	boolean isInInclude() {
		return inInclude == 0;
	}

	void setInInclude(boolean _set) {
		inInclude = _set ? 1 : 0;
	}

}