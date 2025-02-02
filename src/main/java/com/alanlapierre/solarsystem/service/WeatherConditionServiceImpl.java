package com.alanlapierre.solarsystem.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alanlapierre.solarsystem.error.BusinessException;
import com.alanlapierre.solarsystem.model.WeatherCondition;
import com.alanlapierre.solarsystem.repository.WeatherConditionRepository;
import com.alanlapierre.solarsystem.validator.ConditionsComposer;
import com.alanlapierre.solarsystem.validator.ParamValidator;
import com.alanlapierre.solarsystem.vo.WeatherConditionVO;

@Service("weatherConditionService")
@Transactional(readOnly = true)
public class WeatherConditionServiceImpl implements WeatherConditionService {
	
	private final WeatherConditionRepository weatherConditionRepository;

	public WeatherConditionServiceImpl(WeatherConditionRepository weatherConditionRepository) {
		this.weatherConditionRepository = weatherConditionRepository;
	}
	
	
	public WeatherCondition getWeatherConditionBySolarSystemIdAndDay(Long solarSystemId, Integer day) throws IllegalArgumentException {
		
		ParamValidator.test(day, ConditionsComposer.or((i)-> i == null , (i) -> i <= 0));
		ParamValidator.test(solarSystemId, ConditionsComposer.or((i)-> i == null , (i) -> i <= 0));
		
		return weatherConditionRepository.findBySolarSystemIdAndDay(solarSystemId, day);
		
	}


	public WeatherCondition create(WeatherCondition weatherCondition) throws BusinessException{
		
		
		WeatherCondition result = null;

		try {
			result = this.weatherConditionRepository.save(weatherCondition);
		} catch (Exception e) {
			throw new BusinessException("There was an error creating WeatherCondition");
		}

		return result;
		
	}
	
	public WeatherConditionVO mapToWeatherConditionVO(WeatherCondition weatherCondition) {
		WeatherConditionVO vo = new WeatherConditionVO();
		vo.setDay(weatherCondition.getDay());
		vo.setSolarSystemId(weatherCondition.getSolarSystem().getId());
		vo.setWeatherConditionDescription(weatherCondition.getWeatherConditionType().getPrediction());
		vo.setWeatherConditionId(weatherCondition.getId());
		vo.setTriangleArea(weatherCondition.getTriangleArea());
		return vo;
	}

}
