package gitlabtools.auth;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a personal access token that can be used to authenticate
 * with the GitLab REST API. It uses GitLab's web interface, which
 * (in contrast to the REST API) supports username/password
 * authentication.
 */
public class TokenCreator {

    static {
        // turn off annoying warnings
        Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.SEVERE);
    }

    private final String gitLabUrl;

    public TokenCreator(String gitLabUrl) {
        this.gitLabUrl = gitLabUrl;
    }

    public String createAccessToken(String username, String password, String tokenName) {
        var driver = new HtmlUnitDriver();
        try {
            login(driver, username, password);

            driver.navigate().to(gitLabUrl + "/-/profile/personal_access_tokens");
            driver.findElement(By.id("personal_access_token_name")).sendKeys(tokenName);
            driver.findElement(By.id("personal_access_token_scopes_api")).click();
            driver.findElement(By.cssSelector("input[type=submit]")).click();

            return driver.findElement(By.id("created-personal-access-token")).getAttribute("value");
        } finally {
            driver.close();
        }
    }

    private void login(WebDriver driver, String username, String password) {
        driver.navigate().to(gitLabUrl);
        driver.findElement(By.id("user_login")).sendKeys(username);
        driver.findElement(By.id("user_password")).sendKeys(password);
        driver.findElement(By.cssSelector("#new_user [type=submit]")).click();

        if (driver.getCurrentUrl().contains("sign_in")) {
            throw new AuthenticationException();
        }
    }
}
