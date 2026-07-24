package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContentTaxonomySelector {

	private final String selectorId;
	private final List<String> canonicalKeys;
	private final List<String> registryNames;
	private final String namespace;
	private final String contentKind;
	private final String registryName;
	private final String registryNamePrefix;
	private final String registryNameSuffix;
	private final Integer metadata;
	private final Integer metadataMin;
	private final Integer metadataMax;
	private final String exporterCategory;
	private final String recipeManager;
	private final String machineFamily;
	private final String javaClass;
	private final String javaClassPrefix;
	private final String sourceClass;
	private final String sourceClassPrefix;

	public ContentTaxonomySelector(String selectorId, List<String> canonicalKeys,
			List<String> registryNames, String namespace, String contentKind, String registryName,
			String registryNamePrefix, String registryNameSuffix, Integer metadata,
			Integer metadataMin, Integer metadataMax, String exporterCategory,
			String recipeManager, String machineFamily, String javaClass,
			String javaClassPrefix, String sourceClass, String sourceClassPrefix) {
		this.selectorId = value(selectorId);
		this.canonicalKeys = Collections.unmodifiableList(
				new ArrayList<String>(canonicalKeys));
		this.registryNames = Collections.unmodifiableList(
				new ArrayList<String>(registryNames));
		this.namespace = value(namespace);
		this.contentKind = value(contentKind);
		this.registryName = value(registryName);
		this.registryNamePrefix = value(registryNamePrefix);
		this.registryNameSuffix = value(registryNameSuffix);
		this.metadata = metadata;
		this.metadataMin = metadataMin;
		this.metadataMax = metadataMax;
		this.exporterCategory = value(exporterCategory);
		this.recipeManager = value(recipeManager);
		this.machineFamily = value(machineFamily);
		this.javaClass = value(javaClass);
		this.javaClassPrefix = value(javaClassPrefix);
		this.sourceClass = value(sourceClass);
		this.sourceClassPrefix = value(sourceClassPrefix);
	}

	public boolean matches(ContentInventory.Entry entry) {
		if(!canonicalKeys.isEmpty() && !canonicalKeys.contains(entry.getCanonicalKey())) return false;
		if(!registryNames.isEmpty() && !registryNames.contains(entry.getRegistryName())) return false;
		if(!namespace.isEmpty() && !namespace.equals(entry.getNamespace())) return false;
		if(!contentKind.isEmpty() && !contentKind.equals(entry.getContentKind())) return false;
		if(!registryName.isEmpty() && !registryName.equals(entry.getRegistryName())) return false;
		if(!registryNamePrefix.isEmpty()
				&& !entry.getRegistryName().startsWith(registryNamePrefix)) return false;
		if(!registryNameSuffix.isEmpty()
				&& !entry.getRegistryName().endsWith(registryNameSuffix)) return false;
		if(metadata != null && metadata.intValue() != entry.getMetadata()) return false;
		if(metadataMin != null && entry.getMetadata() < metadataMin.intValue()) return false;
		if(metadataMax != null && entry.getMetadata() > metadataMax.intValue()) return false;
		if(!exporterCategory.isEmpty()
				&& !exporterCategory.equals(entry.getExporterCategory())) return false;
		if(!recipeManager.isEmpty()
				&& !recipeManager.equals(entry.getRecipeManager())) return false;
		if(!machineFamily.isEmpty()
				&& !machineFamily.equals(entry.getMachineFamily())) return false;
		if(!javaClass.isEmpty() && !javaClass.equals(entry.getJavaClass())) return false;
		if(!javaClassPrefix.isEmpty()
				&& !entry.getJavaClass().startsWith(javaClassPrefix)) return false;
		if(!sourceClass.isEmpty() && !sourceClass.equals(entry.getSourceClass())) return false;
		if(!sourceClassPrefix.isEmpty()
				&& !entry.getSourceClass().startsWith(sourceClassPrefix)) return false;
		return predicateCount() > 0;
	}

	public int getPrecedence() {
		if(!canonicalKeys.isEmpty()) return 4;
		return predicateCount() >= 2 ? 3 : 2;
	}

	public int predicateCount() {
		int count = 0;
		if(!canonicalKeys.isEmpty()) count++;
		if(!registryNames.isEmpty()) count++;
		if(!namespace.isEmpty()) count++;
		if(!contentKind.isEmpty()) count++;
		if(!registryName.isEmpty()) count++;
		if(!registryNamePrefix.isEmpty()) count++;
		if(!registryNameSuffix.isEmpty()) count++;
		if(metadata != null) count++;
		if(metadataMin != null) count++;
		if(metadataMax != null) count++;
		if(!exporterCategory.isEmpty()) count++;
		if(!recipeManager.isEmpty()) count++;
		if(!machineFamily.isEmpty()) count++;
		if(!javaClass.isEmpty()) count++;
		if(!javaClassPrefix.isEmpty()) count++;
		if(!sourceClass.isEmpty()) count++;
		if(!sourceClassPrefix.isEmpty()) count++;
		return count;
	}

	public boolean isExplicitMembership() {
		return !canonicalKeys.isEmpty() || !registryNames.isEmpty();
	}

	public String getSelectorId() {
		return selectorId;
	}

	public List<String> getCanonicalKeys() {
		return canonicalKeys;
	}

	public List<String> getRegistryNames() {
		return registryNames;
	}

	public String getContentKind() {
		return contentKind;
	}

	public Integer getMetadata() {
		return metadata;
	}

	public Integer getMetadataMin() {
		return metadataMin;
	}

	public Integer getMetadataMax() {
		return metadataMax;
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}
}
