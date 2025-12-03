import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public  class Hasher {
    public static byte[] sha1InArrayOfBytes(byte[] info) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = md.digest(info);
            return hashBytes;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 Algorithm not found", e);
        }
    }

    public static String strToSHA1(byte[] info) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = md.digest(info);
            StringBuilder res = new StringBuilder();
            for (byte b : hashBytes) {
                res.append(String.format("%02x", b));
            }
            return res.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 Algorithm not found", e);
        }
    }

    public static String byteToStrHex(byte[] arr) {
        StringBuilder res = new StringBuilder();
        for (byte b : arr) {
            res.append(String.format("%02x", b));
        }
        return res.toString();

    }

}
