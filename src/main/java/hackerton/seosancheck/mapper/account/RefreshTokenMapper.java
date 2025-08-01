package hackerton.seosancheck.mapper.account;

import hackerton.seosancheck.model.account.RefreshToken;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefreshTokenMapper {

    @Insert("INSERT INTO refresh_tokens (member_id, token, expiry_date) VALUES (#{memberId}, #{token}, #{expiryDate})")
    int insert(RefreshToken refreshToken);

    @Select("SELECT * FROM refresh_tokens WHERE token = #{token}")
    RefreshToken findByToken(String token);

    @Delete("DELETE FROM refresh_tokens WHERE member_id = #{memberId}")
    int deleteByMemberId(int memberId);
}