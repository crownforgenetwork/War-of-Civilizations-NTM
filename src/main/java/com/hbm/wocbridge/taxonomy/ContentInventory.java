package com.hbm.wocbridge.taxonomy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContentInventory {

	public static final class Entry implements Comparable<Entry> {
		private final String canonicalKey;
		private final String contentKind;
		private final String registryName;
		private final int metadata;
		private final String exporterCategory;
		private final String recipeManager;
		private final String machineFamily;
		private final String javaClass;
		private final String sourceClass;
		private final String sourceFile;
		private final Map<String, String> fields;

		public Entry(String canonicalKey, String contentKind, String registryName,
				int metadata, String exporterCategory, String recipeManager,
				String machineFamily, String javaClass, String sourceClass,
				String sourceFile, Map<String, String> fields) {
			this.canonicalKey = value(canonicalKey);
			this.contentKind = value(contentKind);
			this.registryName = value(registryName);
			this.metadata = metadata;
			this.exporterCategory = value(exporterCategory);
			this.recipeManager = value(recipeManager);
			this.machineFamily = value(machineFamily);
			this.javaClass = value(javaClass);
			this.sourceClass = value(sourceClass);
			this.sourceFile = value(sourceFile);
			this.fields = Collections.unmodifiableMap(
					new LinkedHashMap<String, String>(fields));
		}

		public String getCanonicalKey() {
			return canonicalKey;
		}

		public String getContentKind() {
			return contentKind;
		}

		public String getRegistryName() {
			return registryName;
		}

		public String getNamespace() {
			int separator = registryName.indexOf(':');
			return separator > 0 ? registryName.substring(0, separator) : "";
		}

		public int getMetadata() {
			return metadata;
		}

		public String getExporterCategory() {
			return exporterCategory;
		}

		public String getRecipeManager() {
			return recipeManager;
		}

		public String getMachineFamily() {
			return machineFamily;
		}

		public String getJavaClass() {
			return javaClass;
		}

		public String getSourceClass() {
			return sourceClass;
		}

		public String getSourceFile() {
			return sourceFile;
		}

		public String getField(String name) {
			String result = fields.get(name);
			return result == null ? "" : result;
		}

		@Override
		public int compareTo(Entry other) {
			return canonicalKey.compareTo(other.canonicalKey);
		}

		private static String value(String source) {
			return source == null ? "" : source;
		}
	}

	private final List<Entry> entries;
	private final Map<String, Entry> entryIndex;
	private final Map<String, Integer> sourceCounts;
	private final String checksum;
	private final List<String> warnings;

	public ContentInventory(List<Entry> entries, Map<String, Integer> sourceCounts,
			String checksum, List<String> warnings) {
		List<Entry> sorted = new ArrayList<Entry>(entries);
		Collections.sort(sorted);
		this.entries = Collections.unmodifiableList(sorted);
		Map<String, Entry> index = new LinkedHashMap<String, Entry>();
		for(Entry entry : sorted) index.put(entry.getCanonicalKey(), entry);
		this.entryIndex = Collections.unmodifiableMap(index);
		this.sourceCounts = Collections.unmodifiableMap(
				new LinkedHashMap<String, Integer>(sourceCounts));
		this.checksum = checksum == null ? "" : checksum;
		List<String> sortedWarnings = new ArrayList<String>(warnings);
		Collections.sort(sortedWarnings);
		this.warnings = Collections.unmodifiableList(sortedWarnings);
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public Entry get(String canonicalKey) {
		return entryIndex.get(canonicalKey);
	}

	public int size() {
		return entries.size();
	}

	public Map<String, Integer> getSourceCounts() {
		return sourceCounts;
	}

	public String getChecksum() {
		return checksum;
	}

	public List<String> getWarnings() {
		return warnings;
	}
}
