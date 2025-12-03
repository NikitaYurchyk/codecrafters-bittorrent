import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

import javax.swing.text.html.parser.Parser;
import java.awt.image.AreaAveragingScaleFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Main {

  public static void main(String[] args) throws IOException {
    String command = args[0];
    switch (command) {
      case "decode" -> Bittorrent.decode(args[1]);
      case "info" -> Bittorrent.info(args[1]);
      case "peers" -> Bittorrent.peers(args[1]);
      case "handshake" -> Bittorrent.handshake(args[1], args[2]);
      case "download_piece" -> {
        String fileName = args[3];
        String downloadDir = args[2];
        int index = Integer.parseInt(args[4]);

        Bittorrent.info(fileName);
        Bittorrent.peers(fileName);

        byte[] bytes = Files.readAllBytes(Paths.get(fileName));

        Bencode bencode = new Bencode(true);
        Map<String, Object> f = bencode.decode(bytes, Type.DICTIONARY);
        Map<String, Object> info = (Map<String, Object>) f.get("info");

        var infoHash = Hasher.sha1InArrayOfBytes(bencode.encode(info));
        Object rawTotalLen = info.get("length");
        Object rawLen = info.get("piece length");
        int currTotal = 0;


        int lenPiece = 0;
        if (rawLen instanceof Long) {
          lenPiece = ((Long) rawLen).intValue();
        }
        int totalLen = 0;
        if (rawTotalLen instanceof Long) {
          totalLen = ((Long) rawTotalLen).intValue();
        }

        ArrayList<byte[]> allPieces = new ArrayList<>();

        for (var i : Bittorrent.getPeers()) {
          try {
            byte[] randomId = new byte[20];

            Random random = new Random();
            random.nextBytes(randomId);
            byte lenOfProtocol = 19;
            Integer numBytes = 1 + 19 + 8 + 20 + 20;

            var indexOfDots = i.indexOf(':');
            var ip = i.substring(0, indexOfDots);

            String rawPort = i.substring(indexOfDots + 1, i.length());
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


            if (nBytes != -1) {
              byte[] readBytes = Arrays.copyOf(resp, nBytes);
              byte[] tmp = Arrays.copyOfRange(readBytes, 1 + 19 + 8 + 20, 68);
              var peerId = HexFormat.of().formatHex(tmp);
              System.out.println("Peer ID: " + peerId);
            } else {
              throw new Exception("Problem in reponse!");
            }


            byte[] byteBitField = new byte[1024];
            nBytes = in.read(byteBitField);


            if (nBytes != -1) {
              byte[] readBytes = Arrays.copyOf(byteBitField, nBytes);
              System.out.println("bitField");
              System.out.println("bit field: " + Arrays.toString(readBytes));
              ByteBuffer msg_interested = ByteBuffer.allocate(5);
              msg_interested.order(ByteOrder.BIG_ENDIAN);

              msg_interested.putInt(1);
              msg_interested.put((byte) 2);
              out.write(msg_interested.array());
              out.flush();
              byte[] msg_unchoke = new byte[1024];
              nBytes = in.read(msg_unchoke);
              if (nBytes != -1) {
                readBytes = Arrays.copyOf(msg_unchoke, nBytes);
                if (readBytes.length < 5 || readBytes[4] != 1) {
                  throw new Exception("Peer did not unchoke.");
                }
              }
              int offset = 0;
              int numOfPieces = (int) Math.ceil((double) Bittorrent.getTotalLenOfAllPieces() / Bittorrent.getLenOfSinglePieces());
              System.out.println(numOfPieces + " " + Bittorrent.getTotalLenOfAllPieces() + " " + Bittorrent.getLenOfSinglePieces());
              for(int j = 0; j < numOfPieces; j++){
                Bittorrent.makeRequest(out, j, offset);
                int blockSize = Math.min(Bittorrent.getLenOfSinglePieces(), Bittorrent.getTotalLenOfAllPieces() - offset);
//                Map.Entry<Integer, byte[]> offsetAndPiece = Bittorrent.recvBlockMsg(in, blockSize);




              }
              //900000



              System.out.println();




              byte[] piece = new byte[lenPiece];


              currTotal += piece.length;

              if (totalLen - currTotal < lenPiece) {
                lenPiece = totalLen - currTotal;
              }

              allPieces.add(piece);
              index++;

            } else {
              throw new Exception("Fuck");
            }

            socket.close();
            break;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }

      }
      case null, default -> System.out.println("Unknown command: " + command);
    }
  }
}

