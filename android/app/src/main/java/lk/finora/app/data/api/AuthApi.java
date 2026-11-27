package lk.finora.app.data.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Streaming;
import okhttp3.ResponseBody;
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

    @POST("/api/v1/auth/consent/accept")
    Call<Void> acceptConsent(@Header("Authorization") String grant, @Body ConsentAcceptance request);

    @Streaming
    @POST("/api/v1/auth/consent/export")
    Call<ResponseBody> exportConsentData(@Header("Authorization") String grant);

    @POST("/api/v1/auth/consent/delete")
    Call<Void> deleteConsentData(@Header("Authorization") String grant, @Body Deletion request);

    record ConsentAcceptance(String consentVersion, boolean accepted) {}
    record Deletion(String password) {}

    @POST("/api/v1/auth/password/forgot")
    Call<Void> requestPasswordReset(@Body ForgotPassword request);

    @POST("/api/v1/auth/password/reset")
    Call<Void> resetPassword(@Body ResetPassword request);

    final class ForgotPassword {
        private final String email;
        public ForgotPassword(String email) { this.email = email; }
    }

    final class ResetPassword {
        private final String email;
        private final String code;
        private final String newPassword;
        public ResetPassword(String email, String code, String newPassword) {
            this.email = email;
            this.code = code;
            this.newPassword = newPassword;
        }
    }

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
        public String restrictedToken;
        public String consentVersion;
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
