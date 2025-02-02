package com.alanlapierre.solarsystem.service;

import static com.alanlapierre.solarsystem.util.Constants.getDaysPerYear;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alanlapierre.solarsystem.error.BusinessException;
import com.alanlapierre.solarsystem.model.Planet;
import com.alanlapierre.solarsystem.model.SolarSystem;
import com.alanlapierre.solarsystem.model.WeatherCondition;
import com.alanlapierre.solarsystem.model.WeatherConditionType;
import com.alanlapierre.solarsystem.predictor.IPositionable;
import com.alanlapierre.solarsystem.predictor.WeatherConditionPredictor;
import com.alanlapierre.solarsystem.predictor.position.IPosition;
import com.alanlapierre.solarsystem.repository.SolarSystemRepository;
import com.alanlapierre.solarsystem.validator.ConditionsComposer;
import com.alanlapierre.solarsystem.validator.ParamValidator;
import com.alanlapierre.solarsystem.vo.PeriodWeatherConditionVO;
import com.alanlapierre.solarsystem.vo.WeatherConditionVO;

@Service("solarSystemService")
@Transactional(readOnly = true)
public class SolarSystemServiceImpl implements SolarSystemService {

	private final SolarSystemRepository solarSystemRepository;
	private final PlanetService planetService;
	private final WeatherConditionService weatherConditionService;
	private final WeatherConditionTypeService weatherConditionTypeService;
	private final WeatherConditionPredictor weatherConditionPredictor; 

	public SolarSystemServiceImpl(SolarSystemRepository solarSystemRepository, PlanetService planetService,
			WeatherConditionService weatherConditionService, WeatherConditionTypeService weatherConditionTypeService, WeatherConditionPredictor weatherConditionPredictor) {

		this.solarSystemRepository = solarSystemRepository;
		this.planetService = planetService;
		this.weatherConditionService = weatherConditionService;
		this.weatherConditionTypeService = weatherConditionTypeService;
		this.weatherConditionPredictor = weatherConditionPredictor;
	}

	public WeatherConditionVO determineWeatherConditionBySolarSystemIdAndDay(Long solarSystemId, Integer day)
			throws IllegalArgumentException, BusinessException {

		ParamValidator.test(day, ConditionsComposer.or((i) -> i == null, (i) -> i <= 0));
		ParamValidator.test(solarSystemId, ConditionsComposer.or((i) -> i == null, (i) -> i <= 0));

		WeatherConditionVO result = null;

		// Intentamos obtener la condicion previamente calculada si es que
		// existe.
		WeatherCondition weatherCondition = weatherConditionService
				.getWeatherConditionBySolarSystemIdAndDay(solarSystemId, day);

		if (weatherCondition == null) {
			weatherCondition = generateNewWeatherCondition(solarSystemId, day);
			WeatherCondition weatherConditionSaved = weatherConditionService.create(weatherCondition);
			result = weatherConditionService.mapToWeatherConditionVO(weatherConditionSaved);
		} else {
			result = weatherConditionService.mapToWeatherConditionVO(weatherCondition);
		}

		return result;
	}

	public PeriodWeatherConditionVO determineWeatherConditionsBySolarSystemIdAndYears(Long solarSystemId, Integer years)
			throws IllegalArgumentException, BusinessException {

		ParamValidator.test(years, ConditionsComposer.or((i) -> i == null, (i) -> i <= 0 , (i)-> i > 10));
		ParamValidator.test(solarSystemId, ConditionsComposer.or((i) -> i == null , (i) -> i <= 0));

		Integer droughtPeriods, rainyPeriods, optimalPeriods, maxTriangleAreaDay;
		droughtPeriods = rainyPeriods = optimalPeriods = maxTriangleAreaDay = 0;
		Double maxTriangleAreaValue = 0D;

		List<WeatherConditionVO> weatherConditions = generatePeriodWeatherConditions(solarSystemId, years);

		int day = 1;
		for (WeatherConditionVO weatherConditionVO : weatherConditions) {
			switch (weatherConditionVO.getWeatherConditionDescription()) {
			case OPTIMAL_CONDITIONS:
				optimalPeriods++;
				break;
			case DROUGHT:
				droughtPeriods++;
				break;
			case RAINY:
				Double area = weatherConditionVO.getTriangleArea();
				if (area > maxTriangleAreaValue) {
					maxTriangleAreaValue = area;
					maxTriangleAreaDay = day;
				}
				rainyPeriods++;
			default:
				break;
			}
			day++;
		}

		PeriodWeatherConditionVO periodWeatherConditionVO = new PeriodWeatherConditionVO();
		periodWeatherConditionVO.setSolarSystemId(solarSystemId);
		periodWeatherConditionVO.setYears(years);
		periodWeatherConditionVO.setWeatherConditions(weatherConditions);
		periodWeatherConditionVO.setDroughtPeriods(droughtPeriods);
		periodWeatherConditionVO.setRainyPeriods(rainyPeriods);
		periodWeatherConditionVO.setOptimalPeriods(optimalPeriods);
		periodWeatherConditionVO.setDayWithMaximumRainfallIntensity(maxTriangleAreaDay);
		return periodWeatherConditionVO;

	}

	@Override
	public SolarSystem getSolarSystemById(Long solarSystemId) {
		return solarSystemRepository.findById(solarSystemId).get();
	}

	private WeatherCondition generateNewWeatherCondition(Long solarSystemId, Integer day) throws BusinessException {

		SolarSystem solarSystemSaved = solarSystemRepository.findById(solarSystemId).get();
		List<Planet> planetList = planetService.getNewPlanetPositionsByDay(solarSystemSaved.getPlanets(), day);

		List<IPositionable> listPositions = new ArrayList<IPositionable>();

		for (Planet planet : planetList) {
			listPositions.add(planet.getCartesianCoordinate());
		}

		IPosition position = weatherConditionPredictor.determinePosition(listPositions);

		WeatherConditionType weatherConditionType = weatherConditionTypeService
				.getWeatherConditionTypeByPrediction(position.getWeatherConditionPredictionForPosition());

		Double triangleArea = determineTriangleArea(planetList);

		WeatherCondition weatherCondition = new WeatherCondition();
		weatherCondition.setDay(day);
		weatherCondition.setWeatherConditionType(weatherConditionType);
		weatherCondition.setSolarSystem(solarSystemSaved);
		weatherCondition.setTriangleArea(triangleArea);
		return weatherCondition;
	}


	private Double determineTriangleArea(List<Planet> planetList) {
		IPositionable p1 = planetList.get(0).getCartesianCoordinate();
		IPositionable p2 = planetList.get(1).getCartesianCoordinate();
		IPositionable p3 = planetList.get(2).getCartesianCoordinate();
		return weatherConditionPredictor.getTriangleArea(p1, p2, p3);
	}

	private List<WeatherConditionVO> generatePeriodWeatherConditions(Long solarSystemId, Integer years)
			throws BusinessException {
		List<WeatherConditionVO> weatherConditions = new ArrayList<WeatherConditionVO>();
		Integer totalDays = getDaysPerYear() * years;

		for (int i = 1; i <= totalDays; i++) {
			WeatherConditionVO weatherConditionVO = determineWeatherConditionBySolarSystemIdAndDay(solarSystemId, i);
			weatherConditions.add(weatherConditionVO);
		}

		return weatherConditions;
	}

}
