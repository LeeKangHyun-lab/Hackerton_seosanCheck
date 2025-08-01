package hackerton.seosancheck.model.account;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
public class Member implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String userId;
    private String userPw;
    private String nickname;
    private String isDeleted; // 'N' or 'Y'
    private Date deletedAt; // 탈퇴일시
}
