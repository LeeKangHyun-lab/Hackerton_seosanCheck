package hackerton.seosancheck.dto.account.request;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}
