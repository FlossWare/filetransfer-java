package org.flossware.filetransfer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Universal interface for file transfer operations across different protocols.
 *
 * <p>Provides a unified API for reading files from SFTP, WebDAV, SMB/CIFS, FTP, and FTPS servers.
 * All implementations are thread-safe for concurrent read operations.</p>
 *
 * <h2>Supported Protocols</h2>
 * <ul>
 *   <li>SFTP (SSH File Transfer Protocol)</li>
 *   <li>WebDAV (Web Distributed Authoring and Versioning)</li>
 *   <li>SMB/CIFS (Server Message Block / Common Internet File System)</li>
 *   <li>FTP (File Transfer Protocol)</li>
 *   <li>FTPS (FTP over SSL/TLS)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // SFTP
 * FileTransferClient sftp = SftpFileTransferClient.builder()
 *     .host("sftp.example.com")
 *     .port(22)
 *     .username("user")
 *     .password("pass")
 *     .basePath("/remote/files")
 *     .build();
 *
 * byte[] data = sftp.readFile("document.pdf");
 * sftp.close();
 * }</pre>
 *
 * @see SftpFileTransferClient
 * @see WebDavFileTransferClient
 * @see SmbFileTransferClient
 * @see FtpFileTransferClient
 */
public interface FileTransferClient extends Closeable {

    /**
     * Reads a file from the remote server.
     *
     * @param path The relative path to the file (relative to basePath if configured)
     * @return The file contents as a byte array
     * @throws IOException If the file cannot be read or does not exist
     */
    byte[] readFile(String path) throws IOException;

    /**
     * Opens a file as an input stream.
     *
     * <p>Caller is responsible for closing the returned stream.</p>
     *
     * @param path The relative path to the file
     * @return An input stream for reading the file
     * @throws IOException If the file cannot be opened
     */
    InputStream openFile(String path) throws IOException;

    /**
     * Checks if a file exists on the remote server.
     *
     * @param path The relative path to the file
     * @return true if the file exists, false otherwise
     * @throws IOException If the existence check fails
     */
    boolean exists(String path) throws IOException;

    /**
     * Lists files with the given prefix.
     *
     * @param prefix The directory or file prefix to list (empty string for all files)
     * @return A list of file paths
     * @throws IOException If the listing operation fails
     */
    List<String> list(String prefix) throws IOException;

    /**
     * Gets the size of a file in bytes.
     *
     * @param path The relative path to the file
     * @return The file size in bytes
     * @throws IOException If the file does not exist or size cannot be determined
     */
    long getFileSize(String path) throws IOException;

    /**
     * Gets a human-readable description of this file transfer client.
     *
     * @return A description string (e.g., "SFTP[sftp.example.com:22/remote/files]")
     */
    String getDescription();

    /**
     * Closes the file transfer client and releases all resources.
     *
     * @throws IOException If an error occurs during cleanup
     */
    @Override
    void close() throws IOException;
}
