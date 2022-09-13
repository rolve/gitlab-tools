package gitlabtools.auth;

/**
 * Thrown when token creation via web interface failed, most
 * likely due to changes in the HTML structure.
 */
public class TokenCreationException extends Exception {
    public TokenCreationException(Throwable cause) {
        super(cause);
    }
}
