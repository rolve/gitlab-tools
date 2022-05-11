import gitlabtools.auth.TokenCreator;

public class TokenCreatorIT {

    // TODO: convert to actual integration test

    public static void main(String[] args) {
        var creator = new TokenCreator("http://localhost:8080");
        var token = creator.createAccessToken("root", "password", "test-token");
        System.out.println(token);
    }
}
