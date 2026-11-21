package lk.finora.app.data.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("/api/v1/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

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
}
