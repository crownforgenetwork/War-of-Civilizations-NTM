package com.hbm.wocbridge.content;

import java.util.Objects;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class ContentKey implements Comparable<ContentKey> {

	private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

	private final ContentKind kind;
	private final String value;
	private final String registryName;
	private final int metadata;

	private ContentKey(ContentKind kind, String value, String registryName, int metadata) {
		this.kind = kind;
		this.value = value;
		this.registryName = registryName;
		this.metadata = metadata;
	}

	public static ContentKey parse(String value, ContentKind kind) {
		if(kind == null) throw new IllegalArgumentException("Content kind is missing");
		if(value == null || value.isEmpty()) throw new IllegalArgumentException("Content key is missing");
		if(!value.equals(value.trim()) || containsWhitespaceOrControl(value)) {
			throw new IllegalArgumentException("Content key contains whitespace or control characters");
		}
		if(!kind.matchesKey(value)) {
			throw new IllegalArgumentException("Key does not use the " + kind.getKeyPrefix() + " prefix");
		}

		String body = value.substring(kind.getKeyPrefix().length());
		if(body.isEmpty()) throw new IllegalArgumentException("Content key has an empty identifier");

		switch(kind) {
		case ITEM:
		case BLOCK:
			return parseRegistryMetadata(value, kind, body, true);
		case ENTITY:
			return parseRegistryMetadata(value, kind, body, false);
		case CRAFTING_RECIPE:
		case SMELTING_RECIPE:
		case STRUCTURE_LOOT:
		case LOOT_ENTRY:
			validateEmbeddedMetadata(validateHashedOccurrence(body));
			break;
		case MACHINE_RECIPE:
			validateHashedOccurrence(body);
			break;
		default:
			break;
		}
		return new ContentKey(kind, value, body, -1);
	}

	public static ContentKey parse(String value) {
		ContentKind kind = ContentKind.inferFromKey(value);
		if(kind == null) throw new IllegalArgumentException("Unknown content key prefix");
		return parse(value, kind);
	}

	public static ContentKey fromItemStack(ItemStack stack) {
		if(stack == null || stack.getItem() == null) {
			throw new IllegalArgumentException("Item stack is empty");
		}
		Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
		if(registryName == null) throw new IllegalArgumentException("Item is not registered");
		return parse("item:" + registryName + "#" + stack.getItemDamage(), ContentKind.ITEM);
	}

	public static ContentKey fromBlock(Block block, int metadata) {
		if(block == null) throw new IllegalArgumentException("Block is missing");
		Object registryName = Block.blockRegistry.getNameForObject(block);
		if(registryName == null) throw new IllegalArgumentException("Block is not registered");
		return parse("block:" + registryName + "#" + metadata, ContentKind.BLOCK);
	}

	private static ContentKey parseRegistryMetadata(String value, ContentKind kind, String body,
			boolean requireNamespace) {
		int separator = body.lastIndexOf('#');
		if(separator <= 0 || separator == body.length() - 1) {
			throw new IllegalArgumentException("Key must end with #<metadata>");
		}
		String registryName = body.substring(0, separator);
		if(requireNamespace) {
			int namespace = registryName.indexOf(':');
			if(namespace <= 0 || namespace == registryName.length() - 1) {
				throw new IllegalArgumentException("Registry name must include a namespace");
			}
		}
		int metadata;
		try {
			metadata = Integer.parseInt(body.substring(separator + 1));
		} catch(NumberFormatException ex) {
			throw new IllegalArgumentException("Metadata must be an integer");
		}
		if(metadata < 0 || metadata > 32767) {
			throw new IllegalArgumentException("Metadata must be between 0 and 32767");
		}
		return new ContentKey(kind, value, registryName, metadata);
	}

	private static String validateHashedOccurrence(String body) {
		int occurrenceSeparator = body.lastIndexOf(':');
		int hashSeparator = occurrenceSeparator < 0 ? -1 : body.lastIndexOf(':', occurrenceSeparator - 1);
		if(hashSeparator <= 0 || occurrenceSeparator <= hashSeparator + 1
				|| occurrenceSeparator == body.length() - 1) {
			throw new IllegalArgumentException("Recipe or loot key must end with :<sha256>:<occurrence>");
		}
		String hash = body.substring(hashSeparator + 1, occurrenceSeparator);
		if(!HASH.matcher(hash).matches()) {
			throw new IllegalArgumentException("Recipe or loot key has an invalid SHA-256 segment");
		}
		try {
			if(Integer.parseInt(body.substring(occurrenceSeparator + 1)) < 1) {
				throw new IllegalArgumentException("Occurrence must be positive");
			}
		} catch(NumberFormatException ex) {
			throw new IllegalArgumentException("Occurrence must be a positive integer");
		}
		return body.substring(0, hashSeparator);
	}

	private static void validateEmbeddedMetadata(String body) {
		int separator = body.lastIndexOf('#');
		if(separator <= 0 || separator == body.length() - 1) {
			throw new IllegalArgumentException("Recipe or loot identifier must include #<metadata>");
		}
		try {
			int metadata = Integer.parseInt(body.substring(separator + 1));
			if(metadata < 0 || metadata > 32767) {
				throw new IllegalArgumentException("Metadata must be between 0 and 32767");
			}
		} catch(NumberFormatException ex) {
			throw new IllegalArgumentException("Metadata must be an integer");
		}
	}

	private static boolean containsWhitespaceOrControl(String value) {
		for(int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if(Character.isWhitespace(current) || Character.isISOControl(current)) return true;
		}
		return false;
	}

	public ContentKind getKind() {
		return kind;
	}

	public String getValue() {
		return value;
	}

	public String getRegistryName() {
		return registryName;
	}

	public int getMetadata() {
		return metadata;
	}

	@Override
	public int compareTo(ContentKey other) {
		int comparison = value.compareTo(other.value);
		if(comparison != 0) return comparison;
		return kind.name().compareTo(other.kind.name());
	}

	@Override
	public boolean equals(Object object) {
		if(this == object) return true;
		if(!(object instanceof ContentKey)) return false;
		ContentKey other = (ContentKey) object;
		return kind == other.kind && value.equals(other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(kind, value);
	}

	@Override
	public String toString() {
		return value;
	}
}
