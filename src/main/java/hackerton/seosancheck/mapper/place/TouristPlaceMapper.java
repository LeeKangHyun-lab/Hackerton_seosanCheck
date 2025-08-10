package hackerton.seosancheck.mapper.place;

import hackerton.seosancheck.model.place.TouristPlace;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TouristPlaceMapper {

//    @Insert("INSERT INTO tourist_place " +
//            "(name, address, latitude, longitude, description, reference_date, area, category, image_url, type) " +
//            "VALUES (#{name}, #{address}, #{latitude}, #{longitude}, #{description}, #{referenceDate}, #{area}, #{category}, #{imageUrl}, #{type})")
//    @Options(useGeneratedKeys = true, keyProperty = "id")
//    int insert(TouristPlace place);

    @Insert({
            "<script>",
            "INSERT INTO tourist_place (name, address, latitude, longitude, description, reference_date, area, category, image_url) VALUES",
            "<foreach collection='list' item='place' separator=','>",
            "(#{place.name}, #{place.address}, #{place.latitude}, #{place.longitude}, #{place.description}, #{place.referenceDate}, #{place.area}, #{place.category}, #{place.imageUrl})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("list") List<TouristPlace> places);

    @Select("SELECT id, name, address, latitude, longitude, description, reference_date AS referenceDate, " +
            "area, category, image_url AS imageUrl FROM tourist_place")
    List<TouristPlace> selectAll();

    @Select("SELECT id, name, address, latitude, longitude, description, reference_date AS referenceDate, " +
            "area, category, image_url AS imageUrl, type FROM tourist_place WHERE id = #{id}")
    TouristPlace selectById(Long id);

    @Delete("DELETE FROM tourist_place")
    int deleteAll();

    //MySQL
//    // 특정 지역·카테고리에서 랜덤으로 n개
//    @Select("SELECT * FROM tourist_place " +
//            "WHERE area LIKE CONCAT('%', #{area}, '%') " +
//            "AND category LIKE CONCAT('%', #{category}, '%') " +
//            "ORDER BY RAND() LIMIT #{limit}")
//    List<TouristPlace> findRandomByAreaAndCategory(@Param("area") String area,
//                                                   @Param("category") String category,
//                                                   @Param("limit") int limit);
//
//    // 중심 좌표 반경 내 랜덤 조회 (단위: km)
//    @Select("""
//    SELECT *,
//           (6371 * ACOS(
//               COS(RADIANS(#{lat})) * COS(RADIANS(latitude)) *
//               COS(RADIANS(longitude) - RADIANS(#{lng})) +
//               SIN(RADIANS(#{lat})) * SIN(RADIANS(latitude))
//           )) AS distance
//    FROM tourist_place
//    HAVING distance <= #{radiusKm}
//    ORDER BY RAND()
//    LIMIT #{limit}
//""")
//    List<TouristPlace> findNearbyRandom(@Param("lat") double latitude,
//                                        @Param("lng") double longitude,
//                                        @Param("radiusKm") double radiusKm,
//                                        @Param("limit") int limit);

    //PostgreSQL
    // 지역·카테고리 랜덤 n개
    @Select("""
    SELECT id, name, address, latitude, longitude, description,
           reference_date AS referenceDate, area, category, image_url AS imageUrl
    FROM tourist_place
    WHERE area ILIKE ('%' || #{area} || '%')
      AND category ILIKE ('%' || #{category} || '%')
    ORDER BY RANDOM()
    LIMIT #{limit}
    """)
    List<TouristPlace> findRandomByAreaAndCategory(@Param("area") String area,
                                                   @Param("category") String category,
                                                   @Param("limit") int limit);

    // 중심 좌표 반경 내 랜덤 조회 (km)
    @Select("""
    SELECT *
    FROM (
        SELECT tp.*,
               (6371 * ACOS(
                   COS(RADIANS(#{lat})) * COS(RADIANS(latitude)) *
                   COS(RADIANS(longitude) - RADIANS(#{lng})) +
                   SIN(RADIANS(#{lat})) * SIN(RADIANS(latitude))
               )) AS distance
        FROM tourist_place tp
    ) t
    WHERE distance <= #{radiusKm}
    ORDER BY RANDOM()
    LIMIT #{limit}
    """)
    List<TouristPlace> findNearbyRandom(@Param("lat") double latitude,
                                        @Param("lng") double longitude,
                                        @Param("radiusKm") double radiusKm,
                                        @Param("limit") int limit);
}
