package hackerton.seosancheck.mapper.place;

import hackerton.seosancheck.model.place.Store;
import hackerton.seosancheck.model.place.TouristPlace;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface StoreMapper {

    @Insert("INSERT INTO store " +
            "(name, address, detail_address, location, type, longitude, latitude, kind_store, tag) " +
            "VALUES (#{name}, #{address}, #{detailAddress}, #{location}, #{type}, #{longitude}, #{latitude}, #{kindStore}, #{tag})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Store store);

    @Insert({
            "<script>",
            "INSERT INTO store (name, address, detail_address, location, type, longitude, latitude, kind_store, tag) VALUES",
            "<foreach collection='list' item='store' separator=','>",
            "(#{store.name}, #{store.address}, #{store.detailAddress}, #{store.location}, #{store.type}, #{store.longitude}, #{store.latitude}, #{store.kindStore}, #{store.tag})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("list") List<Store> stores);

    // 전체 SELECT
    @Select("SELECT id, name, address, detail_address AS detailAddress, " +
            "location, type, longitude, latitude, kind_store AS kindStore, tag FROM store")
    List<Store> selectAll();

    // 특정 id로 조회
    @Select("SELECT id, name, address, detail_address AS detailAddress, " +
            "location, type, longitude, latitude, kind_store AS kindStore, tag FROM store WHERE id = #{id}")
    Store selectById(Long id);

    // 기존 데이터 삭제 (전체 삭제)
    @Delete("DELETE FROM store")
    int deleteAll();


    //MySQL
    // 식당 근처 조회
//    @Select("""
//    SELECT id, name, address, detail_address AS detailAddress,
//           location, type, longitude, latitude, kind_store AS kindStore, tag,
//           (6371 * acos(
//               cos(radians(#{lat})) * cos(radians(latitude)) *
//               cos(radians(longitude) - radians(#{lon})) +
//               sin(radians(#{lat})) * sin(radians(latitude))
//           )) AS distance
//    FROM store
//    WHERE (tag LIKE '%식%' OR tag LIKE '%집%' OR kind_store LIKE '%해산물%')
//    HAVING distance < #{radiusKm}
//    ORDER BY distance ASC
//    LIMIT #{limit}
//    """)
//    List<Store> findNearbyStores(
//            @Param("lat") double latitude,
//            @Param("lon") double longitude,
//            @Param("radiusKm") double radiusKm,
//            @Param("limit") int limit);


    //PostgreSQL
    // 식당 근처 조회
    @Select("""
    SELECT *
    FROM (
        SELECT id, name, address, detail_address AS detailAddress,
               location, type, longitude, latitude, kind_store AS kindStore, tag,
               (6371 * acos(
                   cos(radians(#{lat})) * cos(radians(latitude)) *
                   cos(radians(longitude) - radians(#{lon})) +
                   sin(radians(#{lat})) * sin(radians(latitude))
               )) AS distance
        FROM store
        WHERE (tag ILIKE '%식%' OR tag ILIKE '%집%' OR kind_store ILIKE '%해산물%')
    ) sub
    WHERE sub.distance < #{radiusKm}
    ORDER BY sub.distance ASC
    LIMIT #{limit}
    """)
    List<Store> findNearbyStores(
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusKm") double radiusKm,
            @Param("limit") int limit);


}
