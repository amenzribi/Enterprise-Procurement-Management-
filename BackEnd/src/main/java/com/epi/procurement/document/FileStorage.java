package com.epi.procurement.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Storage port for attachment content (ADR-003: a local or NAS directory now, S3-compatible storage later). Files
 * are stored under generated keys, never under the uploaded name, outside the web root (S2DR-11).
 */
interface FileStorage {

	String store(byte[] content) throws IOException;

	byte[] read(String key) throws IOException;

	@Component
	class LocalFileStorage implements FileStorage {

		private final Path root;

		LocalFileStorage(@Value("${app.storage.directory:storage/files}") String directory) {
			this.root = Path.of(directory).toAbsolutePath().normalize();
		}

		@Override
		public String store(byte[] content) throws IOException {
			String key = UUID.randomUUID().toString().replace("-", "");
			Path file = path(key);
			Files.createDirectories(file.getParent());
			Files.write(file, content);
			return key;
		}

		@Override
		public byte[] read(String key) throws IOException {
			return Files.readAllBytes(path(key));
		}

		private Path path(String key) {
			if (!key.matches("[0-9a-f]{32}")) {
				throw new IllegalArgumentException("Invalid storage key");
			}
			Path file = root.resolve(key.substring(0, 2)).resolve(key).normalize();
			if (!file.startsWith(root)) {
				throw new IllegalArgumentException("Invalid storage key");
			}
			return file;
		}

	}

}
