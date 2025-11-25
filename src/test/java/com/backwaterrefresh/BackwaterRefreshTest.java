package com.backwaterrefresh;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BackwaterRefreshTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BackwaterRefreshPlugin.class);
		RuneLite.main(args);
	}
}