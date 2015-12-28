package com.falcon.server.server.servlet;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import javax.servlet.ServletInputStream;

public class FalconInputStream extends ServletInputStream {

	private final static boolean STREAM_DEBUG = false;

	/**
	 * The actual input stream (buffered).
	 */
	private InputStream in, origIn;

	private FalconConnection conn;

	private int chunksize = 0;

	private boolean chunking = false, compressed;

	private boolean returnedAsReader, returnedAsStream;

	private long contentLength = -1;

	private long readCount;

	private byte[] oneReadBuf = new byte[1];

	private boolean closed;

	/* ------------------------------------------------------------ */
	/**
	 * Constructor
	 */
	public FalconInputStream(InputStream in, FalconConnection conn) {
		this.conn = conn;
		this.in = new BufferedInputStream(in);
	}

	void refresh() {
		returnedAsReader = false;
		returnedAsStream = false;
		contentLength = -1;
		readCount = 0;
		chunksize = 0;
		closed = false;
		compressed(false);
	}

	/* ------------------------------------------------------------ */
	/**
	 * @param chunking
	 */
	public void chunking(boolean chunking) {
		if (contentLength == -1)
			this.chunking = chunking;
	}

	boolean compressed(boolean on) {
		if (on) {
			if (compressed == false) {
				origIn = in;
				try {
					FalconInputStream sis = new FalconInputStream(in, conn);
					if (chunking) {
						sis.chunking(true);
						chunking(false);
					}
					in = (InputStream) conn.getServer().getGzipInStreamConstr().newInstance(
							new Object[] { sis });
					compressed = true;
				//	conn.server.log("Compressed stream was created with success", null);
				} catch (Exception ex) {
					if (ex instanceof InvocationTargetException)
						conn.getServer().log("Problem in compressed stream creation",
								((InvocationTargetException) ex).getTargetException());
					else
						conn.getServer().log("Problem in compressed stream obtaining", ex);
				}
			}
		} else if (compressed) {
			compressed = false;
			in = origIn;
		}
		return compressed;
	}

	/**
	 * sets max read byte in input
	 */
	void setContentLength(long contentLength) {
		if (this.contentLength == -1 && contentLength >= 0 && chunking == false) {
			this.contentLength = contentLength;
			readCount = 0;
		}
	}

	/* ------------------------------------------------------------ */
	/**
	 * Read a line ended by CRLF, used internally only for reading headers. No
	 * char encoding, ASCII only
	 */
	protected String readLine(int maxLen) throws IOException {
		if (maxLen <= 0)
			throw new IllegalArgumentException("Max len:" + maxLen);
		StringBuffer buf = new StringBuffer(Math.min(1024, maxLen));

		int c;
		boolean cr = false;
		int i = 0;
		while ((c = in.read()) != -1) {
			if (c == 10) { // LF
				if (cr)
					break;
				break;
				// throw new IOException ("LF without CR");
			} else if (c == 13) // CR
				cr = true;
			else {
				// if (cr)
				// throw new IOException ("CR without LF");
				// see
				// http://www.w3.org/Protocols/HTTP/1.1/rfc2616bis/draft-lafon-rfc2616bis-03.html#tolerant.applications
				cr = false;
				if (i >= maxLen)
					throw new IOException("Line lenght exceeds " + maxLen);
				buf.append((char) c);
				i++;
			}
		}
		if (STREAM_DEBUG)
			System.err.println(buf);
		if (c == -1 && buf.length() == 0)
			return null;

		return buf.toString();
	}

	/* ------------------------------------------------------------ */
	public int read() throws IOException {
		int result = read(oneReadBuf, 0, 1);
		if (result == 1)
			return 255 & oneReadBuf[0];
		return -1;
	}

	/* ------------------------------------------------------------ */
	public int read(byte b[]) throws IOException {
		return read(b, 0, b.length);
	}

	/* ------------------------------------------------------------ */
	public synchronized int read(byte b[], int off, int len) throws IOException {
		if (closed)
			throw new IOException("The stream is already closed");
		if (chunking) {
			if (chunksize <= 0 && getChunkSize() <= 0)
				return -1;
			if (len > chunksize)
				len = chunksize;
			len = in.read(b, off, len);
			chunksize = (len < 0) ? -1 : (chunksize - len);
		} else {
			if (contentLength >= 0) {
				if (contentLength - readCount < Integer.MAX_VALUE)

					len = Math.min(len, (int) (contentLength - readCount));
				if (len <= 0) {
					if (STREAM_DEBUG)
						System.err.print("EOF");
					return -1;
				}
				len = in.read(b, off, len);
				if (len > 0)
					readCount += len;
			} else
				// to avoid extra if
				len = in.read(b, off, len);
		}
		if (STREAM_DEBUG && len > 0)
			System.err.print(new String(b, off, len));

		return len;
	}

	/* ------------------------------------------------------------ */
	public long skip(long len) throws IOException {
		if (STREAM_DEBUG)
			System.err.println("instream.skip() :" + len);
		if (closed)
			throw new IOException("The stream is already closed");
		if (chunking) {
			if (chunksize <= 0 && getChunkSize() <= 0)
				return -1;
			if (len > chunksize)
				len = chunksize;
			len = in.skip(len);
			chunksize = (len < 0) ? -1 : (chunksize - (int) len);
		} else {
			if (contentLength >= 0) {
				len = Math.min(len, contentLength - readCount);
				if (len <= 0)
					return -1;
				len = in.skip(len);
				readCount += len;
			} else
				len = in.skip(len);
		}
		return len;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Available bytes to read without blocking. If you are unlucky may return 0
	 * when there are more
	 */
	public int available() throws IOException {
		if (STREAM_DEBUG)
			System.err.println("instream.available()");
		if (closed)
			throw new IOException("The stream is already closed");
		if (chunking) {
			int len = in.available();
			if (len <= chunksize)
				return len;
			return chunksize;
		}

		if (contentLength >= 0) {
			int len = in.available();
			if (contentLength - readCount < Integer.MAX_VALUE)
				return Math.min(len, (int) (contentLength - readCount));
			return len;
		} else
			return in.available();
	}

	/* ------------------------------------------------------------ */
	public void close() throws IOException {
		// keep alive, will be closed by socket
		// in.close();
		if (STREAM_DEBUG)
			System.err.println("instream.close() " + closed);
		if (closed)
			return; // throw new
		// IOException("The stream is already closed");
		// read until end of chunks or content length
		if (chunking)
			while (read() >= 0)
				;
		else if (contentLength < 0)
			;
		else {
			long skipCount = contentLength - readCount;
			while (skipCount > 0) {
				long skipped = skip(skipCount);
				if (skipped <= 0)
					break;
				skipCount -= skipped;
			}
		}
		if (conn.keepAlive == false)
			in.close();
		closed = true;
	}

	/* ------------------------------------------------------------ */
	/**
	 * Mark is not supported
	 * 
	 * @return false
	 */
	public boolean markSupported() {
		return false;
	}

	/* ------------------------------------------------------------ */
	/**
		 * 
		 */
	public void reset() throws IOException {
		// no buffering, so not possible
		if (closed)
			throw new IOException("The stream is already closed");
		if (STREAM_DEBUG)
			System.err.println("instream.reset()");
		in.reset();
	}

	/* ------------------------------------------------------------ */
	/**
	 * Not Implemented
	 * 
	 * @param readlimit
	 */
	public void mark(int readlimit) {
		// not supported
		if (STREAM_DEBUG)
			System.err.println("instream.mark(" + readlimit + ")");
	}

	/* ------------------------------------------------------------ */
	private int getChunkSize() throws IOException {
		if (chunksize < 0)
			return -1;

		chunksize = -1;

		// Get next non blank line
		chunking = false;
		String line = readLine(60);
		while (line != null && line.length() == 0)
			line = readLine(60);
		chunking = true;

		// Handle early EOF or error in format
		if (line == null)
			return -1;

		// Get chunksize
		int i = line.indexOf(';');
		if (i > 0)
			line = line.substring(0, i).trim();
		try {
			chunksize = Integer.parseInt(line, 16);
		} catch (NumberFormatException nfe) {
			throw new IOException("Chunked stream is broken, " + line);
		}

		// check for EOF
		if (chunksize == 0) {
			chunksize = -1;
			// Look for footers
			readLine(60);
			chunking = false;
		}
		return chunksize;
	}

	boolean isReturnedAsStream() {
		return returnedAsStream;
	}

	void setReturnedAsStream(boolean _on) {
		returnedAsStream = _on;
	}

	boolean isReturnedAsReader() {
		return returnedAsReader;
	}

	void setReturnedAsReader(boolean _on) {
		returnedAsReader = _on;
	}
}