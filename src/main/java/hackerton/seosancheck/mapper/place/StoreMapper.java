package hackerton.seosancheck.mapper.place;

import hackerton.seosancheck.model.place.Store;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface StoreMapper {

    // 단건 INSERT

    // (!!!!!!!!!!!!!!!!batch 방식으로 개선하기!!!!!!!!!!!!!!!!!!!!)


    @Insert("INSERT INTO store " +
            "(name, address, detail_address, location, type, longitude, latitude) " +
            "VALUES (#{name}, #{address}, #{detailAddress}, #{location}, #{type}, #{longitude}, #{latitude})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Store store);

    // 전체 SELECT
    @Select("SELECT id, name, address, detail_address AS detailAddress, " +
            "location, type, longitude, latitude FROM store")
    List<Store> selectAll();

//    // 특정 id로 조회
//    @Select("SELECT id, name, address, detail_address AS detailAddress, " +
//            "location, type, longitude, latitude FROM store WHERE id = #{id}")
//    Store selectById(Long id);

    // 기존 데이터 삭제 (전체 삭제)
    @Delete("DELETE FROM store")
    int deleteAll();
}
