public class TrackerRequest {
    public String infoHash;
    public String peerId = "01234567890123456789";
    public String port = "6881";
    public String uploaded = "0";
    public String downloaded = "0";
    public String left = "0";
    public String compact = "1";

    public String percentsEncoding(){
        StringBuilder res = new StringBuilder("%");
        for(int i = 0; i < infoHash.length(); i+=2){
            res.append(infoHash.substring(i, i + 2));
            res.append("%");
        }
        return res.toString();
    }

    public String transformCharsIntoStrToHex(String str) {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < str.length(); i += 3) {
            if (i + 3 > str.length()) break;
            String tmp = str.substring(i, i + 3);
            if (tmp.charAt(0) != '%') {
                break;
            }
            char hexValue = (char) Integer.parseInt(tmp.substring(1), 16);
            if (
                    hexValue == '-'
                    || hexValue == '_'
                    || hexValue == '.'
                    || hexValue == '~'
                    || (hexValue >= 65 && hexValue <= 90)
                    || (hexValue >= 97 && hexValue <= 122)
            ) {
                res.append(hexValue);
            } else {
                res.append(tmp);
            }
        }
        return res.toString();
    }


}

