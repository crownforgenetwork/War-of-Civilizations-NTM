package com.hbm.wocbridge.taxonomy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ContentInventoryLoader {

	private static final class Source {
		private final String fileName;
		private final String kind;
		private final String registryColumn;

		private Source(String fileName, String kind, String registryColumn) {
			this.fileName = fileName;
			this.kind = kind;
			this.registryColumn = registryColumn;
		}
	}

	private static final List<Source> SOURCES = Arrays.asList(
			new Source("blocks.csv", "BLOCK", "registry_name"),
			new Source("crafting_recipes.csv", "CRAFTING_RECIPE", "output_registry_name"),
			new Source("creative_tabs.csv", "CREATIVE_TAB", "tab_label"),
			new Source("entities.csv", "ENTITY", "registry_name"),
			new Source("fluids.csv", "FLUID", "fluid_name"),
			new Source("items.csv", "ITEM", "registry_name"),
			new Source("machine_recipes.csv", "MACHINE_RECIPE", "output_registry_name"),
			new Source("smelting_recipes.csv", "SMELTING_RECIPE", "output_registry_name"),
			new Source("structure_loot.csv", "STRUCTURE_LOOT", "item_registry_name"),
			new Source("worldgen_features.csv", "WORLDGEN_FEATURE", "feature_name"));

	public ContentInventory load(File directory) {
		List<ContentInventory.Entry> entries =
				new ArrayList<ContentInventory.Entry>();
		Map<String, Integer> counts = new TreeMap<String, Integer>();
		List<String> warnings = new ArrayList<String>();
		List<File> inputs = new ArrayList<File>();
		for(Source source : SOURCES) {
			File file = new File(directory, source.fileName);
			if(!file.isFile()) {
				warnings.add("MISSING_EXPORT: " + source.fileName);
				counts.put(source.fileName, Integer.valueOf(0));
				continue;
			}
			inputs.add(file);
			int count = read(source, file, entries, warnings);
			counts.put(source.fileName, Integer.valueOf(count));
		}
		return new ContentInventory(entries, counts,
				TaxonomyIo.inputChecksum(inputs), warnings);
	}

	private static int read(Source source, File file,
			List<ContentInventory.Entry> entries, List<String> warnings) {
		int count = 0;
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8))) {
			String headerLine = reader.readLine();
			if(headerLine == null) {
				warnings.add("EMPTY_EXPORT: " + source.fileName);
				return 0;
			}
			List<String> headers = parseCsv(headerLine);
			String line;
			int lineNumber = 1;
			while((line = reader.readLine()) != null) {
				lineNumber++;
				if(line.isEmpty()) continue;
				List<String> values = parseCsv(line);
				if(values.size() != headers.size()) {
					warnings.add("MALFORMED_CSV_ROW: " + source.fileName + ":"
							+ lineNumber + " columns=" + values.size()
							+ " expected=" + headers.size());
					continue;
				}
				Map<String, String> fields = new LinkedHashMap<String, String>();
				for(int index = 0; index < headers.size(); index++) {
					fields.put(headers.get(index), values.get(index));
				}
				String canonicalKey = value(fields, "content_key");
				if(canonicalKey.isEmpty()) {
					warnings.add("MISSING_CONTENT_KEY: " + source.fileName
							+ ":" + lineNumber);
					continue;
				}
				String registryName = value(fields, source.registryColumn);
				int metadata = parseMetadata(fields);
				entries.add(new ContentInventory.Entry(
						canonicalKey, source.kind, registryName, metadata,
						value(fields, "source_category"),
						value(fields, "recipe_manager"),
						value(fields, "machine_family"),
						first(fields, "java_class", "recipe_class"),
						value(fields, "source_class"), source.fileName, fields));
				count++;
			}
		} catch(Exception ex) {
			warnings.add("EXPORT_READ_FAILED: " + source.fileName + ": "
					+ conciseMessage(ex));
		}
		return count;
	}

	private static int parseMetadata(Map<String, String> fields) {
		String value = first(fields, "metadata", "output_metadata");
		if(value.isEmpty()) return -1;
		try {
			return Integer.parseInt(value);
		} catch(NumberFormatException ex) {
			return -1;
		}
	}

	static List<String> parseCsv(String line) {
		List<String> values = new ArrayList<String>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for(int index = 0; index < line.length(); index++) {
			char current = line.charAt(index);
			if(quoted) {
				if(current == '"') {
					if(index + 1 < line.length() && line.charAt(index + 1) == '"') {
						value.append('"');
						index++;
					} else {
						quoted = false;
					}
				} else {
					value.append(current);
				}
			} else if(current == ',') {
				values.add(value.toString());
				value.setLength(0);
			} else if(current == '"') {
				quoted = true;
			} else {
				value.append(current);
			}
		}
		values.add(value.toString());
		return values;
	}

	private static String first(Map<String, String> fields, String... names) {
		for(String name : names) {
			String result = value(fields, name);
			if(!result.isEmpty()) return result;
		}
		return "";
	}

	private static String value(Map<String, String> fields, String name) {
		String result = fields.get(name);
		return result == null ? "" : result;
	}

	private static String conciseMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null || message.isEmpty()
				? exception.getClass().getSimpleName() : message;
	}

	public static List<String> sourceFileNames() {
		List<String> names = new ArrayList<String>();
		for(Source source : SOURCES) names.add(source.fileName);
		Collections.sort(names);
		return names;
	}
}
