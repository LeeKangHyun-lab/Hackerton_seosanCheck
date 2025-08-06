package hackerton.seosancheck.mapper.place;

import hackerton.seosancheck.model.place.TouristPlace;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TouristPlaceMapper {

    @Insert("INSERT INTO tourist_place " +
            "(name, address, latitude, longitude, description, reference_date, area, category, image_url, type) " +
            "VALUES (#{name}, #{address}, #{latitude}, #{longitude}, #{description}, #{referenceDate}, #{area}, #{category}, #{imageUrl}, #{type})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TouristPlace place);

    @Select("SELECT id, name, address, latitude, longitude, description, reference_date AS referenceDate, " +
            "area, category, image_url AS imageUrl, type FROM tourist_place")
    List<TouristPlace> selectAll();

    @Select("SELECT id, name, address, latitude, longitude, description, reference_date AS referenceDate, " +
            "area, category, image_url AS imageUrl, type FROM tourist_place WHERE id = #{id}")
    TouristPlace selectById(Long id);

    @Delete("DELETE FROM tourist_place")
    int deleteAll();
}
