package uk.tw.energy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.tw.energy.service.AccountService;
import uk.tw.energy.service.PricePlanService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/price-plans")
public class PricePlanComparatorController {

    public final static String PRICE_PLAN_ID_KEY = "pricePlanId";
    public final static String PRICE_PLAN_COMPARISONS_KEY = "pricePlanComparisons";
    private final PricePlanService pricePlanService;
    private final AccountService accountService;

    public PricePlanComparatorController(PricePlanService pricePlanService, AccountService accountService) {
        this.pricePlanService = pricePlanService;
        this.accountService = accountService;
    }

    @GetMapping("/compare-all/{smartMeterId}")
    public ResponseEntity<Map<String, Object>> calculatedCostForEachPricePlan(@PathVariable String smartMeterId) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        Optional<Map<String, BigDecimal>> consumptionsForPricePlans =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);

        if (!consumptionsForPricePlans.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> pricePlanComparisons = new HashMap<>();
        pricePlanComparisons.put(PRICE_PLAN_ID_KEY, pricePlanId);
        pricePlanComparisons.put(PRICE_PLAN_COMPARISONS_KEY, consumptionsForPricePlans.get());

        return consumptionsForPricePlans.isPresent()
                ? ResponseEntity.ok(pricePlanComparisons)
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/costlastweek/{smartMeterId}")
    public ResponseEntity<List<Map.Entry<String, BigDecimal>>> calculatedCostForLastWeek(@PathVariable String smartMeterId) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (pricePlanId == null) {
            Map<String, String> wrongResponse = new HashMap<>();
            wrongResponse.put("Message", "There is no price plan, please check it");
            return new ResponseEntity(wrongResponse, HttpStatus.BAD_REQUEST);
        }
        Optional<Map<String, BigDecimal>> consumptionsForLastWeek
                = pricePlanService.getConsumptionCostOfElectricityReadingsForLastWeek(smartMeterId, pricePlanId);
        if (!consumptionsForLastWeek.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        List<Map.Entry<String, BigDecimal>> costForLastWeek = new ArrayList<>(consumptionsForLastWeek.get().entrySet());
        return ResponseEntity.ok(costForLastWeek.stream().filter(m -> m.getKey().equals(pricePlanId)).collect(Collectors.toList()));

    }

    @GetMapping("/recommend/{smartMeterId}")
    public ResponseEntity<List<Map.Entry<String, BigDecimal>>> recommendCheapestPricePlans(@PathVariable String smartMeterId,
                                                                                           @RequestParam(value = "limit", required = false) Integer limit) {
        Optional<Map<String, BigDecimal>> consumptionsForPricePlans =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);
        if (!consumptionsForPricePlans.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        List<Map.Entry<String, BigDecimal>> recommendations = new ArrayList<>(consumptionsForPricePlans.get().entrySet());
        recommendations.sort(Comparator.comparing(Map.Entry::getValue));
        if (limit != null && limit < recommendations.size()) {
            recommendations = recommendations.subList(0, limit);
        }
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/cost-dayofweek/{smartMeterId}")
    public ResponseEntity<Map<String, Object>> calculatedCostDayOfWeek(@PathVariable String smartMeterId) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        Optional<Map<String, BigDecimal>> consumptionsCostDayOfWeekForSmartMeterId =
                pricePlanService.getConsumptionCostOfElectricityReadingsDayOfWeek(smartMeterId, pricePlanId);

        if (!consumptionsCostDayOfWeekForSmartMeterId.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> consumptionsDayOfWeek = new HashMap<>();
        consumptionsDayOfWeek.put("consumptions", consumptionsCostDayOfWeekForSmartMeterId.get().get(pricePlanId));
        consumptionsDayOfWeek.put(PRICE_PLAN_ID_KEY, pricePlanId);
        consumptionsDayOfWeek.put("day of week", Instant.now().atZone(ZoneId.systemDefault()).getDayOfWeek());

        return consumptionsCostDayOfWeekForSmartMeterId.isPresent()
                ? ResponseEntity.ok(consumptionsDayOfWeek)
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/cost-compare/daysofweek/{smartMeterId}")
    public ResponseEntity<List<Map.Entry<String, BigDecimal>>> calculatedCostForDaysOfWeek(@PathVariable String smartMeterId) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        Optional<Map<String, BigDecimal>> consumptionsCostDaysOfWeekForSmartMeterId =
                pricePlanService.getConsumptionCostOfElectricityReadingsDaysOfWeek(smartMeterId, pricePlanId);

        if (!consumptionsCostDaysOfWeekForSmartMeterId.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        List<Map.Entry<String, BigDecimal>> consumptionsRanksDaysOfWeek = new ArrayList<>(consumptionsCostDaysOfWeekForSmartMeterId.get().entrySet());
        consumptionsRanksDaysOfWeek.sort(Comparator.comparing(Map.Entry::getValue));
        return ResponseEntity.ok(consumptionsRanksDaysOfWeek);

    }

    @GetMapping("/cost-compare/daysofweek-plans/{smartMeterId}")
    public ResponseEntity<List<Map.Entry<String, Map<String, BigDecimal>>>> recommendCheapestPricePlansForDaysOfWeek(@PathVariable String smartMeterId,
                                                                                                                     @RequestParam(value = "limit", required = false) Integer limit) {
        Optional<Map<String, Map<String, BigDecimal>>> consumptionsCostDaysOfWeekWithPricePlans =
                pricePlanService.getConsumptionCostOfElectricityReadingsDaysOfWeekForEachPricePlan(smartMeterId);

        if (!consumptionsCostDaysOfWeekWithPricePlans.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        List<Map.Entry<String, Map<String, BigDecimal>>> consumptionsRanksDaysOfWeekFoEachPricePlan = new ArrayList<>(consumptionsCostDaysOfWeekWithPricePlans.get().entrySet());
        if (limit != null && limit < consumptionsRanksDaysOfWeekFoEachPricePlan.size()) {
            consumptionsRanksDaysOfWeekFoEachPricePlan = consumptionsRanksDaysOfWeekFoEachPricePlan.subList(0, limit);
        }
        return ResponseEntity.ok(consumptionsRanksDaysOfWeekFoEachPricePlan);

    }

}
