package com.aseubel.yusi.service.geo.impl;

import com.aseubel.yusi.config.AmapConfig;
import com.aseubel.yusi.pojo.dto.geo.POIResult;
import com.aseubel.yusi.pojo.dto.geo.ReverseGeocodeResult;
import com.aseubel.yusi.service.geo.GeoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeoServiceImpl implements GeoService {

    private static final Logger log = LoggerFactory.getLogger(GeoServiceImpl.class);

    private final AmapConfig amapConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeoServiceImpl(AmapConfig amapConfig) {
        this.amapConfig = amapConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<POIResult> searchPOI(String keyword, String city) {
        List<POIResult> results = new ArrayList<>();

        try {
            String url = UriComponentsBuilder.fromHttpUrl(amapConfig.getBaseUrl() + "/assistant/inputtips")
                    .queryParam("key", amapConfig.getKey())
                    .queryParam("keywords", keyword)
                    .queryParam("city", city != null ? city : "")
                    .queryParam("datatype", "all")
                    .build()
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if ("1".equals(root.path("status").asText())) {
                JsonNode tips = root.path("tips");
                if (tips.isArray()) {
                    for (JsonNode tip : tips) {
                        String location = tip.path("location").asText();
                        if (location != null && !location.isEmpty() && location.contains(",")) {
                            String[] coords = location.split(",");
                            if (coords.length == 2) {
                                POIResult poi = new POIResult();
                                poi.setId(tip.path("id").asText());
                                poi.setName(tip.path("name").asText());
                                poi.setAddress(tip.path("address").asText());
                                poi.setDistrict(tip.path("district").asText());
                                poi.setLongitude(Double.parseDouble(coords[0]));
                                poi.setLatitude(Double.parseDouble(coords[1]));
                                results.add(poi);
                            }
                        }
                    }
                }
            } else {
                log.warn("Amap inputtips API error: {}", root.path("info").asText());
            }
        } catch (Exception e) {
            log.error("POI search failed", e);
        }

        return results;
    }

    @Override
    public ReverseGeocodeResult reverseGeocode(Double latitude, Double longitude) {
        try {
            // 高德地图格式: 经度,纬度
            String location = longitude + "," + latitude;

            String url = UriComponentsBuilder.fromHttpUrl(amapConfig.getBaseUrl() + "/geocode/regeo")
                    .queryParam("key", amapConfig.getKey())
                    .queryParam("location", location)
                    .queryParam("extensions", "base")
                    .build()
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if ("1".equals(root.path("status").asText())) {
                JsonNode regeocode = root.path("regeocode");
                JsonNode addressComponent = regeocode.path("addressComponent");

                String formattedAddress = regeocode.path("formatted_address").asText();
                String district = addressComponent.path("district").asText();
                String city = addressComponent.path("city").asText();

                // 如果 city 为空，使用 province
                if (city == null || city.isEmpty() || "[]".equals(city)) {
                    city = addressComponent.path("province").asText();
                }

                return new ReverseGeocodeResult(formattedAddress, district, city);
            } else {
                log.warn("Amap regeo API error: {}", root.path("info").asText());
            }
        } catch (Exception e) {
            log.error("Reverse geocode failed", e);
        }

        return null;
    }
}
