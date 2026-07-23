package com.hbm.wocbridge.debug;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.wocbridge.debug.ContentExporter.ContentEnrichment;
import com.hbm.wocbridge.debug.ContentExporter.CreativeTabData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

@SideOnly(Side.CLIENT)
public final class ContentExporterClientEnrichment implements ContentEnrichment {

	private int variantFailures;
	private int displayNameFailures;
	private int creativeTabFailures;

	@Override
	public boolean hasClientEnrichment() {
		return true;
	}

	@Override
	public void addItemVariants(Item item, Map<Integer, ItemStack> variants) {
		List<ItemStack> raw = new ArrayList<ItemStack>();
		try {
			item.getSubItems(item, item.getCreativeTab(), raw);
			for(ItemStack stack : raw) {
				if(stack == null || stack.getItem() != item) continue;
				int metadata = stack.getItemDamage();
				if(metadata < 0 || metadata > 32767 || variants.containsKey(metadata)) continue;
				variants.put(metadata, stack.copy());
			}
		} catch(Throwable ex) {
			variantFailures++;
		}
	}

	@Override
	public String getItemDisplayName(ItemStack stack) {
		try {
			return safe(stack == null ? "" : stack.getDisplayName());
		} catch(Throwable ex) {
			displayNameFailures++;
			return "";
		}
	}

	@Override
	public String getBlockDisplayName(Block block) {
		try {
			return safe(block == null ? "" : block.getLocalizedName());
		} catch(Throwable ex) {
			displayNameFailures++;
			return "";
		}
	}

	@Override
	public String getItemCreativeTabLabel(Item item) {
		try {
			CreativeTabs tab = item == null ? null : item.getCreativeTab();
			return tab == null ? "" : safe(tab.getTabLabel());
		} catch(Throwable ex) {
			creativeTabFailures++;
			return "";
		}
	}

	@Override
	public String getBlockCreativeTabLabel(Block block) {
		try {
			CreativeTabs tab = block == null ? null : block.getCreativeTabToDisplayOn();
			return tab == null ? "" : safe(tab.getTabLabel());
		} catch(Throwable ex) {
			creativeTabFailures++;
			return "";
		}
	}

	@Override
	public List<CreativeTabData> getCreativeTabs() {
		List<CreativeTabData> tabs = new ArrayList<CreativeTabData>();
		for(CreativeTabs tab : CreativeTabs.creativeTabArray) {
			if(tab == null || !tab.getClass().getName().startsWith("com.hbm.creativetabs.")) continue;
			try {
				String label = safe(tab.getTabLabel());
				if(label.isEmpty()) {
					creativeTabFailures++;
					continue;
				}

				Set<String> itemNames = new LinkedHashSet<String>();
				for(Object object : Item.itemRegistry) {
					if(!(object instanceof Item)) continue;
					Item item = (Item) object;
					try {
						if(item.getCreativeTab() == tab) {
							String name = registryName(item);
							if(!name.isEmpty()) itemNames.add(name);
						}
					} catch(Throwable ex) {
						creativeTabFailures++;
					}
				}

				String translatedLabel;
				try {
					translatedLabel = safe(tab.getTranslatedTabLabel());
				} catch(Throwable ex) {
					creativeTabFailures++;
					translatedLabel = "";
				}

				String iconName;
				try {
					iconName = registryName(tab.getTabIconItem());
				} catch(Throwable ex) {
					creativeTabFailures++;
					iconName = "";
				}

				tabs.add(new CreativeTabData(label, translatedLabel, tab.getClass().getName(),
						iconName, itemNames.size(), sourceCategory(tab.getClass())));
			} catch(Throwable ex) {
				creativeTabFailures++;
			}
		}
		return tabs;
	}

	@Override
	public String getSummary() {
		int total = variantFailures + displayNameFailures + creativeTabFailures;
		if(total == 0) return "";
		return "client enrichment used deterministic empty/fallback values after "
				+ variantFailures + " subtype, " + displayNameFailures + " display-name, and "
				+ creativeTabFailures + " creative-tab failures.";
	}

	private static String registryName(Item item) {
		if(item == null) return "";
		Object name = Item.itemRegistry.getNameForObject(item);
		return name == null ? "" : name.toString();
	}

	private static String sourceCategory(Class<?> type) {
		if(type == null || type.getPackage() == null) return "";
		String packageName = type.getPackage().getName();
		if(packageName.startsWith("com.hbm.")) {
			return packageName.substring("com.hbm.".length());
		}
		return packageName;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
