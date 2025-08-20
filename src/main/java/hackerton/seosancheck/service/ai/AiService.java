package hackerton.seosancheck.service.ai;

import hackerton.seosancheck.model.ai.TravelConditions;
import hackerton.seosancheck.model.ai.TravelPlanResponse;

import java.util.List;

public interface AiService {


    TravelConditions extractConditions(String sentence);

    List<TravelPlanResponse> generateMultiplePlans(String text, String areaParam);
}
