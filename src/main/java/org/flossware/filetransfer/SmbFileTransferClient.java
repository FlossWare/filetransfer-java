package org.flossware.filetransfer;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * FileTransferClient implementation for SMB/CIFS (Server Message Block / Common Internet File System).
 *
 * <p>Uses the jcifs-ng library for accessing Windows file shares and Samba servers.
 * Supports domain, workgroup, and standalone server authentication.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Domain authentication
 * FileTransferClient smb = SmbFileTransferClient.builder()
 *     .server("smb://server.example.com/share/")
 *     .domain("DOMAIN")
 *     .username("user")
 *     .password("pass")
 *     .build();
 *
 * // Workgroup authentication
 * FileTransferClient smb = SmbFileTransferClient.builder()
 *     .server("smb://192.168.1.100/Public/")
 *     .username("guest")
 *     .password("")
 *     .build();
 *
 * try {
 *     byte[] data = smb.readFile("documents/report.pdf");
 *     List<String> files = smb.list("documents/");
 * } finally {
 *     smb.close();
 * }
 * }</pre>
 */
public class SmbFileTransferClient implements FileTransferClient {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String server;
    private final String domain;
    private final String username;
    private final String password;
    private final CIFSContext cifsContext;

    private SmbFileTransferClient(String server, String domain, String username, String password) throws IOException {
        Objects.requireNonNull(server, "server cannot be null");
        this.server = server.endsWith("/") ? server : server + "/";
        this.domain = domain;
        this.username = username;
        this.password = password;

        try {
            Properties props = new Properties();
            PropertyConfiguration config = new PropertyConfiguration(props);

            if (username != null && password != null) {
                NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                    domain, username, password
                );
                this.cifsContext = new BaseContext(config).withCredentials(auth);
            } else {
                this.cifsContext = new BaseContext(config);
            }
        } catch (Exception e) {
            throw new IOException("Failed to initialize SMB context", e);
        }
    }

    private String resolveUrl(String path) {
        return server + path;
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String url = resolveUrl(path);

        try (SmbFile smbFile = new SmbFile(url, cifsContext);
             InputStream in = smbFile.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String url = resolveUrl(path);
        SmbFile smbFile = new SmbFile(url, cifsContext);
        return smbFile.getInputStream();
    }

    @Override
    public boolean exists(String path) throws IOException {
        String url = resolveUrl(path);
        try (SmbFile smbFile = new SmbFile(url, cifsContext)) {
            return smbFile.exists() && !smbFile.isDirectory();
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String url = resolveUrl(prefix);
        List<String> results = new ArrayList<>();

        try (SmbFile smbDir = new SmbFile(url, cifsContext)) {
            if (!smbDir.isDirectory()) {
                throw new IOException("Path is not a directory: " + prefix);
            }

            SmbFile[] files = smbDir.listFiles();
            if (files != null) {
                for (SmbFile file : files) {
                    String fileName = file.getName();
                    // Remove trailing slash from directory names
                    if (fileName.endsWith("/")) {
                        fileName = fileName.substring(0, fileName.length() - 1);
                    }
                    results.add(prefix + (prefix.endsWith("/") ? "" : "/") + fileName);
                }
            }
        }

        return results;
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String url = resolveUrl(path);
        try (SmbFile smbFile = new SmbFile(url, cifsContext)) {
            if (!smbFile.exists()) {
                throw new IOException("File not found: " + path);
            }
            if (smbFile.isDirectory()) {
                throw new IOException("Path is a directory: " + path);
            }
            return smbFile.length();
        }
    }

    @Override
    public String getDescription() {
        String userDesc = username != null ? username + "@" : "";
        String domainDesc = domain != null ? domain + "\\" : "";
        return "SMB[" + domainDesc + userDesc + server + "]";
    }

    @Override
    public void close() throws IOException {
        // jcifs-ng doesn't require explicit cleanup
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String server;
        private String domain;
        private String username;
        private String password;

        public Builder server(String server) {
            this.server = server;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Builder credentials(String domain, String username, String password) {
            this.domain = domain;
            this.username = username;
            this.password = password;
            return this;
        }

        public SmbFileTransferClient build() throws IOException {
            Objects.requireNonNull(server, "server must be set");
            return new SmbFileTransferClient(server, domain, username, password);
        }
    }
}
