package org.flossware.filetransfer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for SftpFileTransferClient to achieve 100% coverage.
 * Note: Most methods require JSch connection which needs a live SFTP server or complex mocking.
 * These tests focus on builder validation, configuration, and path resolution logic.
 */
class SftpFileTransferClientTest {

    private SftpFileTransferClient client;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore close errors in tearDown
            }
        }
    }

    @Test
    @DisplayName("Should support builder chaining")
    void testBuilderChaining() {
        SftpFileTransferClient.Builder builder = SftpFileTransferClient.builder();
        assertSame(builder, builder.host("example.com"));
        assertSame(builder, builder.port(22));
        assertSame(builder, builder.username("user"));
        assertSame(builder, builder.password("pass"));
        assertSame(builder, builder.privateKey("/path/to/key"));
        assertSame(builder, builder.basePath("/base"));
        assertSame(builder, builder.knownHostsFile("/path/to/known_hosts"));
        assertSame(builder, builder.strictHostKeyChecking(true));
    }

    @Test
    @DisplayName("Should throw NullPointerException when host is null")
    void testBuilderNullHost() {
        assertThrows(NullPointerException.class,
            () -> SftpFileTransferClient.builder()
                .username("user")
                .password("pass")
                .build());
    }

    @Test
    @DisplayName("Should throw NullPointerException when username is null")
    void testBuilderNullUsername() {
        assertThrows(NullPointerException.class,
            () -> SftpFileTransferClient.builder()
                .host("example.com")
                .password("pass")
                .build());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when both password and privateKey are null")
    void testBuilderMissingAuth() {
        assertThrows(IllegalStateException.class,
            () -> SftpFileTransferClient.builder()
                .host("example.com")
                .username("user")
                .build());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for port less than 1")
    void testBuilderInvalidPortTooLow() {
        assertThrows(IllegalArgumentException.class,
            () -> SftpFileTransferClient.builder()
                .host("example.com")
                .port(0)
                .username("user")
                .password("pass")
                .build());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for port greater than 65535")
    void testBuilderInvalidPortTooHigh() {
        assertThrows(IllegalArgumentException.class,
            () -> SftpFileTransferClient.builder()
                .host("example.com")
                .port(65536)
                .username("user")
                .password("pass")
                .build());
    }

    @Test
    @DisplayName("Should build client with password authentication")
    void testBuildWithPassword() throws Exception {
        client = createTestClient("pass", null, null);
        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("SFTP["));
        assertTrue(description.contains("user@example.com:22"));
    }

    @Test
    @DisplayName("Should build client with private key authentication")
    void testBuildWithPrivateKey() throws Exception {
        client = createTestClient(null, "/path/to/key", null);
        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("SFTP["));
        assertTrue(description.contains("user@example.com:22"));
    }

    @Test
    @DisplayName("Should build client with base path")
    void testBuildWithBasePath() throws Exception {
        client = createTestClient("pass", null, "/base");
        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("/base]"));
    }

    @Test
    @DisplayName("Should build client with default port 22")
    void testBuildWithDefaultPort() throws Exception {
        client = SftpFileTransferClient.builder()
            .host("example.com")
            .username("user")
            .password("pass")
            .build();

        assertNotNull(client);
        assertTrue(client.getDescription().contains(":22"));
    }

    @Test
    @DisplayName("Should build client with custom port")
    void testBuildWithCustomPort() throws Exception {
        client = createTestClientWithPort(2222);
        assertNotNull(client);
        assertTrue(client.getDescription().contains(":2222"));
    }

    @Test
    @DisplayName("Should resolve path without base path")
    void testResolvePathWithoutBasePath() throws Exception {
        client = createTestClient("pass", null, "");

        Method resolvePath = SftpFileTransferClient.class.getDeclaredMethod("resolvePath", String.class);
        resolvePath.setAccessible(true);

        String result = (String) resolvePath.invoke(client, "test.txt");
        assertEquals("/test.txt", result);
    }

    @Test
    @DisplayName("Should resolve path with base path")
    void testResolvePathWithBasePath() throws Exception {
        client = createTestClient("pass", null, "/base");

        Method resolvePath = SftpFileTransferClient.class.getDeclaredMethod("resolvePath", String.class);
        resolvePath.setAccessible(true);

        String result = (String) resolvePath.invoke(client, "test.txt");
        assertEquals("/base/test.txt", result);
    }

    @Test
    @DisplayName("Should resolve path with trailing slash in base path")
    void testResolvePathTrailingSlash() throws Exception {
        client = createTestClient("pass", null, "/base/");

        Method resolvePath = SftpFileTransferClient.class.getDeclaredMethod("resolvePath", String.class);
        resolvePath.setAccessible(true);

        String result = (String) resolvePath.invoke(client, "test.txt");
        assertEquals("/base/test.txt", result);
    }

    @Test
    @DisplayName("Should resolve path with multiple slashes")
    void testResolvePathMultipleSlashes() throws Exception {
        client = createTestClient("pass", null, "/base/");

        Method resolvePath = SftpFileTransferClient.class.getDeclaredMethod("resolvePath", String.class);
        resolvePath.setAccessible(true);

        String result = (String) resolvePath.invoke(client, "/test.txt");
        // Should handle leading slash in filename
        assertTrue(result.equals("/base//test.txt") || result.equals("/base/test.txt"));
    }

    @Test
    @DisplayName("Should return description with password auth")
    void testGetDescriptionPassword() throws Exception {
        client = createTestClient("pass", null, "/base");

        String description = client.getDescription();
        assertTrue(description.contains("SFTP["));
        assertTrue(description.contains("user@example.com:22"));
        assertTrue(description.contains("/base]"));
    }

    @Test
    @DisplayName("Should return description with private key auth")
    void testGetDescriptionPrivateKey() throws Exception {
        client = createTestClient(null, "/path/to/key", "");

        String description = client.getDescription();
        assertTrue(description.contains("SFTP["));
        assertTrue(description.contains("user@example.com:22"));
    }

    @Test
    @DisplayName("Should close without error when not connected")
    void testCloseNotConnected() throws Exception {
        client = createTestClient("pass", null, null);

        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("Should throw NullPointerException when constructor receives null host")
    void testConstructorNullHost() throws Exception {
        java.lang.reflect.Constructor<SftpFileTransferClient> constructor =
            SftpFileTransferClient.class.getDeclaredConstructor(
                String.class, int.class, String.class, String.class, String.class,
                String.class, String.class, boolean.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, 22, "user", "pass", null, null, null, false));

        assertTrue(exception.getCause() instanceof NullPointerException);
        assertTrue(exception.getCause().getMessage().contains("host cannot be null"));
    }

    @Test
    @DisplayName("Should throw NullPointerException when constructor receives null username")
    void testConstructorNullUsername() throws Exception {
        java.lang.reflect.Constructor<SftpFileTransferClient> constructor =
            SftpFileTransferClient.class.getDeclaredConstructor(
                String.class, int.class, String.class, String.class, String.class,
                String.class, String.class, boolean.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance("example.com", 22, null, "pass", null, null, null, false));

        assertTrue(exception.getCause() instanceof NullPointerException);
        assertTrue(exception.getCause().getMessage().contains("username cannot be null"));
    }

    @Test
    @DisplayName("Should verify DEFAULT_SESSION_TIMEOUT_MS constant")
    void testConstantSessionTimeout() throws Exception {
        java.lang.reflect.Field sessionTimeout = SftpFileTransferClient.class.getDeclaredField("DEFAULT_SESSION_TIMEOUT_MS");
        sessionTimeout.setAccessible(true);
        assertEquals(30000, sessionTimeout.get(null));
    }

    @Test
    @DisplayName("Should verify DEFAULT_CHANNEL_TIMEOUT_MS constant")
    void testConstantChannelTimeout() throws Exception {
        java.lang.reflect.Field channelTimeout = SftpFileTransferClient.class.getDeclaredField("DEFAULT_CHANNEL_TIMEOUT_MS");
        channelTimeout.setAccessible(true);
        assertEquals(10000, channelTimeout.get(null));
    }

    @Test
    @DisplayName("Should verify DEFAULT_BUFFER_SIZE constant")
    void testConstantBufferSize() throws Exception {
        java.lang.reflect.Field bufferSize = SftpFileTransferClient.class.getDeclaredField("DEFAULT_BUFFER_SIZE");
        bufferSize.setAccessible(true);
        assertEquals(8192, bufferSize.get(null));
    }

    @Test
    @DisplayName("Should configure strict host key checking enabled")
    void testStrictHostKeyCheckingEnabled() throws Exception {
        client = SftpFileTransferClient.builder()
            .host("example.com")
            .username("user")
            .password("pass")
            .strictHostKeyChecking(true)
            .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("Should configure strict host key checking disabled")
    void testStrictHostKeyCheckingDisabled() throws Exception {
        client = SftpFileTransferClient.builder()
            .host("example.com")
            .username("user")
            .password("pass")
            .strictHostKeyChecking(false)
            .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("Should configure known hosts file")
    void testKnownHostsFile() throws Exception {
        client = SftpFileTransferClient.builder()
            .host("example.com")
            .username("user")
            .password("pass")
            .knownHostsFile("/path/to/known_hosts")
            .build();

        assertNotNull(client);
    }

    @Test
    @DisplayName("Should accept valid port 1")
    void testValidPortMinimum() throws Exception {
        client = SftpFileTransferClient.builder()
            .host("example.com")
            .port(1)
            .username("user")
            .password("pass")
            .build();

        assertNotNull(client);
        assertTrue(client.getDescription().contains(":1"));
    }

    @Test
    @DisplayName("Should accept valid port 65535")
    void testValidPortMaximum() throws Exception {
        client = SftpFileTransferClient.builder()
            .host("example.com")
            .port(65535)
            .username("user")
            .password("pass")
            .build();

        assertNotNull(client);
        assertTrue(client.getDescription().contains(":65535"));
    }

    @Test
    @DisplayName("Should handle null basePath in constructor")
    void testConstructorNullBasePath() throws Exception {
        client = createTestClient("pass", null, null);
        assertNotNull(client);
        // Null basePath should not cause NPE
        String description = client.getDescription();
        assertTrue(description.contains("SFTP["));
    }

    @Test
    @DisplayName("Should handle both password and privateKey null in constructor")
    void testConstructorBothAuthNull() throws Exception {
        java.lang.reflect.Constructor<SftpFileTransferClient> constructor =
            SftpFileTransferClient.class.getDeclaredConstructor(
                String.class, int.class, String.class, String.class, String.class,
                String.class, String.class, boolean.class);
        constructor.setAccessible(true);

        SftpFileTransferClient testClient = constructor.newInstance(
            "example.com", 22, "user", null, null, null, null, false);
        assertNotNull(testClient);
        testClient.close();
    }

    private SftpFileTransferClient createTestClient(String password, String privateKeyPath, String basePath) throws Exception {
        java.lang.reflect.Constructor<SftpFileTransferClient> constructor =
            SftpFileTransferClient.class.getDeclaredConstructor(
                String.class, int.class, String.class, String.class, String.class,
                String.class, String.class, boolean.class);
        constructor.setAccessible(true);

        return constructor.newInstance(
            "example.com", 22, "user", password, privateKeyPath, basePath, null, false);
    }

    private SftpFileTransferClient createTestClientWithPort(int port) throws Exception {
        java.lang.reflect.Constructor<SftpFileTransferClient> constructor =
            SftpFileTransferClient.class.getDeclaredConstructor(
                String.class, int.class, String.class, String.class, String.class,
                String.class, String.class, boolean.class);
        constructor.setAccessible(true);

        return constructor.newInstance(
            "example.com", port, "user", "pass", null, null, null, false);
    }
}
