package hackerton.seosancheck.mapper.place;

import hackerton.seosancheck.model.place.TouristPlace;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TouristPlaceMapper {

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
            "area, category, image_url AS imageUrl,  type FROM tourist_place WHERE id = #{id}")
    TouristPlace selectById(Long id);

    @Delete("DELETE FROM tourist_place")
    int deleteAll();

    //PostgreSQL
//     지역·카테고리 랜덤 n개
    @Select("""
    SELECT id, name, address, latitude, longitude, description,
           reference_date AS referenceDate, area, category, image_url AS imageUrl
    FROM tourist_place
    WHERE area ILIKE ('%' || #{area} || '%')
    ORDER BY RANDOM()
    LIMIT #{limit}
    """)
    List<TouristPlace> findRandomByArea(@Param("area") String area,
                                        @Param("limit") int limit);

//    PostgreSQL
    //관광지 근처 조회
    @Select("""
        SELECT *
        FROM (
            SELECT id, name, address, latitude, longitude, description, reference_date AS referenceDate,
                   area, category, image_url AS imageUrl,
                   (6371 * acos(
                       cos(radians(#{lat})) * cos(radians(latitude)) *
                       cos(radians(longitude) - radians(#{lon})) +
                       sin(radians(#{lat})) * sin(radians(latitude))
                   )) AS distance
            FROM tourist_place
        ) sub
        WHERE sub.distance < #{radiusKm}
        ORDER BY RANDOM()
        LIMIT #{limit}
        """)
    List<TouristPlace> findNearbyPlaces(
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusKm") double radiusKm,   // km 단위
            @Param("limit") int limit);


}
