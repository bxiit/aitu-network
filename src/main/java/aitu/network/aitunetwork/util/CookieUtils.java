package aitu.network.aitunetwork.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CookieUtils {

    public static final String JWT = "jwt";

    public void addJwtToCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(JWT, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 60);
        response.addCookie(cookie);
    }
}
