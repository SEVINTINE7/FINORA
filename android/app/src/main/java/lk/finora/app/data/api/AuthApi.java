package lk.finora.app.data.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("/api/v1/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("/api/v1/auth/register")
    Call<RegisterResponse> register(@Body RegisterRequest request);

    @POST("/api/v1/auth/refresh")
    Call<LoginResponse> refresh(@Body RefreshRequest request);

    @POST("/api/v1/auth/logout")
    Call<Void> logout(@Body RefreshRequest request);

    final class RefreshRequest {
        private final String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    final class LoginRequest {
        private final String email;
        private final String password;

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    final class LoginResponse {
        public String accessToken;
        public String refreshToken;
        public long expiresIn;
        public String role;
        public String language;
    }

    final class RegisterRequest {
        private final String email;
        private final String password;
        private final String fullName;
        private final String language;
        private final boolean over18;
        private final String consentVersion;

        public RegisterRequest(String email, String password, String fullName, String language,
                boolean over18, String consentVersion) {
            this.email = email;
            this.password = password;
            this.fullName = fullName;
            this.language = language;
            this.over18 = over18;
            this.consentVersion = consentVersion;
        }
    }

    final class RegisterResponse {
        public long userId;
    }
}
