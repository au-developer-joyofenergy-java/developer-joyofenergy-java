package uk.tw.energy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.tw.energy.service.AccountService;
import uk.tw.energy.service.PricePlanService;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/price-plans")
public class PricePlanComparatorController {

    public final static String PRICE_PLAN_ID_KEY = "pricePlanId";
    public final static String PRICE_PLAN_COMPARISONS_KEY = "pricePlanComparisons";
    private final PricePlanService pricePlanService;
    private final AccountService accountService;
    private final String day_of_week = "day of week";
    private final String consumptions = "consumptions";

    public PricePlanComparatorController(PricePlanService pricePlanService, AccountService accountService) {
        this.pricePlanService = pricePlanService;
        this.accountService = accountService;
    }

    static boolean checkNullOrBlank(String str) {
        return null == str || "".equals(str);
    }

    @GetMapping("/compare-all/{smartMeterId}")
    public ResponseEntity<Map<String, Object>> calculatedCostForEachPricePlan(@PathVariable String smartMeterId) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (checkNullOrBlank(pricePlanId)) {
            Map<String, String> wrongResponse = new HashMap<>(1);
            wrongResponse.put("Message", "This smartMeterId does not exsit, please check or login it");
            return new ResponseEntity(wrongResponse, HttpStatus.BAD_REQUEST);
        }
        Map<String, BigDecimal> consumptionsForPricePlans =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);

        if (consumptionsForPricePlans.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> pricePlanComparisons = new HashMap<>();
        pricePlanComparisons.put(PRICE_PLAN_ID_KEY, pricePlanId);
        pricePlanComparisons.put(PRICE_PLAN_COMPARISONS_KEY, consumptionsForPricePlans);
        return ResponseEntity.ok(pricePlanComparisons);
    }

    @GetMapping("/recommend/{smartMeterId}")
    public ResponseEntity<Map<String, BigDecimal>> recommendCheapestPricePlans(@PathVariable String smartMeterId,
                                                                               @RequestParam(value = "limit", required = false) Integer limit) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (checkNullOrBlank(pricePlanId)) {
            Map<String, String> wrongResponse = new HashMap<>(1);
            wrongResponse.put("Message", "This smartMeterId does not exsit, please check or login it");
            return new ResponseEntity(wrongResponse, HttpStatus.BAD_REQUEST);
        }
        Map<String, BigDecimal> consumptionsForPricePlans =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(smartMeterId);
        if (consumptionsForPricePlans.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (limit != null) {
            Map<String, BigDecimal> recommendations = consumptionsForPricePlans.entrySet().stream().
                    sorted(Map.Entry.comparingByValue())
                    .limit(limit)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return ResponseEntity.ok(recommendations);
        }
        return ResponseEntity.ok(consumptionsForPricePlans);
    }

    @GetMapping("/cost-lastweek/{smartMeterId}")
    public ResponseEntity<Map<String, BigDecimal>> calculatedCostForLastWeek(@PathVariable String smartMeterId) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (checkNullOrBlank(pricePlanId)) {
            Map<String, String> wrongResponse = new HashMap<>(1);
            wrongResponse.put("Message", "This smartMeterId does not exsit, please check or login it");
            return new ResponseEntity(wrongResponse, HttpStatus.BAD_REQUEST);
        }
        LocalDate now = LocalDate.now();
        Map<String, BigDecimal> consumptionsForLastWeek
                = pricePlanService.getConsumptionCostOfElectricityReadingsForLastWeek(smartMeterId, now);
        Map<String, BigDecimal> costForLastWeek = new HashMap<>();
        if (consumptionsForLastWeek.isEmpty()) {
            costForLastWeek.put(pricePlanId, new BigDecimal(0));
            return ResponseEntity.ok(costForLastWeek);
        }
        costForLastWeek = consumptionsForLastWeek
                .entrySet()
                .stream()
                .filter(m -> m.getKey().equals(pricePlanId))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return ResponseEntity.ok(costForLastWeek);
    }

    @GetMapping("/cost-dayofweek/{smartMeterId}")
    public ResponseEntity<Map<String, Object>> calculatedCostDayOfWeek(@PathVariable String smartMeterId) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (checkNullOrBlank(pricePlanId)) {
            Map<String, String> wrongResponse = new HashMap<>(1);
            wrongResponse.put("Message", "This smartMeterId does not exsit, please check or login it");
            return new ResponseEntity(wrongResponse, HttpStatus.BAD_REQUEST);
        }
        LocalDate todayDate = LocalDate.now();
        Map<String, BigDecimal> consumptionsCostDayOfWeekForSmartMeterId =
                pricePlanService.getConsumptionCostOfElectricityReadingsDayOfWeek(smartMeterId, todayDate);
        Map<String, Object> consumptionsDayOfWeek = new HashMap<>();
        consumptionsDayOfWeek.put(consumptions, consumptionsCostDayOfWeekForSmartMeterId.get(pricePlanId));
        consumptionsDayOfWeek.put(PRICE_PLAN_ID_KEY, pricePlanId);
        consumptionsDayOfWeek.put(day_of_week, Instant.now().atZone(ZoneId.systemDefault()).getDayOfWeek());
        return ResponseEntity.ok(consumptionsDayOfWeek);
    }

    @GetMapping("/cost-compare/daysofweek/{smartMeterId}")
    public ResponseEntity<Map<String, BigDecimal>> calculatedCostForDaysOfWeek(@PathVariable String smartMeterId) {

        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (checkNullOrBlank(pricePlanId)) {
            Map<String, String> wrongResponse = new HashMap<>(1);
            wrongResponse.put("Message", "This smartMeterId does not exsit, please check or login it");
            return new ResponseEntity(wrongResponse, HttpStatus.BAD_REQUEST);
        }
        LocalDate todayDate = LocalDate.now();
        Map<String, BigDecimal> consumptionsCostDaysOfWeekForSmartMeterId =
                pricePlanService.getConsumptionCostOfElectricityReadingsDaysOfWeek(smartMeterId, pricePlanId, todayDate);
        Map<String, BigDecimal> consumptionsRanksDaysOfWeek =
                consumptionsCostDaysOfWeekForSmartMeterId
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        return ResponseEntity.ok(consumptionsRanksDaysOfWeek);
    }

    @GetMapping("/cost-compare/daysofweek-plans/{smartMeterId}")
    public ResponseEntity<Map<DayOfWeek, Map<String, BigDecimal>>> recommendCheapestPricePlansForDaysOfWeek(@PathVariable String smartMeterId,
                                                                                                            @RequestParam(value = "limit", required = false) Integer limit) {
        String pricePlanId = accountService.getPricePlanIdForSmartMeterId(smartMeterId);
        if (checkNullOrBlank(pricePlanId)) {
            Map<String, Map<String, String>> wrongResponse = new HashMap<>(1);
            Map<String, String> test = new HashMap<>(1);
            test.put("Message", "This smartMeterId does not exsit, please check or login it");
            wrongResponse.put("Bad request", test);
            return new ResponseEntity(wrongResponse, HttpStatus.BAD_REQUEST);
        }
        LocalDate todayDate = LocalDate.now();
        if (limit != null) {
            Map<DayOfWeek, Map<String, BigDecimal>> consumptionsCostDaysOfWeekWithPricePlans =
                    pricePlanService.getConsumptionCostOfElectricityReadingsDaysOfWeekForEachPricePlan(smartMeterId, todayDate, limit);

            return ResponseEntity.ok(consumptionsCostDaysOfWeekWithPricePlans);
        } else {
            Map<DayOfWeek, Map<String, BigDecimal>> consumptionsCostDaysOfWeekWithPricePlans =
                    pricePlanService.getConsumptionCostOfElectricityReadingsDaysOfWeekForEachPricePlanWithOutLimit(smartMeterId, todayDate);

            return ResponseEntity.ok(consumptionsCostDaysOfWeekWithPricePlans);
        }

    }

}
