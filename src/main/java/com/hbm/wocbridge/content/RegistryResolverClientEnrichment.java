package com.hbm.wocbridge.content;

import java.util.HashSet;
import java.util.Set;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;

@SideOnly(Side.CLIENT)
public final class RegistryResolverClientEnrichment {

	private RegistryResolverClientEnrichment() { }

	public static Set<String> collectCreativeTabNames() {
		Set<String> names = new HashSet<String>();
		for(CreativeTabs tab : CreativeTabs.creativeTabArray) {
			if(tab != null) names.add(tab.getTabLabel());
		}
		return names;
	}
}
