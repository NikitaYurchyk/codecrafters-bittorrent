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
      case "download" -> Bittorrent.download(args[2], args[3]);
      case "download_piece" -> Bittorrent.downloadPiece(args);
      case null, default -> System.out.println("Unknown command: " + command);
    }
  }
}

