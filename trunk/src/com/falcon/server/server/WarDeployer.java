package com.falcon.server.server;

import java.io.File;

import com.falcon.server.server.core.FalconServer;
import com.falcon.server.server.core.WebAppConfiguration;

public interface WarDeployer {

	public static final String DEPLOY_ARCH_EXT = ".war";

	public void deployWar(File warFile, File deployTarDir);

	void deploy(FalconServer server);
	
	public void attachApp(WebAppConfiguration webapp);
}
