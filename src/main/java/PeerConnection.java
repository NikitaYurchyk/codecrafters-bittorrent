import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;

public class PeerConnection implements AutoCloseable {
    private final String peerAddress;
    private final byte[] infoHash;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private String peerId;

    public PeerConnection(String peerAddress, byte[] infoHash) {
        this.peerAddress = peerAddress;
        this.infoHash = infoHash;
    }

    public void connect() throws IOException {
        int colonIndex = peerAddress.indexOf(':');
        String ip = peerAddress.substring(0, colonIndex);
        int port = Integer.parseInt(peerAddress.substring(colonIndex + 1));

        socket = new Socket(ip, port);
        socket.setSoTimeout(BittorrentConstants.SOCKET_TIMEOUT_MS);
        out = socket.getOutputStream();
        in = socket.getInputStream();
    }

    public void performHandshake() throws IOException {
        byte[] randomId = new byte[BittorrentConstants.PEER_ID_SIZE];
        new Random().nextBytes(randomId);

        ByteBuffer handshake = ByteBuffer.allocate(BittorrentConstants.HANDSHAKE_SIZE);
        handshake.put((byte) BittorrentConstants.PROTOCOL_LENGTH);
        handshake.put(BittorrentConstants.PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));
        handshake.put(new byte[BittorrentConstants.RESERVED_BYTES_SIZE]);
        handshake.put(infoHash);
        handshake.put(randomId);

        out.write(handshake.array());
        out.flush();

        byte[] response = new byte[BittorrentConstants.BUFFER_SIZE];
        int bytesRead = in.read(response);
        if (bytesRead == -1) {
            throw new IOException("No handshake response from peer");
        }

        byte[] peerIdBytes = Arrays.copyOfRange(response, 48, 68);
        this.peerId = HexFormat.of().formatHex(peerIdBytes);
    }

    public void waitForBitfield() throws IOException {
        byte[] bitfield = new byte[BittorrentConstants.BUFFER_SIZE];
        int bytesRead = in.read(bitfield);
        if (bytesRead == -1) {
            throw new IOException("No bitfield received from peer");
        }
    }

    public void sendInterested() throws IOException {
        ByteBuffer interested = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        interested.putInt(1);
        interested.put((byte) MessageType.INTERESTED.getCode());
        out.write(interested.array());
        out.flush();
    }

    public void waitForUnchoke() throws IOException {
        byte[] message = new byte[BittorrentConstants.BUFFER_SIZE];
        int bytesRead = in.read(message);
        if (bytesRead == -1 || message[4] != MessageType.UNCHOKE.getCode()) {
            throw new IOException("Peer did not unchoke");
        }
    }

    public byte[] requestBlock(int pieceIndex, int begin, int length) throws IOException {
        ByteBuffer request = ByteBuffer.allocate(4 + 1 + 4 + 4 + 4).order(ByteOrder.BIG_ENDIAN);
        request.putInt(BittorrentConstants.REQUEST_MESSAGE_LENGTH);
        request.put((byte) MessageType.REQUEST.getCode());
        request.putInt(pieceIndex);
        request.putInt(begin);
        request.putInt(length);
        out.write(request.array());
        out.flush();

        byte[] lengthBytes = new byte[BittorrentConstants.MESSAGE_LENGTH_BYTES];
        int lengthBytesRead = 0;
        while (lengthBytesRead < BittorrentConstants.MESSAGE_LENGTH_BYTES) {
            int bytesRead = in.read(lengthBytes, lengthBytesRead,
                    BittorrentConstants.MESSAGE_LENGTH_BYTES - lengthBytesRead);
            if (bytesRead == -1) {
                throw new IOException("Connection closed while reading message length");
            }
            lengthBytesRead += bytesRead;
        }
        int messageLength = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).getInt();

        byte[] message = new byte[messageLength];
        int totalRead = 0;
        while (totalRead < messageLength) {
            int bytesRead = in.read(message, totalRead, messageLength - totalRead);
            if (bytesRead == -1) {
                throw new IOException("Connection closed while reading message body");
            }
            totalRead += bytesRead;
        }

        if (message[BittorrentConstants.MESSAGE_ID_OFFSET] != MessageType.PIECE.getCode()) {
            throw new IOException("Expected piece message, got: " + message[BittorrentConstants.MESSAGE_ID_OFFSET]);
        }

        int blockDataLength = messageLength - BittorrentConstants.PIECE_MESSAGE_PAYLOAD_OFFSET;
        return Arrays.copyOfRange(message, BittorrentConstants.PIECE_MESSAGE_PAYLOAD_OFFSET,
                BittorrentConstants.PIECE_MESSAGE_PAYLOAD_OFFSET + blockDataLength);
    }

    public String getPeerId() {
        return peerId;
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
