package hackerton.seosancheck.model.ai;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class TouristPlace {
    private Long id;
    private String name;
    private String address;
    private String theme;
}
