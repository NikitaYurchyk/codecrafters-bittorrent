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
        int pieceIndex = Integer.parseInt(args[4]);

        Bittorrent.info(fileName);
        Bittorrent.peers(fileName);

        byte[] bytes = Files.readAllBytes(Paths.get(fileName));

        Bencode bencode = new Bencode(true);
        Map<String, Object> f = bencode.decode(bytes, Type.DICTIONARY);
        Map<String, Object> info = (Map<String, Object>) f.get("info");

        var infoHash = Hasher.sha1InArrayOfBytes(bencode.encode(info));

        int blockSize = 16 * 1024;
        int standardPieceLength = Bittorrent.getLenOfSinglePieces();
        int totalFileLength = Bittorrent.getTotalLenOfAllPieces();

        // Calculate actual piece length (last piece might be smaller)
        int pieceLength;
        int pieceOffset = pieceIndex * standardPieceLength;
        if (pieceOffset + standardPieceLength > totalFileLength) {
          pieceLength = totalFileLength - pieceOffset;
          System.out.println("Last piece detected, actual length: " + pieceLength);
        } else {
          pieceLength = standardPieceLength;
        }

        int numBlocks = (int) Math.ceil((double) pieceLength / blockSize);

        boolean pieceDownloaded = false;
        for (var i : Bittorrent.getPeers()) {
          if (pieceDownloaded) break;
          try {
            System.out.println("Trying peer: " + i);
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

              byte[] fullPiece = new byte[pieceLength];

              for (int blockIndex = 0; blockIndex < numBlocks; blockIndex++) {
                int begin = blockIndex * blockSize;
                int length = Math.min(blockSize, pieceLength - begin);
                Bittorrent.makeRequest(out, pieceIndex, begin, length);

                byte[] lengthBytes = new byte[4];
                int lengthBytesRead = 0;
                while (lengthBytesRead < 4) {
                  int bytesRead = in.read(lengthBytes, lengthBytesRead, 4 - lengthBytesRead);
                  if (bytesRead == -1) {
                    throw new Exception("Connection closed while reading message length");
                  }
                  lengthBytesRead += bytesRead;
                }
                int messageLength = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).getInt();

                byte[] message = new byte[messageLength];
                int totalRead = 0;
                while (totalRead < messageLength) {
                  int bytesRead = in.read(message, totalRead, messageLength - totalRead);
                  if (bytesRead == -1) {
                    throw new Exception("Connection closed while reading message body");
                  }
                  totalRead += bytesRead;
                }

                byte messageId = message[0];
                if (messageId != 7) {
                  throw new Exception("Expected piece message (7), got: " + messageId);
                }
                int receivedBegin = ByteBuffer.wrap(message, 5, 4).order(ByteOrder.BIG_ENDIAN).getInt();

                int blockDataLength = messageLength - 9;
                System.arraycopy(message, 9, fullPiece, receivedBegin, blockDataLength);
                System.out.println("Received block " + blockIndex + " (begin=" + receivedBegin + ", length=" + blockDataLength + ")");
              }

              byte[] pieceHash = Hasher.sha1InArrayOfBytes(fullPiece);
              String actualHash = HexFormat.of().formatHex(pieceHash);
              String expectedHash = Bittorrent.getPiecesHashes().get(pieceIndex);

              if (!actualHash.equals(expectedHash)) {
                throw new Exception("Hash mismatch! Expected: " + expectedHash + ", Got: " + actualHash);
              }

              Files.write(Paths.get(downloadDir), fullPiece);
              System.out.println("Piece " + pieceIndex + " downloaded to " + downloadDir);
              pieceDownloaded = true;

            } else {
              throw new Exception("No bitfield received from peer");
            }

            socket.close();
          } catch (Exception e) {
            System.err.println("Failed to download from peer " + i + ": " + e.getMessage());
          }
        }

        if (!pieceDownloaded) {
          throw new RuntimeException("Failed to download piece from any peer");
        }

      }
      case null, default -> System.out.println("Unknown command: " + command);
    }
  }
}

