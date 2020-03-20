package com.jbhunt.pricing.agreement.client;

import java.util.List;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.jbhunt.pricing.agreement.dto.ChargeTypeEnterpriseDTO;
import com.jbhunt.pricing.agreement.properties.PricingAgreementProperties;
import com.jbhunt.pricing.configuration.client.constants.PricingConfigClientURLConstants;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ChargeCodeClient {
	
	private final RestTemplate pricingConfigRestTemplate;
	private final PricingAgreementProperties pricingAgreementProperties;
	
	public List<Integer> saveChargeCode(ChargeTypeEnterpriseDTO chargeTypeEnterpriseDTO) {
		HttpEntity<ChargeTypeEnterpriseDTO> httpEntity = new HttpEntity<>(chargeTypeEnterpriseDTO);
		ResponseEntity<List<Integer>> response = pricingConfigRestTemplate.exchange(pricingAgreementProperties.getReferenceDataURL() + "chargecodes",
				HttpMethod.POST, httpEntity, new ParameterizedTypeReference<List<Integer>>() {
				});

		return getResponseBody(response);
	}

	public void fetchChargeCodeDetails() {
		String configUrl = pricingAgreementProperties.getReferenceDataURL()
				+ PricingConfigClientURLConstants.CONFIG_DETAILS_BY_PARAM_URL;
		pricingConfigRestTemplate.exchange(configUrl, HttpMethod.GET,
				HttpEntity.EMPTY, new ParameterizedTypeReference<Void>() {
				});
	}

	public static <T> T getResponseBody(ResponseEntity<T> object) {
		T responseBody = null;
		if (Optional.ofNullable(object).isPresent()) {
			responseBody = object.getBody();
		}
		return responseBody;
	}
}
