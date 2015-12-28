package com.falcon.server.server.core;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.servlet.ServletException;

import com.falcon.server.server.WarDeployer;
import com.falcon.server.util.Utils;
import static com.falcon.server.start.ServerConstants.*;

public class FalconWarDeploy implements WarDeployer {

	protected FalconServer server;

	/**
	 * in deploy mode scans for all wars in war directory (app deployment dir)
	 * for each war looks in corresponding place of deploy directory and figures
	 * a difference, like any file in war exists and no corresponding file in
	 * deploy directory or it's older if difference positive, then delete target
	 * deploy directory unpack war if run mode process all WEB-INF/web.xml and
	 * build app descriptor, including context name, servlet names, servlet
	 * urls, class parameters process every app descriptor as standard servlet
	 * connection proc dispatch for every context name assigned an app
	 * dispatcher, it uses the rest to find servlet and do resource mapping
	 * 
	 */

	public void deploy(File warDir, final File deployTarDir) {

		if (warDir.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				if (pathname.isFile() && pathname.getName().toLowerCase().endsWith(DEPLOY_ARCH_EXT)) {
					deployWar(pathname, deployTarDir);
					return true;
				}
				return false;
			}
		}).length == 0)
			server.log("No .war packaged web apps found.");
		if (deployTarDir.listFiles(new FileFilter() {
			public boolean accept(File file) {
				if (file.isDirectory())
					try {
						attachApp(new WebAppConfiguration(file, file.getName(), server));
						return true;
					} catch (ServletException se) {
						/*	server.log("Creation of a web app " + file.getName() + " failed due "
									+ se.getRootCause(), se.getRootCause());*/
					}
				return false;
			}
		}).length == 0)
			server.log("No web apps have been deployed.");
	}

	public void deployWar(File warFile, File deployTarDir) {
		String context = warFile.getName();
		assert context.toLowerCase().endsWith(DEPLOY_ARCH_EXT);
		context = context.substring(0, context.length() - DEPLOY_ARCH_EXT.length());
		server.log("Deploying " + context);
		ZipFile zipFile = null;
		File deployDir = new File(deployTarDir, context);
		try {
			// some overhead didn't check that doesn't exist
			if (assureDir(deployDir) == false) {
				server.log("Can't reach deployment dir " + deployDir);
				return;
			}
			zipFile = new ZipFile(warFile);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry ze = entries.nextElement();
				String en = ze.getName();
				if (File.separatorChar == '/')
					en = en.replace('\\', File.separatorChar);
				File outFile = new File(deployDir, en);
				if (ze.isDirectory()) {
					outFile.mkdirs();
				} else {
					OutputStream os = null;
					InputStream is = null;
					File parentFile = outFile.getParentFile();
					if (parentFile.exists() == false)
						parentFile.mkdirs();
					if (outFile.exists() && outFile.lastModified() >= ze.getTime()) {
						continue;
					}
					try {
						os = new FileOutputStream(outFile);
						is = zipFile.getInputStream(ze);
						copyStream(is, os);
						outFile.setLastModified(ze.getTime());
					} catch (IOException ioe2) {
						server.log("Problem in extracting " + en + " " + ioe2);
					} finally {
						try {
							os.close();
						} catch (Exception e2) {

						}
						try {
							is.close();
						} catch (Exception e2) {

						}
					}
				}
			}
		} catch (ZipException ze) {
			server.log("Invalid .war format");
		} catch (IOException ioe) {
			server.log("Can't read " + warFile + "/ " + ioe);
		} finally {
			try {
				zipFile.close();
				zipFile = null;
			} catch (Exception e) {

			}
		}
	}

	public void attachApp(WebAppConfiguration webapp) {
		server.addWebApp(webapp.getContextPath(), webapp);
		server.log("register webapp " + webapp.contextName);
	}

	public void deploy(FalconServer server) {
		this.server = server;
		String webapp_dir = System.getProperty(WEBAPPDIR);
		if (webapp_dir == null)
			webapp_dir = System.getProperty("user.dir") + File.separator + "webapps";
		System.setProperty(WEBAPPDIR, webapp_dir);
		final File file_webapp = new File(webapp_dir);
		if (assureDir(file_webapp) == false) {
			server.log("Web app " + file_webapp + " isn't a directory, deployment is impossible.");
			return;
		}

		String deployDir = System.getProperty(DEPLOYDIR);
		File file_deployDir = null;
		if (deployDir != null)
			file_deployDir = new File(deployDir);
		else
			file_deployDir = file_webapp;
		System.setProperty(DEPLOYDIR, webapp_dir);
		
		deploy(file_webapp, file_deployDir);
		if (System.getProperty("falcon.wardeploy.dynamically") != null) {

			Thread watcher = new WarDeployWatcher("Deploy update watcher", server, file_webapp,
					file_deployDir, this);
			watcher.setDaemon(true);
			watcher.start();
		}
	}

	protected boolean assureDir(File fileDir) {
		if (fileDir.exists() == false)
			fileDir.mkdirs();
		if (fileDir.isDirectory() == false) {
			return false;
		}
		return true;
	}

	static void copyStream(InputStream is, OutputStream os) throws IOException {
		Utils.copyStream(is, os, -1);
	}
}