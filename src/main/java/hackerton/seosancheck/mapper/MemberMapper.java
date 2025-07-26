package hackerton.seosancheck.mapper;

import hackerton.seosancheck.entity.account.Member;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemberMapper {

    @Select("SELECT COUNT(*) FROM members WHERE user_id = #{userId}")
    int selectCount(String userId);

    @Insert("INSERT INTO members (user_id, user_pw) VALUES (#{userId}, #{userPw})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Member member);

    @Select("SELECT id, user_id, user_pw FROM members WHERE user_id = #{userId}")
    Member selectByUserId(String userId);
}
