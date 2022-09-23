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

    public PricePlanService(List<PricePlan> pricePlans, MeterReadingService meterReadingService) {
        this.pricePlans = pricePlans;
        this.meterReadingService = meterReadingService;
    }

    public Optional<Map<String, BigDecimal>> getConsumptionCostOfElectricityReadingsForEachPricePlan(String smartMeterId) {
        Optional<List<ElectricityReading>> electricityReadings = meterReadingService.getReadings(smartMeterId);

        if (!electricityReadings.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(pricePlans.stream().collect(
                Collectors.toMap(PricePlan::getPlanName, t -> calculateCost(electricityReadings.get(), t))));
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

    public Optional<Map<String, BigDecimal>> getConsumptionCostOfElectricityReadingsDayOfWeek(String smartMeterId, String pricePlanId) {
        LocalDate todayDate = LocalDate.now();
        Instant beginTimeOfTheDay = todayDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant now = Instant.now();

        Optional<List<ElectricityReading>> electricityReadings;
        electricityReadings = meterReadingService.getReadings(smartMeterId);

        if (!electricityReadings.isPresent()) {
            return Optional.empty();
        }
        List<ElectricityReading> electricityReadingsDayOfWeek = electricityReadings
                .get()
                .stream()
                .filter(x->x.getTime().compareTo(beginTimeOfTheDay)>=0 && x.getTime().compareTo(now)<=0)
                .collect(Collectors.toList());

        if (electricityReadingsDayOfWeek.isEmpty()) {
            return Optional.empty();
        }
        Map<String,BigDecimal> result = pricePlans.stream().filter(x-> x.getPlanName().equals(pricePlanId)).collect(
                Collectors.toMap(PricePlan::getPlanName, t -> calculateCost(electricityReadingsDayOfWeek, t)));
        return Optional.of(result);
    }

    public Optional<Map<String, BigDecimal>> getConsumptionCostOfElectricityReadingsDaysOfWeek(String smartMeterId, String pricePlanId) {

        LocalDate todayDate = LocalDate.now();
        LocalDate beginTime = todayDate.with(DayOfWeek.MONDAY);
        int gapDays = Period.between(beginTime,todayDate).getDays();

        Optional<List<ElectricityReading>> electricityReadings;
        electricityReadings = meterReadingService.getReadings(smartMeterId);

        if(!electricityReadings.isPresent()){
            return Optional.empty();
        }

        Map<String, BigDecimal> consumptionsDaysOfWeek = new HashMap<>();
        for (int i = 0;i<=gapDays; i++){

           LocalDate beginDate = LocalDate.from(todayDate.minusDays(i));
           LocalDate endDate = LocalDate.from(beginDate.plusDays(i+1));
           Instant beginDateInstant = beginDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
           Instant endDateInstant = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

            List<ElectricityReading> electricityReadingsDayOfWeek = electricityReadings
                    .get()
                    .stream()
                    .filter(x->x.getTime().compareTo(beginDateInstant)>=0 && x.getTime().compareTo(endDateInstant)<=0)
                    .collect(Collectors.toList());

            if(electricityReadingsDayOfWeek.isEmpty()){
                consumptionsDaysOfWeek.put(String.valueOf(beginDate.getDayOfWeek()),new BigDecimal(0));

            }else{
                Map<String,BigDecimal> result= pricePlans.stream().filter(x-> x.getPlanName().equals(pricePlanId)).collect(
                        Collectors.toMap(PricePlan::getPlanName, t -> calculateCost(electricityReadingsDayOfWeek, t)));

                consumptionsDaysOfWeek.put(String.valueOf(beginDate.getDayOfWeek()),result.get(pricePlanId));
            }


        }
       return Optional.of(consumptionsDaysOfWeek);




    }

}
