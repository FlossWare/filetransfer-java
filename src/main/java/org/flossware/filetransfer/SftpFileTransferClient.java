package org.flossware.filetransfer;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Vector;

/**
 * FileTransferClient implementation for SFTP (SSH File Transfer Protocol).
 *
 * <p>Supports both password and private key authentication. Connection is established
 * on first use and maintained until close() is called.</p>
 *
 * <h2>Connection Management</h2>
 * <p>This implementation reuses a single SFTP connection across multiple operations
 * for efficiency. Connection establishment is synchronized.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Password authentication
 * FileTransferClient sftp = SftpFileTransferClient.builder()
 *     .host("sftp.example.com")
 *     .port(22)
 *     .username("user")
 *     .password("secret")
 *     .basePath("/remote/files")
 *     .build();
 *
 * // Private key authentication
 * FileTransferClient sftp = SftpFileTransferClient.builder()
 *     .host("sftp.example.com")
 *     .username("deploy")
 *     .privateKey("/home/user/.ssh/id_rsa")
 *     .basePath("/app/files")
 *     .build();
 *
 * try {
 *     byte[] data = sftp.readFile("document.pdf");
 *     List<String> files = sftp.list("uploads/");
 * } finally {
 *     sftp.close();
 * }
 * }</pre>
 */
public class SftpFileTransferClient implements FileTransferClient {
    private static final int DEFAULT_SESSION_TIMEOUT_MS = 30000;
    private static final int DEFAULT_CHANNEL_TIMEOUT_MS = 10000;
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKeyPath;
    private final String basePath;
    private final String knownHostsFile;
    private final boolean strictHostKeyChecking;

    private JSch jsch;
    private Session session;
    private ChannelSftp sftpChannel;

    private SftpFileTransferClient(String host, int port, String username, String password,
                                    String privateKeyPath, String basePath, String knownHostsFile,
                                    boolean strictHostKeyChecking) {
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.port = port > 0 ? port : 22;
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.password = password;
        this.privateKeyPath = privateKeyPath;
        this.basePath = basePath != null ? basePath : "/";
        this.knownHostsFile = knownHostsFile;
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    private synchronized void ensureConnected() throws IOException {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            return;
        }

        try {
            jsch = new JSch();

            if (knownHostsFile != null) {
                jsch.setKnownHosts(knownHostsFile);
            }

            if (privateKeyPath != null) {
                jsch.addIdentity(privateKeyPath);
            }

            session = jsch.getSession(username, host, port);

            if (password != null) {
                session.setPassword(password);
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", strictHostKeyChecking ? "yes" : "no");
            session.setConfig(config);
            session.connect(DEFAULT_SESSION_TIMEOUT_MS);

            Channel channel = session.openChannel("sftp");
            channel.connect(DEFAULT_CHANNEL_TIMEOUT_MS);
            sftpChannel = (ChannelSftp) channel;

        } catch (JSchException e) {
            throw new IOException("Failed to connect to SFTP server: " + host, e);
        }
    }

    private String resolvePath(String path) {
        if (basePath.endsWith("/")) {
            return basePath + path;
        }
        return basePath + "/" + path;
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        ensureConnected();

        String fullPath = resolvePath(path);

        try (InputStream in = sftpChannel.get(fullPath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();

        } catch (SftpException e) {
            throw new IOException("Failed to read file from SFTP: " + fullPath, e);
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        ensureConnected();

        String fullPath = resolvePath(path);

        try {
            return sftpChannel.get(fullPath);
        } catch (SftpException e) {
            throw new IOException("Failed to open file from SFTP: " + fullPath, e);
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        ensureConnected();

        String fullPath = resolvePath(path);

        try {
            SftpATTRS attrs = sftpChannel.stat(fullPath);
            return attrs != null && !attrs.isDir();
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw new IOException("Failed to check file existence: " + fullPath, e);
        }
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        ensureConnected();

        String fullPath = resolvePath(prefix);
        List<String> results = new ArrayList<>();

        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftpChannel.ls(fullPath);

            for (ChannelSftp.LsEntry entry : entries) {
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    results.add(prefix + (prefix.endsWith("/") ? "" : "/") + entry.getFilename());
                }
            }

            return results;

        } catch (SftpException e) {
            throw new IOException("Failed to list files from SFTP: " + fullPath, e);
        }
    }

    @Override
    public long getFileSize(String path) throws IOException {
        ensureConnected();

        String fullPath = resolvePath(path);

        try {
            SftpATTRS attrs = sftpChannel.stat(fullPath);
            if (attrs == null || attrs.isDir()) {
                throw new IOException("Path is not a file: " + fullPath);
            }
            return attrs.getSize();
        } catch (SftpException e) {
            throw new IOException("Failed to get file size: " + fullPath, e);
        }
    }

    @Override
    public String getDescription() {
        return "SFTP[" + username + "@" + host + ":" + port + basePath + "]";
    }

    @Override
    public void close() throws IOException {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private int port = 22;
        private String username;
        private String password;
        private String privateKeyPath;
        private String basePath = "/";
        private String knownHostsFile;
        private boolean strictHostKeyChecking = true;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
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

        public Builder privateKey(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder knownHostsFile(String knownHostsFile) {
            this.knownHostsFile = knownHostsFile;
            return this;
        }

        public Builder strictHostKeyChecking(boolean strict) {
            this.strictHostKeyChecking = strict;
            return this;
        }

        public SftpFileTransferClient build() {
            Objects.requireNonNull(host, "host must be set");
            Objects.requireNonNull(username, "username must be set");
            if (password == null && privateKeyPath == null) {
                throw new IllegalStateException("Either password or privateKey must be set");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            return new SftpFileTransferClient(host, port, username, password, privateKeyPath,
                                              basePath, knownHostsFile, strictHostKeyChecking);
        }
    }
}
