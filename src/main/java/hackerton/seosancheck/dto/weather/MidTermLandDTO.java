package hackerton.seosancheck.dto.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MidTermLandDTO {
    private String wf3Am; private String wf3Pm;
    private String wf4Am; private String wf4Pm;
    private String wf5Am; private String wf5Pm;
    private String wf6Am; private String wf6Pm;
    private String wf7Am; private String wf7Pm;
    private String wf8;
    private String wf9;
    private String wf10;
}