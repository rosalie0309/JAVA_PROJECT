public class TrustedHelperInfo {
    private final String token;
    private final int port;
    private final String fileId;

    public TrustedHelperInfo(String token, int port, String fileId) {
        this.token = token;
        this.port = port;
        this.fileId = fileId;
    }

    public String getToken() {
        return token;
    }

    public int getPort() {
        return port;
    }

    public String getFileId() {
        return fileId;
    }
}
