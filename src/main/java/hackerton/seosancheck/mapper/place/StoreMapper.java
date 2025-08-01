package hackerton.seosancheck.mapper.place;

import hackerton.seosancheck.model.place.Store;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface StoreMapper {

    // 단건 INSERT
    @Insert("INSERT INTO store " +
            "(name, base_address, detail_address, location, type, mapx, mapy) " +
            "VALUES (#{name}, #{baseAddress}, #{detailAddress}, #{location}, #{type}, #{mapx}, #{mapy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Store store);

    // 전체 SELECT
    @Select("SELECT id, name, base_address AS baseAddress, detail_address AS detailAddress, " +
            "location, type, mapx, mapy FROM store")
    List<Store> selectAll();

    // 특정 id로 조회
    @Select("SELECT id, name, base_address AS baseAddress, detail_address AS detailAddress, " +
            "location, type, mapx, mapy FROM store WHERE id = #{id}")
    Store selectById(Long id);

    // 기존 데이터 삭제 (전체 삭제)
    @Delete("DELETE FROM store")
    int deleteAll();
}
