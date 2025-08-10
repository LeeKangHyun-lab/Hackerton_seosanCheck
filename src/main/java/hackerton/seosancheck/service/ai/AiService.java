package hackerton.seosancheck.service.ai;

import hackerton.seosancheck.model.ai.TravelConditions;
import hackerton.seosancheck.model.ai.TravelPlanResponse;

import java.util.List;

public interface AiService {

//    public TravelPlanResponse generateTravelPlan(String area, String category, String text);

//    List<TravelPlanResponse> generateMultiplePlans(String area, String category, String text);

    TravelConditions extractConditions(String sentence);

    List<TravelPlanResponse> generateMultiplePlans(String text, String areaParam, String categoryParam);
}
