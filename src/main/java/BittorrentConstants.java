public class BittorrentConstants {
    public static final int BLOCK_SIZE = 16 * 1024;
    public static final int PROTOCOL_LENGTH = 19;
    public static final String PROTOCOL_NAME = "BitTorrent protocol";
    public static final int HANDSHAKE_SIZE = 68;
    public static final int RESERVED_BYTES_SIZE = 8;
    public static final int INFO_HASH_SIZE = 20;
    public static final int PEER_ID_SIZE = 20;
    public static final int SOCKET_TIMEOUT_MS = 10000;
    public static final int BUFFER_SIZE = 1024;
    public static final int MESSAGE_LENGTH_BYTES = 4;
    public static final int MESSAGE_ID_OFFSET = 0;
    public static final int PIECE_MESSAGE_PAYLOAD_OFFSET = 9;
    public static final int PIECE_MESSAGE_BEGIN_OFFSET = 5;
    public static final int REQUEST_MESSAGE_LENGTH = 13;

    private BittorrentConstants() {
    }
}
