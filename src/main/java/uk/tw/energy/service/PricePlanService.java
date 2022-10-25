package uk.tw.energy.service;

import org.springframework.stereotype.Service;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.PricePlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PricePlanService {

    private final List<PricePlan> pricePlans;
    private final MeterReadingService meterReadingService;
    private final int secondsToAdd = 86400;

    public PricePlanService(List<PricePlan> pricePlans, MeterReadingService meterReadingService) {
        this.pricePlans = pricePlans;
        this.meterReadingService = meterReadingService;
    }

    public Map<String, BigDecimal> getConsumptionCostOfElectricityReadingsForEachPricePlan(String smartMeterId) {
        List<ElectricityReading> electricityReadings = meterReadingService.getReadings(smartMeterId);

        Map<String, BigDecimal> result = new HashMap<>();
        if (electricityReadings.isEmpty()) {
            return result;
        }
        result = pricePlans.stream().collect(
                Collectors.toMap(PricePlan::getPlanName, t -> calculateCost(electricityReadings, t)));
        return result;
    }


    private BigDecimal calculateCost(List<ElectricityReading> electricityReadings, PricePlan pricePlan) {
        BigDecimal average = calculateAverageReading(electricityReadings);
        BigDecimal timeElapsed = calculateTimeElapsed(electricityReadings);

        BigDecimal averagedCost = average.divide(timeElapsed, RoundingMode.HALF_UP);
        return averagedCost.multiply(pricePlan.getUnitRate());
    }

    private BigDecimal calculateAverageReading(List<ElectricityReading> electricityReadings) {
        BigDecimal summedReadings = electricityReadings.stream()
                .map(ElectricityReading::getReading)
                .reduce(BigDecimal.ZERO, (reading, accumulator) -> reading.add(accumulator));

        return summedReadings.divide(BigDecimal.valueOf(electricityReadings.size()), RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTimeElapsed(List<ElectricityReading> electricityReadings) {
        ElectricityReading first = electricityReadings.stream()
                .min(Comparator.comparing(ElectricityReading::getTime))
                .get();
        ElectricityReading last = electricityReadings.stream()
                .max(Comparator.comparing(ElectricityReading::getTime))
                .get();

        return BigDecimal.valueOf(Duration.between(first.getTime(), last.getTime()).getSeconds() / 3600.0);
    }

    public Map<String, BigDecimal> getConsumptionCostOfElectricityReadingsForLastWeek(String smartMeterId, LocalDate date) {
        LocalDate lastWeek = LocalDate.from(date.minusWeeks(1));
        LocalDate beginTime = lastWeek.with(DayOfWeek.MONDAY);
        LocalDate endTime = lastWeek.with(DayOfWeek.SUNDAY);
        Instant beginTimeInstant = beginTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endTimeInstant = endTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<ElectricityReading> electricityReadings = meterReadingService.getReadings(smartMeterId);
        Map<String, BigDecimal> result = new HashMap<>();
        if (electricityReadings.isEmpty()) {
            return result;
        }
        result = getStringBigDecimalMap(beginTimeInstant, endTimeInstant, electricityReadings);
        return result;
    }

    private Map<String, BigDecimal> getStringBigDecimalMap(Instant beginTimeInstant, Instant endTimeInstant, List<ElectricityReading> electricityReadings) {
        Map<String, BigDecimal> result = new HashMap<>();
        List<ElectricityReading> electricityReadingsForLastWeek = electricityReadings
                .stream()
                .filter(x -> x.getTime().compareTo(beginTimeInstant) >= 0 && x.getTime().compareTo(endTimeInstant) <= 0)
                .collect(Collectors.toList());
        if (electricityReadingsForLastWeek.isEmpty()) {
            return result;
        } else {
            result = pricePlans.stream().collect(
                    Collectors.toMap(PricePlan::getPlanName, t -> calculateCost(electricityReadingsForLastWeek, t)));
            return result;
        }
    }

    public Map<String, BigDecimal> getConsumptionCostOfElectricityReadingsDayOfWeek(String smartMeterId, LocalDate date) {
        Instant beginTimeOfTheDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant now = Instant.now();
        List<ElectricityReading> electricityReadings = meterReadingService.getReadings(smartMeterId);
        Map<String, BigDecimal> result = new HashMap<>();
        if (electricityReadings.isEmpty()) {
            return result;
        }
        result = getStringBigDecimalMap(beginTimeOfTheDay, now, electricityReadings);
        return result;
    }

    public Map<String, BigDecimal> getConsumptionCostOfElectricityReadingsDaysOfWeek(String smartMeterId, String pricePlanId, LocalDate todayDate) {
        List<ElectricityReading> electricityReadings = meterReadingService.getReadings(smartMeterId);
        Map<String, BigDecimal> consumptionsDaysOfWeek = new HashMap<>();
        if (electricityReadings.isEmpty()) {
            return consumptionsDaysOfWeek;
        }
        LocalDate startDayDate = todayDate.with(DayOfWeek.MONDAY);
        int gapDays = Period.between(startDayDate, todayDate).getDays();
        for (int i = 0; i <= gapDays; i++) {
            LocalDate beginDate = LocalDate.from(todayDate.minusDays(i));
            Instant beginDateInstant = beginDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endDateInstant = beginDateInstant.plusSeconds(secondsToAdd);
            Map<String, BigDecimal> result = getStringBigDecimalMap(beginDateInstant, endDateInstant, electricityReadings);
            if (result.isEmpty()) {
                consumptionsDaysOfWeek.put(String.valueOf(beginDate.getDayOfWeek()), new BigDecimal(0));
            } else {
                consumptionsDaysOfWeek.put(String.valueOf(beginDate.getDayOfWeek()), result.get(pricePlanId));
            }
        }
        return consumptionsDaysOfWeek;
    }

    public Map<DayOfWeek, Map<String, BigDecimal>> getConsumptionCostOfElectricityReadingsDaysOfWeekForEachPricePlan(String smartMeterId, LocalDate todayDate, Integer limit) {
        List<ElectricityReading> electricityReadings = meterReadingService.getReadings(smartMeterId);
        Map<DayOfWeek, Map<String, BigDecimal>> consumptionsDaysOfWeek = new HashMap<>();
        if (electricityReadings.isEmpty()) {
            return consumptionsDaysOfWeek;
        }
        LocalDate startDayDate = todayDate.with(DayOfWeek.MONDAY);
        int gapDays = Period.between(startDayDate, todayDate).getDays();
        for (int i = 0; i <= gapDays; i++) {
            LocalDate beginDate = LocalDate.from(todayDate.minusDays(i));
            Instant beginDateInstant = beginDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endDateInstant = beginDateInstant.plusSeconds(secondsToAdd);
            Map<String, BigDecimal> result = getStringBigDecimalMap(beginDateInstant, endDateInstant, electricityReadings);
            result = result.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(limit).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
            consumptionsDaysOfWeek.put(beginDate.getDayOfWeek(), result);
        }
        Map<DayOfWeek, Map<String, BigDecimal>> consumptionsDaysOfWeekWithDateOrder = consumptionsDaysOfWeek.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        return consumptionsDaysOfWeekWithDateOrder;
    }

    public Map<DayOfWeek, Map<String, BigDecimal>> getConsumptionCostOfElectricityReadingsDaysOfWeekForEachPricePlanWithOutLimit(String smartMeterId, LocalDate todayDate) {
        List<ElectricityReading> electricityReadings = meterReadingService.getReadings(smartMeterId);
        Map<DayOfWeek, Map<String, BigDecimal>> consumptionsDaysOfWeek = new HashMap<>();
        if (electricityReadings.isEmpty()) {
            return consumptionsDaysOfWeek;
        }
        Map<DayOfWeek, Map<String, BigDecimal>> consumptionsDaysOfWeekWithOutDateOrder = new HashMap<>();
        LocalDate startDayDate = todayDate.with(DayOfWeek.MONDAY);
        int gapDays = Period.between(startDayDate, todayDate).getDays();
        for (int i = 0; i <= gapDays; i++) {
            LocalDate beginDate = LocalDate.from(todayDate.minusDays(i));
            Instant beginDateInstant = beginDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endDateInstant = beginDateInstant.plusSeconds(secondsToAdd);
            Map<String, BigDecimal> result = getStringBigDecimalMap(beginDateInstant, endDateInstant, electricityReadings);
            consumptionsDaysOfWeekWithOutDateOrder.put(beginDate.getDayOfWeek(), result);
        }
        Map<DayOfWeek, Map<String, BigDecimal>> consumptionsDaysOfWeekWithDateOrder = consumptionsDaysOfWeekWithOutDateOrder.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        return consumptionsDaysOfWeekWithDateOrder;
    }
}
