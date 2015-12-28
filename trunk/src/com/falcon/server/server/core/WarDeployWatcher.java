package com.falcon.server.server.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.ServletException;

import com.falcon.server.server.WarDeployer;

public class WarDeployWatcher extends Thread {

	private FalconServer server;

	private File file_webapp;

	private File file_deployDir;

	int sleepTime;

	WarDeployer deploy;

	public WarDeployWatcher(String name, FalconServer server, File source, File target,
			WarDeployer deploy) {

		super.setName(name);
		this.server = server;
		int td = 20;
		try {
			td = Integer.parseInt(System.getProperty("falcon.wardeploy.dynamically"));
		} catch (NumberFormatException nfe) {
			server.log("Default redeployment check interval: " + td + " is used");
		}
		sleepTime = td * 1000;

		file_webapp = source;

		file_deployDir = target;

		this.deploy = deploy;
	}

	public void run() {
		for (;;)
			try {
				deployWatch(file_webapp, file_deployDir);
			} catch (Throwable t) {
				if (t instanceof ThreadDeath)
					throw (ThreadDeath) t;
				server.log("Unhandled " + t, t);
			} finally {
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					break;
				}
			}
	}

	protected synchronized void deployWatch(File warDir, final File deployTarDir) {
		Collection<WebAppConfiguration> se = server.getWebApps();
		ArrayList<WebAppConfiguration> markedServlets = new ArrayList<WebAppConfiguration>(10);
		for (WebAppConfiguration app : se) {
			File war = new File(warDir, app.getContextName() + WarDeployer.DEPLOY_ARCH_EXT);
			if (war.exists() && war.lastModified() > app.lastDeployed) {
				markedServlets.add(app);
			}
		}
		for (WebAppConfiguration was : markedServlets) {
			was.destroy();
			deploy.deployWar(
					new File(warDir, was.deployDir.getName() + WarDeployer.DEPLOY_ARCH_EXT),
					deployTarDir);
			try {
				was = new WebAppConfiguration(was.deployDir, was.deployDir.getName(), server);
				deploy.attachApp(was);
			} catch (ServletException sex) {
		/*		server.log("Creation of a web app " + was.contextName + " failed due "
						+ sex.getRootCause(), sex.getRootCause());
		*/	}
		}
	}

}
