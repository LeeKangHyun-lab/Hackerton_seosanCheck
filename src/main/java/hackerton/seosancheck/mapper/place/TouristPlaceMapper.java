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

    @Select("SELECT * FROM tourist_place " +
            "WHERE area = #{area} AND category = #{category} " +
            "ORDER BY RAND() LIMIT #{limit}")
    List<TouristPlace> findRandomByAreaAndCategory(@Param("area") String area,
                                                   @Param("category") String category,
                                                   @Param("limit") int limit);


}
