import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Bittorrent {
    static private String trackerUrl;
    static private int lenOfSinglePiece = 0;
    static private int totalLenOfAllPieces = 0;

    static private ArrayList<String> piecesHashes = new ArrayList<>();
    static private ArrayList<String> listOfPeers = new ArrayList<>();


    static public int getLenOfSinglePieces(){
        return lenOfSinglePiece;
    }

    static public int getTotalLenOfAllPieces(){
        return totalLenOfAllPieces;
    }


    public static void printPeers(ByteBuffer peers){
        ArrayList<String> res = new ArrayList<>();
        Integer u = 0xFF;
        for(int i = 0; i < peers.limit(); i += 6){
            Integer p1 = peers.get(i)     & u;
            Integer p2 = peers.get(i + 1) & u;
            Integer p3 = peers.get(i + 2) & u;
            Integer p4 = peers.get(i + 3) & u;

            ByteBuffer slice = ByteBuffer.wrap(new byte[]{peers.get(i + 4), peers.get(i + 5)});
            int r = slice.order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
            String s = p1.toString() + '.' + p2.toString() + '.' + p3.toString() + '.' + p4.toString() + ":" + r;
            res.add(s);
        }
        for (var i : res){
            listOfPeers.add(i);
            System.out.println(Decoder.gson.toJson(i));
        }

    }
    private static void printPieces(byte[] pieces){
        for(int i = 0; i < pieces.length / BittorrentConstants.INFO_HASH_SIZE; i++){
            int numIterations = i * BittorrentConstants.INFO_HASH_SIZE;
            byte[] tmpBytes = new byte[BittorrentConstants.INFO_HASH_SIZE];
            int index = 0;
            for(int j = numIterations; j < numIterations + BittorrentConstants.INFO_HASH_SIZE; j++){
                tmpBytes[index] = pieces[j];
                index++;
            }
            String hex = HexFormat.of().formatHex(tmpBytes);

            piecesHashes.add(hex);
            System.out.println(Decoder.gson.toJson(hex));
        }
        return;
    }
    public static String getTrackerUrl(){
        return trackerUrl;
    }
    public static ArrayList<String> getPeers(){
        return listOfPeers;
    }
    public static ArrayList<String> getPiecesHashes(){
        return piecesHashes;
    }

    public static void peers(String fileName){
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(fileName));
            Bencode bencode = new Bencode(true);
            Map<String, Object> f = bencode.decode(bytes, Type.DICTIONARY);
            Object announceObject = f.get("announce");

            byte[] announceBytes = ((ByteBuffer) announceObject).array();

            var trackerURL = new String(announceBytes, StandardCharsets.UTF_8);

            TrackerRequest r = new TrackerRequest();

            Map<String, Object> info = (Map<String, Object>) f.get("info");

            r.infoHash = Hasher.strToSHA1(bencode.encode(info));
            r.left = info.get("length").toString();

            String infoHashWithPrecents = r.percentsEncoding();
            String encoded_info_hash = r.transformCharsIntoStrToHex(infoHashWithPrecents);
            String info_hash = "?info_hash=" + encoded_info_hash;
            String peer_id = "&peer_id=" + r.peerId;
            String port = "&port=" + r.port;
            String uploaded = "&uploaded=" + r.uploaded;
            String downloaded = "&downloaded=" + r.downloaded;
            String left = "&left=" + r.left;
            String compact = "&compact=" + r.compact;

            String url = trackerURL + info_hash + peer_id + port + uploaded + downloaded + left + compact;

            HttpClient httpClient = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            Bencode b = new Bencode(true);

            var check = b.decode(response.body(), Type.DICTIONARY);
            printPeers((ByteBuffer) check.get("peers"));

        }catch (RuntimeException e){
            System.err.println(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void handshake(String fileName, String rawIpAndPort){
        try {
            TorrentMetadata metadata = TorrentMetadata.fromFile(fileName);

            byte[] randomId = new byte[BittorrentConstants.PEER_ID_SIZE];
            new Random().nextBytes(randomId);

            var indexOfDots = rawIpAndPort.indexOf(':');
            var ip = rawIpAndPort.substring(0, indexOfDots);
            String rawPort = rawIpAndPort.substring(indexOfDots + 1);
            var actualPort = Integer.parseInt(rawPort);

            ByteBuffer handshake = ByteBuffer.allocate(BittorrentConstants.HANDSHAKE_SIZE);
            handshake.put((byte) BittorrentConstants.PROTOCOL_LENGTH);
            handshake.put(BittorrentConstants.PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));
            handshake.put(new byte[BittorrentConstants.RESERVED_BYTES_SIZE]);
            handshake.put(metadata.getInfoHash());
            handshake.put(randomId);

            Socket socket = new Socket(ip, actualPort);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(handshake.array());
            out.flush();

            byte[] resp = new byte[BittorrentConstants.BUFFER_SIZE];
            int bytesRead = in.read(resp);

            if(bytesRead != -1) {
                byte[] readBytes = Arrays.copyOf(resp, bytesRead);
                System.out.println("result of handshake");
                byte[] tmp = Arrays.copyOfRange(readBytes, 48, BittorrentConstants.HANDSHAKE_SIZE);
                var peerId = HexFormat.of().formatHex(tmp);
                System.out.println("Peer ID: " + peerId);
            }else{
                throw new Exception("Problem in reponse!");
            }
            socket.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void decode(String encodedMsg){
        String bencodedValue = encodedMsg;
        String decoded;
        try {
            decoded = Decoder.decodeBencode(bencodedValue);
        } catch(RuntimeException e) {
            System.out.println(e.getMessage());
            return;
        }
        System.out.println(decoded);
    }

    public static void info(String fileName){
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(fileName));
            Bencode bencode = new Bencode(true);

            Map<String, Object> f = bencode.decode(bytes, Type.DICTIONARY);
            Object announceObject = f.get("announce");

            byte[] announceBytes = ((ByteBuffer) announceObject).array();
            trackerUrl = new String(announceBytes, StandardCharsets.UTF_8);

            Map<String, Object> info = (Map<String, Object>) f.get("info");
            Object rawTotalLen = info.get("length");
            if(rawTotalLen instanceof Long){
                totalLenOfAllPieces = ((Long) rawTotalLen).intValue();
            }

            System.out.println(Decoder.gson.toJson("Tracker URL: "+ trackerUrl));
            System.out.println(Decoder.gson.toJson("Length: "+ rawTotalLen));


            System.out.println("Info Hash: "+ Hasher.strToSHA1(bencode.encode(info)));
            var pieces = info.get("pieces");
            Object rawLen = info.get("piece length");
            if(rawLen instanceof Long){
                lenOfSinglePiece = ((Long) rawLen).intValue();
            }
            byte[] piecesInByte = ((ByteBuffer) pieces).array();
            System.out.println("Piece Length: " + lenOfSinglePiece);
            System.out.println("Piece Hashes: ");
            printPieces(piecesInByte);


        }catch (RuntimeException e) {
            System.err.println(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] downloadSinglePiece(PeerConnection peer, TorrentMetadata metadata,
                                              int pieceIndex) throws IOException {
        int pieceLength = metadata.getPieceLength(pieceIndex);
        int numBlocks = (int) Math.ceil((double) pieceLength / BittorrentConstants.BLOCK_SIZE);
        byte[] fullPiece = new byte[pieceLength];

        for (int blockIndex = 0; blockIndex < numBlocks; blockIndex++) {
            int begin = blockIndex * BittorrentConstants.BLOCK_SIZE;
            int length = Math.min(BittorrentConstants.BLOCK_SIZE, pieceLength - begin);

            byte[] blockData = peer.requestBlock(pieceIndex, begin, length);
            System.arraycopy(blockData, 0, fullPiece, begin, blockData.length);
        }

        String actualHash = HexFormat.of().formatHex(Hasher.sha1InArrayOfBytes(fullPiece));
        String expectedHash = metadata.getPieceHash(pieceIndex);
        if (!actualHash.equals(expectedHash)) {
            throw new IOException("Hash mismatch for piece " + pieceIndex);
        }

        return fullPiece;
    }

    private static List<String> parsePeerAddresses(ByteBuffer peers) {
        List<String> addresses = new ArrayList<>();
        int mask = 0xFF;

        for (int i = 0; i < peers.limit(); i += 6) {
            int p1 = peers.get(i) & mask;
            int p2 = peers.get(i + 1) & mask;
            int p3 = peers.get(i + 2) & mask;
            int p4 = peers.get(i + 3) & mask;

            ByteBuffer portBuffer = ByteBuffer.wrap(new byte[]{peers.get(i + 4), peers.get(i + 5)});
            int port = portBuffer.order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;

            addresses.add(p1 + "." + p2 + "." + p3 + "." + p4 + ":" + port);
        }

        return addresses;
    }

    private static byte[] assembleFile(byte[][] pieces) {
        int totalBytes = 0;
        for (byte[] piece : pieces) {
            totalBytes += piece.length;
        }

        byte[] file = new byte[totalBytes];
        int offset = 0;
        for (byte[] piece : pieces) {
            System.arraycopy(piece, 0, file, offset, piece.length);
            offset += piece.length;
        }

        return file;
    }

    public static void download(String outputPath, String fileName) throws IOException {
        TorrentMetadata metadata = TorrentMetadata.fromFile(fileName);

        Bittorrent.info(fileName);
        Bittorrent.peers(fileName);

        List<String> peerList = new ArrayList<>(listOfPeers);

        int totalPieces = metadata.getTotalPieces();
        byte[][] allPieces = new byte[totalPieces][];
        boolean[] downloaded = new boolean[totalPieces];
        int piecesDownloaded = 0;

        System.out.println("Downloading " + totalPieces + " pieces...");

        for (String peerAddress : peerList) {
            if (piecesDownloaded == totalPieces) break;

            try (PeerConnection peer = new PeerConnection(peerAddress, metadata.getInfoHash())) {
                System.out.println("Connecting to peer: " + peerAddress);

                peer.connect();
                peer.performHandshake();
                peer.waitForBitfield();
                peer.sendInterested();
                peer.waitForUnchoke();

                for (int pieceIndex = 0; pieceIndex < totalPieces; pieceIndex++) {
                    if (downloaded[pieceIndex]) continue;

                    try {
                        byte[] pieceData = downloadSinglePiece(peer, metadata, pieceIndex);
                        allPieces[pieceIndex] = pieceData;
                        downloaded[pieceIndex] = true;
                        piecesDownloaded++;
                        System.out.println("Downloaded piece " + pieceIndex +
                                " (" + piecesDownloaded + "/" + totalPieces + ")");
                    } catch (IOException e) {
                        System.err.println("Failed piece " + pieceIndex + ": " + e.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed peer " + peerAddress + ": " + e.getMessage());
            }
        }

        if (piecesDownloaded < totalPieces) {
            throw new IOException("Downloaded only " + piecesDownloaded + "/" + totalPieces + " pieces");
        }

        byte[] completeFile = assembleFile(allPieces);
        Files.write(Paths.get(outputPath), completeFile);
        System.out.println("Downloaded " + outputPath);
    }

    public static void downloadPiece(String[] args) throws IOException {
        String downloadDir = args[2];
        String fileName = args[3];
        int pieceIndex = Integer.parseInt(args[4]);

        TorrentMetadata metadata = TorrentMetadata.fromFile(fileName);

        Bittorrent.info(fileName);
        Bittorrent.peers(fileName);

        List<String> peerList = new ArrayList<>(listOfPeers);

        for (String peerAddress : peerList) {
            try (PeerConnection peer = new PeerConnection(peerAddress, metadata.getInfoHash())) {
                System.out.println("Trying peer: " + peerAddress);

                peer.connect();
                peer.performHandshake();
                System.out.println("Peer ID: " + peer.getPeerId());
                peer.waitForBitfield();
                peer.sendInterested();
                peer.waitForUnchoke();

                byte[] pieceData = downloadSinglePiece(peer, metadata, pieceIndex);

                Files.write(Paths.get(downloadDir), pieceData);
                System.out.println("Piece " + pieceIndex + " downloaded to " + downloadDir);
                return;

            } catch (IOException e) {
                System.err.println("Failed to download from peer " + peerAddress + ": " + e.getMessage());
            }
        }

        throw new IOException("Failed to download piece from any peer");
    }
}
