import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import com.dampcake.bencode.Type;
import com.google.gson.Gson;
import com.dampcake.bencode.Bencode;
import java.util.List;
import java.util.Map;

public class Decoder {
    public static final Gson gson = new Gson();

    public static String decodeBencode(String bencodedString) {
        char firstChar = bencodedString.charAt(0);

        if (Character.isDigit(firstChar)) {
            int firstColonIndex = 0;
            for(int i = 0; i < bencodedString.length(); i++) {
                if(bencodedString.charAt(i) == ':') {
                    firstColonIndex = i;
                    break;
                }
            }
            int length = Integer.parseInt(bencodedString.substring(0, firstColonIndex));
            return bencodedString.substring(firstColonIndex+1, firstColonIndex+1+length);
        } else if (firstChar == 'i') {
            Bencode bencode = new Bencode();
            Long number = bencode.decode(bencodedString.getBytes(), Type.NUMBER);
            return gson.toJson(number);
        } else if (firstChar == 'l') {
            Bencode bencode = new Bencode();
            List<Object> lst = bencode.decode(bencodedString.getBytes(), Type.LIST);
            return gson.toJson(lst);
        } else if (firstChar == 'd') {
            Bencode bencode = new Bencode();
            Map<String, Object> dict = bencode.decode(bencodedString.getBytes(), Type.DICTIONARY);
            return gson.toJson(dict);
        } else {
            throw new RuntimeException("Unsupported bencode type: " + firstChar);
        }
    }
}
