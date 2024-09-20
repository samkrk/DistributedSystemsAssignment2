

public class test{

    public static void main(String[] args){
        String file = "src/content/weather0.txt";

        String res = JSONParser.convertFileToJSON(file);
        System.out.println(res);

    }
}