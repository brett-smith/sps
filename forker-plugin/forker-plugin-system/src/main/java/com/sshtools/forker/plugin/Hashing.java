package com.sshtools.forker.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hashing {
	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static String hash(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.update(text.getBytes());
			return bytesToHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Could not hash.", e);
		}
	}

	public static String sha1(File file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");

			try (InputStream fis = new FileInputStream(file)) {
				int n = 0;
				byte[] buffer = new byte[8192];
				while (n != -1) {
					n = fis.read(buffer);
					if (n > 0) {
						digest.update(buffer, 0, n);
					}
				}
				return bytesToHex(digest.digest());
			}
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("Could not hash.", e);
		}
	}
}
