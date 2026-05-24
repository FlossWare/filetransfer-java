package org.flossware.filetransfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * FileTransferClient implementation for FTP and FTPS (FTP over SSL/TLS).
 *
 * <p>Uses Java's built-in FTP URL handler. Supports both anonymous and authenticated access.
 * For FTPS, use ftps:// protocol in the server URL.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // FTP with authentication
 * FileTransferClient ftp = FtpFileTransferClient.builder()
 *     .server("ftp://ftp.example.com/files/")
 *     .username("user")
 *     .password("pass")
 *     .build();
 *
 * // FTPS (FTP over SSL/TLS)
 * FileTransferClient ftps = FtpFileTransferClient.builder()
 *     .server("ftps://secure.example.com/files/")
 *     .username("user")
 *     .password("pass")
 *     .build();
 *
 * // Anonymous FTP
 * FileTransferClient ftp = FtpFileTransferClient.builder()
 *     .server("ftp://public.example.com/downloads/")
 *     .build();
 *
 * try {
 *     byte[] data = ftp.readFile("document.pdf");
 * } finally {
 *     ftp.close();
 * }
 * }</pre>
 */
public class FtpFileTransferClient implements FileTransferClient {
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String server;
    private final String username;
    private final String password;

    private FtpFileTransferClient(String server, String username, String password) {
        Objects.requireNonNull(server, "server cannot be null");

        if (!server.startsWith("ftp://") && !server.startsWith("ftps://")) {
            throw new IllegalArgumentException("server must start with ftp:// or ftps://");
        }

        this.server = server.endsWith("/") ? server : server + "/";
        this.username = username;
        this.password = password;
    }

    private URL buildUrl(String path) throws IOException {
        if (username != null && password != null) {
            String protocol = server.startsWith("ftps://") ? "ftps://" : "ftp://";
            String hostAndPath = server.substring(protocol.length());
            return new URL(protocol + username + ":" + password + "@" + hostAndPath + path);
        } else {
            return new URL(server + path);
        }
    }

    private void configureConnection(URLConnection connection) {
        connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        URL url = buildUrl(path);
        URLConnection connection = url.openConnection();
        configureConnection(connection);

        try (InputStream in = connection.getInputStream();
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
        URL url = buildUrl(path);
        URLConnection connection = url.openConnection();
        configureConnection(connection);
        return connection.getInputStream();
    }

    @Override
    public boolean exists(String path) throws IOException {
        try {
            URL url = buildUrl(path);
            URLConnection connection = url.openConnection();
            configureConnection(connection);

            try (InputStream in = connection.getInputStream()) {
                return in.read() != -1;
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        // FTP protocol doesn't support directory listing via Java URL API
        // Users should use a full FTP client library (Apache Commons Net) for advanced features
        throw new UnsupportedOperationException(
            "FTP directory listing not supported via Java URL API. " +
            "Use SFTP, WebDAV, or SMB for directory listing support."
        );
    }

    @Override
    public long getFileSize(String path) throws IOException {
        URL url = buildUrl(path);
        URLConnection connection = url.openConnection();
        configureConnection(connection);

        long size = connection.getContentLengthLong();
        if (size < 0) {
            throw new IOException("Could not determine file size for: " + path);
        }

        return size;
    }

    @Override
    public String getDescription() {
        return "FTP[" + server + ", authenticated=" + (username != null) + "]";
    }

    @Override
    public void close() throws IOException {
        // No resources to clean up
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String server;
        private String username;
        private String password;

        public Builder server(String server) {
            this.server = server;
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

        public FtpFileTransferClient build() {
            Objects.requireNonNull(server, "server must be set");
            return new FtpFileTransferClient(server, username, password);
        }
    }
}
