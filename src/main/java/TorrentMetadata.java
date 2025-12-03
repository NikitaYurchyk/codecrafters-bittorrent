import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public class TorrentMetadata {
    private final String trackerUrl;
    private final int pieceLength;
    private final int totalLength;
    private final List<String> pieceHashes;
    private final byte[] infoHash;

    private TorrentMetadata(String trackerUrl, int pieceLength, int totalLength,
                            List<String> pieceHashes, byte[] infoHash) {
        this.trackerUrl = trackerUrl;
        this.pieceLength = pieceLength;
        this.totalLength = totalLength;
        this.pieceHashes = new ArrayList<>(pieceHashes);
        this.infoHash = infoHash;
    }

    public static TorrentMetadata fromFile(String fileName) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(fileName));
        Bencode bencode = new Bencode(true);
        Map<String, Object> file = bencode.decode(bytes, Type.DICTIONARY);

        ByteBuffer announceBuffer = (ByteBuffer) file.get("announce");
        String trackerUrl = new String(announceBuffer.array(), StandardCharsets.UTF_8);

        Map<String, Object> info = (Map<String, Object>) file.get("info");
        byte[] infoHash = Hasher.sha1InArrayOfBytes(bencode.encode(info));

        int totalLength = ((Long) info.get("length")).intValue();
        int pieceLength = ((Long) info.get("piece length")).intValue();

        byte[] piecesBytes = ((ByteBuffer) info.get("pieces")).array();
        List<String> pieceHashes = parsePieceHashes(piecesBytes);

        return new TorrentMetadata(trackerUrl, pieceLength, totalLength, pieceHashes, infoHash);
    }

    private static List<String> parsePieceHashes(byte[] pieces) {
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < pieces.length / BittorrentConstants.INFO_HASH_SIZE; i++) {
            byte[] hashBytes = Arrays.copyOfRange(pieces, i * BittorrentConstants.INFO_HASH_SIZE,
                    (i + 1) * BittorrentConstants.INFO_HASH_SIZE);
            hashes.add(HexFormat.of().formatHex(hashBytes));
        }
        return hashes;
    }

    public int getPieceLength(int pieceIndex) {
        int offset = pieceIndex * pieceLength;
        if (offset + pieceLength > totalLength) {
            return totalLength - offset;
        }
        return pieceLength;
    }

    public int getTotalPieces() {
        return pieceHashes.size();
    }

    public String getTrackerUrl() {
        return trackerUrl;
    }

    public int getStandardPieceLength() {
        return pieceLength;
    }

    public int getTotalLength() {
        return totalLength;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public String getPieceHash(int index) {
        return pieceHashes.get(index);
    }

    public List<String> getAllPieceHashes() {
        return new ArrayList<>(pieceHashes);
    }
}
