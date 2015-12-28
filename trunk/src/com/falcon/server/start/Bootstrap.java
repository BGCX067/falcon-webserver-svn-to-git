package com.falcon.server.start;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import com.falcon.server.server.core.FalconServer;
import com.falcon.server.util.Utils;

public class Bootstrap implements ServerConstants {

	private static final String CLI_FILENAME = "command_param.xml";

	private String Falcon_honme = null;

	protected static FalconServer server;

	private static Thread sdHook;

	public void boot(String[] args) {
		String workPath = System.getProperty("user.dir", ".");
		StringBuffer messages = null;

		int argc = args.length;
		int argn;
		if (argc == 0) { // a try to read from file for java -jar server.jar
			args = readArguments(workPath, CLI_FILENAME);
			if (args == null) {
				messages = appendMessage(messages, "Can't read from CLI file\n");
			} else
				argc = args.length;
		}
		Falcon_honme = workPath;

		Map<String, Object> arguments = new HashMap<String, Object>(20);
		arguments.put(ARG_WORK_DIRECTORY, workPath);
		for (argn = 0; argn < argc && args[argn].length() > 0 && args[argn].charAt(0) == '-';) {
			if (args[argn].equals("-p") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_PORT, new Integer(args[argn]));
			} else if (args[argn].equals("-s") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_SERVLETS, args[argn]);
			} else if (args[argn].equals("-r") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_REALMS, args[argn]);
			} else if (args[argn].equals("-a") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_ALIASES, args[argn]);
			} else if (args[argn].equals("-b") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_BINDADDRESS, args[argn]);
			} else if (args[argn].equals("-k") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_BACKLOG, args[argn]/*new Integer(args[argn])*/);
			} else if (args[argn].equals("-cout") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_COMPILE_OUT, args[argn]);
			} else if (args[argn].equals("-w") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_WAR, args[argn]);
			} else if (args[argn].equals("-mka") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_MAX_CONN_USE, args[argn]);
				arguments.put(ARG_KEEPALIVE, Boolean.TRUE);
			} else if (args[argn].equals("-nka")) {
				arguments.put(ARG_KEEPALIVE, Boolean.FALSE);
			} else if (args[argn].equals("-sp")) {
				arguments.put(ARG_SESSION_PERSIST, Boolean.TRUE);
			} else if (args[argn].equals("-kat") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_KEEPALIVE_TIMEOUT, args[argn]);
				arguments.put(ARG_KEEPALIVE, Boolean.TRUE);
			} else if (args[argn].equals("-e") && argn + 1 < argc) {
				++argn;
				try {
					arguments.put(ARG_SESSION_TIMEOUT, new Integer(args[argn]));
				} catch (NumberFormatException nfe) {
				}
			} else if (args[argn].equals("-z") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_THREAD_POOL_SIZE, args[argn]);
				// backlog will be anyway upper limitation
			} else if (args[argn].equals("-d") && argn + 1 < argc) {
				++argn;
				arguments.put(ARG_LOG_DIR, args[argn]);
			} else if (args[argn].startsWith("-l")) {
				arguments
						.put(
								ARG_ACCESS_LOG_FMT,
								"{0}:{9} {1} {2} [{3,date,dd/MMM/yyyy:HH:mm:ss Z}] \"{4} {5} {6}\" {7,number,#} {8,number} {10} {11}");
				if (args[argn].length() > 2) {
					arguments.put(ARG_LOG_OPTIONS, args[argn].substring(2).toUpperCase());
					if (args[argn].indexOf('f') >= 0) {
						++argn;
						arguments.put(ARG_ACCESS_LOG_FMT, args[argn]);
					}
				} else
					arguments.put(ARG_LOG_OPTIONS, "");
			} else if (args[argn].startsWith("-nohup")) {
				arguments.put(ARG_NOHUP, ARG_NOHUP);
			} else if (args[argn].equals("-m") && argn + 1 < argc) {
				++argn;
				try {
					arguments.put(ARG_MAX_ACTIVE_SESSIONS, new Integer(args[argn]));
					if (((Integer) arguments.get(ARG_MAX_ACTIVE_SESSIONS)).intValue() < DEF_MIN_ACT_SESS)
						arguments.put(ARG_MAX_ACTIVE_SESSIONS, new Integer(DEF_MIN_ACT_SESS));
				} catch (NumberFormatException nfe) {
					// ignored
				}
			} else if (args[argn].equals("-err")) {
				if (argn + 1 < argc && args[argn + 1].startsWith("-") == false) {
					++argn;
					try {
						arguments.put(ARG_ERR, (PrintStream) Class.forName(args[argn])
								.newInstance());
					} catch (Error er) {
						messages = appendMessage(messages,
								"Problem of processing class parameter of error redirection stream: ")
								.append(er).append('\n');
					} catch (Exception ex) {
						messages = appendMessage(messages,
								"Exception in processing class parameter of error redirection stream: ")
								.append(ex).append('\n');
					}
				} else
					arguments.put(ARG_ERR, System.err);
			} else if (args[argn].equals("-out")) {
				if (argn + 1 < argc && args[argn + 1].startsWith("-") == false) {
					++argn;
					try {
						arguments.put(ARG_OUT, (PrintStream) Class.forName(args[argn])
								.newInstance());
					} catch (Error er) {
						messages = appendMessage(messages,
								"Problem of processing class parameter of out redirection stream: ")
								.append(er).append('\n');
					} catch (Exception ex) {
						messages = appendMessage(messages,
								"Exception in processing class parameter of out redirection stream: ")
								.append(ex).append('\n');
					}
				}
			} else if (args[argn].startsWith("-")) { // free args, note it
				if (args[argn].length() > 1)
					arguments.put(args[argn].substring(1),// .toUpperCase(),
							argn < argc - 1 ? args[++argn] : "");
			} else
				usage();

			++argn;
		}
		if (argn != argc)
			usage();

		PrintStream logout = initLog(arguments, messages);
		/**
		 * format path mapping from=givenpath;dir=realpath
		 */
		initAliases(arguments);

		// Create the server.
		startServer(arguments, logout);
	}

	public static void main(String[] args) {
		new Bootstrap().boot(args);
	}

	private static String[] readArguments(String workPath, String file) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(new File(workPath, file)));
			return Utils.splitStr(br.readLine(), "\"");
		} catch (Exception e) { // many can happen
			return null;
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException ioe) {
				}
		}
	}

	private static StringBuffer appendMessage(StringBuffer messages, String message) {
		if (messages == null)
			messages = new StringBuffer(100);
		return messages.append(message);
	}

	private static void usage() {
		System.out
				.println(SERVER_NAME
						+ " "
						+ SERVER_VERSION
						+ "\n"
						+ "Usage:  "
						+ " [-p port] [-s servletpropertiesfile] [-a aliasmappingfile]\n"
						+ "         [-b bind address] [-k backlog] [-l[a][r][f access_log_fmt]]\n"
						+ "         [-c cgi-bin-dir] [-m max_active_session] [-d log_directory]\n"
						+ "         [-sp] [-j jsp_servlet_class] [-w war_deployment_module_class]\n"
						+ "         [-nka] [-kat timeout_in_secs] [-mka max_times_connection_use]\n"
						+ "         [-e [-]duration_in_minutes] [-nohup] [-z max_threadpool_size]\n"
						+ "         [-err [class_name?PrintStream]] [-out [class_name?PrintStream]]\n"
						+ "         [-acceptorImpl class_name_of_Accpetor_impl [extra_acceptor_parameters] ]\n"
						+ "  Legend:\n" + "    -sp    session persistence\n"
						+ "    -l     access log a - with user agent, and r - referer\n"
						+ "    -nka   no keep alive for connection");
		System.exit(1);
	}

	private PrintStream initLog(Map<String, Object> arguments, StringBuffer messages) {
		PrintStream printstream = System.err;
		if (arguments.get(ARG_OUT) != null)
			printstream = (PrintStream) arguments.get(ARG_OUT);
		else {
			String logEncoding = System.getProperty(DEF_LOGENCODING);
			try {
				File logDir = new File(Falcon_honme);
				if (arguments.get(ARG_LOG_DIR) != null) {
					File dir = new File((String) arguments.get(ARG_LOG_DIR));
					if (dir.isAbsolute() == true) {
						logDir = dir;
					} else {
						logDir = new File(Falcon_honme, dir.getPath());
					}
				}
				if (!logDir.exists())
					logDir.mkdir();
				File logFile = new File(logDir.getPath(), "FAL-" + System.currentTimeMillis()
						+ ".log");
				if (logEncoding != null)
					printstream = new PrintStream(new FileOutputStream(logFile), true, logEncoding); /* 1.4 */
				else
					printstream = new PrintStream(new FileOutputStream(logFile), true);
			} catch (IOException e) {
				System.err.println("I/O problem at setting a log stream " + e);
			}
		}
		if (arguments.get(ARG_ERR) != null) {
			System.setErr((PrintStream) arguments.get(ARG_ERR));
		} else {
			System.setErr(printstream);
		}
		if (messages != null)
			System.err.println(messages);

		return printstream;
	}

	private void initAliases(Map<String, Object> arguments) {
		if (arguments.get(ARG_ALIASES) != null) {
			File file = new File((String) arguments.get(ARG_ALIASES));
			if (file.isAbsolute() == false)
				file = new File(Falcon_honme, file.getPath());
			if (file.exists() && file.canRead()) {
				try {
					// DataInputStream in = new DataInputStream(
					// new FileInputStream(file));
					BufferedReader in = new BufferedReader(new InputStreamReader(
							new FileInputStream(file)));
					do {
						String mappingstr = in.readLine(); // no arguments in
						// non ASCII
						// encoding allowed
						if (mappingstr == null)
							break;
						if (mappingstr.startsWith("#"))
							continue;
						StringTokenizer maptokenzr = new StringTokenizer(mappingstr, "=;");
						if (maptokenzr.hasMoreTokens()) {
							if (maptokenzr.nextToken("=").equalsIgnoreCase("from")) {
								if (maptokenzr.hasMoreTokens()) {
									String srcpath = maptokenzr.nextToken("=;");
									if (maptokenzr.hasMoreTokens()
											&& maptokenzr.nextToken(";=").equalsIgnoreCase("dir"))
										try {
											if (maptokenzr.hasMoreTokens()) {
												File mapFile = new File(maptokenzr.nextToken());
												if (mapFile.isAbsolute() == false)
													mapFile = new File(Falcon_honme, mapFile
															.getPath());
											}
										} catch (NullPointerException e) {
										}
								}
							}
						}
					} while (true);
				} catch (IOException e) {
					System.err.println("Problem reading aliases file: "
							+ arguments.get(ARG_ALIASES) + "/" + e);
				}
			} else
				System.err.println("File " + file + " (" + arguments.get(ARG_ALIASES)
						+ ") doesn't exist or not readable.");
		}
	}

	private void startServer(Map<String, Object> arguments, PrintStream printstream) {
		server = new FalconServer(arguments, printstream);
		// And add the standard Servlets.
		server.addWarDeployer((String) arguments.get(ARG_WAR));
		/*if (arguments.get(ARG_NOHUP) == null){
			Thread stop=new Thread(new StopMonitor(),"Stop Monitor");
			stop.setDaemon(true);
			stop.start();
		}else {*/
		Runtime.getRuntime().addShutdownHook(sdHook = new Thread(new Runnable() {
			synchronized public void run() {
				server.destroyAllWebapp();
			}
		}, "ShutDownHook"));
		// }
		// And run.
		int code = server.serve();
		if (code != 0 && arguments.get(ARG_NOHUP) == null)
			try {
				System.out.println();
				System.in.close(); // to break termination thread
			} catch (IOException e) {
			}
		try {
			if (sdHook != null)
				Runtime.getRuntime().removeShutdownHook(sdHook);
			server.destroyAllWebapp();
		} catch (IllegalStateException ise) {

		} catch (Throwable t) {
			server.log("At destroying ", t);
		}
		killAliveThreads();
		Runtime.getRuntime().halt(code);
	}

	private static void killAliveThreads() {
		server.getPool().shutdown();
		ThreadGroup tg = Thread.currentThread().getThreadGroup();
		while (tg.getParent() != null)
			tg = tg.getParent();
		int ac = tg.activeCount() + tg.activeGroupCount() + 10;

		Thread[] ts = new Thread[ac];
		ac = tg.enumerate(ts, true);
		if (ac == ts.length)
			// server.log("Destroy:interruptRunningProcesses: Not all threads will be stopped.");
			// kill non daemon
			for (int i = 0; i < ac; i++)
				if (ts[i].isDaemon() == false) {
					String tn = ts[i].getName();
					// System.err.println("Interrupting and kill " + tn);

					if (ts[i] == Thread.currentThread() || "Stop Monitor".equals(tn)
							|| "ShutDownHook".equals(tn) || "DestroyJavaVM".equals(tn)
							|| (tn != null && tn.startsWith("AWT-")) || "main".equals(tn))
						continue;
					ts[i].interrupt();
					Thread.yield();
					if (ts[i].isAlive()) {
						try {
							ts[i].interrupt();
						} catch (Throwable t) {
							if (t instanceof ThreadDeath) {
								/*	server
											.log(
													"Thread death exception happened and stopping thread, thread stopping loop will be terminated",
													t);*/
								throw (ThreadDeath) t;
							} // else
							// server.log("An exception at stopping " + ts[i] +
							// " " + t);
						}
					}
				}// else
		// serve.log("Daemon thread "+ts[i].getName()+" is untouched.");
	}

	class StopMonitor implements Runnable {

		public void run() {
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
			String line;
			while (true) {
				try {
					System.out.println("Press \"quit\" <ENTER>, stop the server ");
					line = in.readLine();
					if (line != null && line.length() > 0 && line.equals("quit")) {
						server.notifyStop();
						break;
					}
				} catch (IOException e) {
					server.log("Exception in reading from console ", e);
					break;
				}
			}
		}
	}

}
