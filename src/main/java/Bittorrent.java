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
            Integer p1 = peers.get(i) & u;
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
        for(int i = 0; i < pieces.length/20; i++){
            int numIterations = i * 20;
            byte[] tmpBytes = new byte[20];
            int index = 0;
            for(int j = numIterations; j < numIterations + 20; j++){
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
            byte[] bytes = Files.readAllBytes(Paths.get(fileName));
            Bencode bencode = new Bencode(true);
            Map<String, Object> f = bencode.decode(bytes, Type.DICTIONARY);
            Map<String, Object> info = (Map<String, Object>) f.get("info");
            var infoHash = Hasher.sha1InArrayOfBytes(bencode.encode(info));

            byte[] randomId = new byte[20];

            Random random = new Random();
            random.nextBytes(randomId);
            byte lenOfProtocol = 19;
            Integer numBytes = 1 + 19 + 8 + 20 + 20;

            var indexOfDots = rawIpAndPort.indexOf(':');
            var ip = rawIpAndPort.substring(0, indexOfDots);

            String rawPort = rawIpAndPort.substring(indexOfDots + 1, rawIpAndPort.length());
            var actualPort = Integer.parseInt(rawPort);

            ByteBuffer handshake = ByteBuffer.allocate(numBytes);

            handshake.put(lenOfProtocol);
            handshake.put("BitTorrent protocol".getBytes(StandardCharsets.UTF_8));
            handshake.put(new byte[8]);
            handshake.put(infoHash);
            handshake.put(randomId);

            Socket socket = new Socket(ip, actualPort);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(handshake.array());
            out.flush();

            byte[] resp = new byte[1024];
            int nBytes = in.read(resp);

            if(nBytes != -1) {
                byte[] readBytes = Arrays.copyOf(resp, nBytes);
                System.out.println("result of handshake");
                byte[] hashTmp = Arrays.copyOfRange(readBytes, 1 + 19 + 8, 48);
                byte[] tmp = Arrays.copyOfRange(readBytes, 1 + 19 + 8 + 20, 68);
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

    public static void makeRequest(OutputStream out, int index, int offset) throws IOException {
        // len + req number + beg + offset + payload
        ByteBuffer req = ByteBuffer.allocate(4 + 1 + 4 + 4 + 4);
        req.putInt(13);
        req.put((byte) 6);
        req.putInt(index);
        req.putInt(offset);
        req.putInt(Math.min(16384, Bittorrent.getLenOfSinglePieces() - offset));
        out.write(req.array());
        out.flush();
    }
    // int receive len first
    // 1 byte msg id which 7
    // int index piece index
    // int offset withing the piece
    // int dataBlock
//    public static Map.Entry<Integer, byte[]> recvBlockMsg(InputStream in, int blockSize) throws IOException {
//        int bytesDownloaded = 0;
//
//
//        byte[] lengthBuffer = new byte[4];
//        int bytesRead = in.read(lengthBuffer);
//        int messageLength = ByteBuffer.wrap(lengthBuffer).getInt();
//        System.out.println(messageLength);
//        byte[] messagePart = new byte[messageLength];
//        int bytesReadInMessagePart = in.readNBytes(messagePart, 0, messageLength);
//        byte msgType = messagePart[0];
//        System.out.println("Msg type: " + msgType);
//
//        byte[] indexBuffer = Arrays.copyOfRange(messagePart, 1, 5);
//        int indexFromMsg = ByteBuffer.wrap(indexBuffer).getInt();
//        System.out.println("Index number of block " + indexFromMsg);
//
//        byte[] beginBuffer = Arrays.copyOfRange(messagePart, 5, 9);
//        int offsetInMsg = ByteBuffer.wrap(beginBuffer).getInt();
//        System.out.println("Offset in the block " + offsetInMsg);
//
//        return bytesDownloaded;
//    }


}
