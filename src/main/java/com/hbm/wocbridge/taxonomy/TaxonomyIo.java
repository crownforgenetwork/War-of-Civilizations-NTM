package com.hbm.wocbridge.taxonomy;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class TaxonomyIo {

	private TaxonomyIo() { }

	public static String sha256(File file) throws IOException {
		MessageDigest digest = digest();
		try(FileInputStream input = new FileInputStream(file)) {
			byte[] buffer = new byte[8192];
			int read;
			while((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
		}
		return hex(digest.digest());
	}

	public static String sha256(String value) {
		MessageDigest digest = digest();
		digest.update(value.getBytes(StandardCharsets.UTF_8));
		return hex(digest.digest());
	}

	public static String inputChecksum(List<File> files) {
		try {
			List<File> sorted = new ArrayList<File>(files);
			Collections.sort(sorted, new Comparator<File>() {
				@Override
				public int compare(File left, File right) {
					return left.getName().compareTo(right.getName());
				}
			});
			MessageDigest digest = digest();
			for(File file : sorted) {
				digest.update(file.getName().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
				try(FileInputStream input = new FileInputStream(file)) {
					byte[] buffer = new byte[8192];
					int read;
					while((read = input.read(buffer)) >= 0) {
						digest.update(buffer, 0, read);
					}
				}
			}
			return hex(digest.digest());
		} catch(IOException ex) {
			return "unavailable";
		}
	}

	public static void writeAtomic(File destination, String content) throws IOException {
		File parent = destination.getParentFile();
		Files.createDirectories(parent.toPath());
		Path temporary = Files.createTempFile(
				parent.toPath(), "." + destination.getName() + ".", ".tmp");
		boolean moved = false;
		try {
			try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					Files.newOutputStream(temporary), StandardCharsets.UTF_8))) {
				writer.write(content);
			}
			try {
				Files.move(temporary, destination.toPath(),
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch(java.nio.file.AtomicMoveNotSupportedException ex) {
				Files.move(temporary, destination.toPath(),
						StandardCopyOption.REPLACE_EXISTING);
			}
			moved = true;
		} finally {
			if(!moved) Files.deleteIfExists(temporary);
		}
	}

	public static String canonicalJson(JsonElement element) {
		if(element == null || element.isJsonNull()) return "null";
		if(element.isJsonPrimitive()) return element.toString();
		if(element.isJsonArray()) {
			List<String> values = new ArrayList<String>();
			for(JsonElement child : element.getAsJsonArray()) {
				values.add(canonicalJson(child));
			}
			return "[" + join(values, ",") + "]";
		}
		JsonObject object = element.getAsJsonObject();
		List<String> names = new ArrayList<String>();
		for(Map.Entry<String, JsonElement> entry : object.entrySet()) {
			names.add(entry.getKey());
		}
		Collections.sort(names);
		List<String> values = new ArrayList<String>();
		for(String name : names) {
			values.add(quote(name) + ":" + canonicalJson(object.get(name)));
		}
		return "{" + join(values, ",") + "}";
	}

	public static String pretty(JsonElement element) {
		StringBuilder result = new StringBuilder();
		writePretty(element, result, 0);
		result.append('\n');
		return result.toString();
	}

	public static String csv(String value) {
		if(value == null) return "";
		if(value.indexOf(',') < 0 && value.indexOf('"') < 0
				&& value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	public static String readUtf8(File file) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try(FileInputStream input = new FileInputStream(file)) {
			byte[] buffer = new byte[8192];
			int read;
			while((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
		}
		return new String(output.toByteArray(), StandardCharsets.UTF_8);
	}

	private static void writePretty(JsonElement element, StringBuilder output, int depth) {
		if(element == null || element.isJsonNull() || element.isJsonPrimitive()) {
			output.append(element == null ? "null" : element.toString());
			return;
		}
		if(element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			if(array.size() == 0) {
				output.append("[]");
				return;
			}
			output.append("[\n");
			for(int index = 0; index < array.size(); index++) {
				indent(output, depth + 1);
				writePretty(array.get(index), output, depth + 1);
				if(index + 1 < array.size()) output.append(',');
				output.append('\n');
			}
			indent(output, depth);
			output.append(']');
			return;
		}
		JsonObject object = element.getAsJsonObject();
		List<String> names = new ArrayList<String>();
		for(Map.Entry<String, JsonElement> entry : object.entrySet()) {
			names.add(entry.getKey());
		}
		Collections.sort(names);
		if(names.isEmpty()) {
			output.append("{}");
			return;
		}
		output.append("{\n");
		for(int index = 0; index < names.size(); index++) {
			String name = names.get(index);
			indent(output, depth + 1);
			output.append(quote(name)).append(": ");
			writePretty(object.get(name), output, depth + 1);
			if(index + 1 < names.size()) output.append(',');
			output.append('\n');
		}
		indent(output, depth);
		output.append('}');
	}

	private static void indent(StringBuilder output, int depth) {
		for(int index = 0; index < depth; index++) output.append("  ");
	}

	private static String quote(String value) {
		return new com.google.gson.JsonPrimitive(value).toString();
	}

	private static String join(List<String> values, String delimiter) {
		StringBuilder result = new StringBuilder();
		for(int index = 0; index < values.size(); index++) {
			if(index > 0) result.append(delimiter);
			result.append(values.get(index));
		}
		return result.toString();
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch(Exception ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for(byte value : bytes) result.append(String.format("%02x", value & 0xff));
		return result.toString();
	}
}
