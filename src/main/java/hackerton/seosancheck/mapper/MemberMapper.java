package hackerton.seosancheck.mapper;

import hackerton.seosancheck.entity.account.Member;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MemberMapper {

    @Select("SELECT COUNT(*) FROM members WHERE user_id = #{userId} AND is_deleted = 'N'")
    int selectCount(String userId);

    @Insert("INSERT INTO members (user_id, nickname, user_pw) VALUES (#{userId}, #{nickname}, #{userPw})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Member member);

    @Select("SELECT id, user_id, user_pw FROM members WHERE user_id = #{userId} AND is_deleted = 'N'")
    Member selectByUserId(String userId);

    @Update("UPDATE members SET is_deleted = 'Y', deleted_at = NOW() WHERE id = #{id}")
    int softDeleteById(int id);

    @Delete("DELETE FROM members WHERE is_deleted = 'Y' AND deleted_at < NOW() - INTERVAL 30 SECOND")
    int deleteOldAccounts();
}

