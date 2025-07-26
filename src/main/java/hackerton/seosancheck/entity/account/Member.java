package hackerton.seosancheck.entity.account;

import lombok.Data;

import java.io.Serializable;

@Data
public class Member implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String userId;
    private String userPw;
}