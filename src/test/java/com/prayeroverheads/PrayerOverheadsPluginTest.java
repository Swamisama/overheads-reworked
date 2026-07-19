package com.prayeroverheads;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PrayerOverheadsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PrayerOverheadsPlugin.class);
		RuneLite.main(args);
	}
}
